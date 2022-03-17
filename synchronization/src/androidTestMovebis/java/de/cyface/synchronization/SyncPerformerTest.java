/*
 * Copyright 2018-2021 Cyface GmbH
 *
 * This file is part of the Cyface SDK for Android.
 *
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.synchronization;

import static de.cyface.serializer.DataSerializable.humanReadableSize;
import static de.cyface.synchronization.TestUtils.AUTHORITY;
import static de.cyface.synchronization.TestUtils.TAG;
import static de.cyface.testutils.SharedTestUtils.clearPersistenceLayer;
import static de.cyface.testutils.SharedTestUtils.insertSampleMeasurementWithData;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncResult;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import de.cyface.model.MeasurementIdentifier;
import de.cyface.model.RequestMetaData;
import de.cyface.persistence.DefaultPersistenceBehaviour;
import de.cyface.persistence.MeasurementContentProviderClient;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.exception.NoSuchMeasurementException;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.Modality;
import de.cyface.persistence.model.Track;
import de.cyface.persistence.serialization.MeasurementSerializer;
import de.cyface.synchronization.exception.AccountNotActivated;
import de.cyface.synchronization.exception.BadRequestException;
import de.cyface.synchronization.exception.ConflictException;
import de.cyface.synchronization.exception.EntityNotParsableException;
import de.cyface.synchronization.exception.ForbiddenException;
import de.cyface.synchronization.exception.HostUnresolvable;
import de.cyface.synchronization.exception.InternalServerErrorException;
import de.cyface.synchronization.exception.MeasurementTooLarge;
import de.cyface.synchronization.exception.NetworkUnavailableException;
import de.cyface.synchronization.exception.ServerUnavailableException;
import de.cyface.synchronization.exception.SynchronisationException;
import de.cyface.synchronization.exception.SynchronizationInterruptedException;
import de.cyface.synchronization.exception.TooManyRequestsException;
import de.cyface.synchronization.exception.UnauthorizedException;
import de.cyface.synchronization.exception.UnexpectedResponseCode;
import de.cyface.synchronization.exception.UploadSessionExpired;
import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;

/**
 * Tests the actual data transmission code. Since this test requires a running Movebis API server, and communicates with
 * that server, it is a flaky test and a large test.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.1.0
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class SyncPerformerTest {

    // ATTENTION: Depending on the API you test against, you might also need to replace the res/raw/truststore.jks
    private final static String TEST_API_URL = "http://localhost:8080"; // never use a non-numeric port here!
    private final static String TEST_TOKEN = "ey*****";

    private Context context;
    private ContentResolver contentResolver;
    private SyncPerformer oocut;
    private PersistenceLayer<DefaultPersistenceBehaviour> persistence;
    @Mock
    private Http mockedHttp;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        contentResolver = context.getContentResolver();

        // Ensure reproducibility
        clearPersistenceLayer(context, contentResolver, AUTHORITY);
        persistence = new PersistenceLayer<>(context, contentResolver, AUTHORITY, new DefaultPersistenceBehaviour());

        oocut = new SyncPerformer(context);
    }

    @After
    public void tearDown() {
        clearPersistenceLayer(context, contentResolver, AUTHORITY);
    }

    /**
     * Tests that a {@link Measurement} is marked as sync when the server returns
     * {@code java.net.HttpURLConnection#HTTP_CONFLICT}.
     */
    @Test
    public void testSendData_returnsSuccessWhenServerReturns409()
            throws CursorIsNullException, NoSuchMeasurementException,
            ServerUnavailableException, ForbiddenException, BadRequestException, ConflictException,
            UnauthorizedException, InternalServerErrorException, EntityNotParsableException, SynchronisationException,
            NetworkUnavailableException, SynchronizationInterruptedException, TooManyRequestsException,
            HostUnresolvable, MeasurementTooLarge, UploadSessionExpired, UnexpectedResponseCode, AccountNotActivated {

        // Arrange
        // Insert data to be synced
        final int locationCount = 1;
        final Measurement measurement = insertSampleMeasurementWithData(context, AUTHORITY, MeasurementStatus.FINISHED,
                persistence, 1, locationCount);
        final long measurementIdentifier = measurement.getIdentifier();
        final MeasurementStatus loadedStatus = persistence.loadMeasurementStatus(measurementIdentifier);
        assertThat(loadedStatus, is(equalTo(MeasurementStatus.FINISHED)));

        try (final ContentProviderClient client = contentResolver.acquireContentProviderClient(AUTHORITY)) {

            if (client == null)
                throw new IllegalStateException(
                        String.format("Unable to acquire client for content provider %s", AUTHORITY));

            // Load measurement serialized compressed
            final MeasurementContentProviderClient loader = new MeasurementContentProviderClient(measurementIdentifier,
                    client, AUTHORITY);
            final MeasurementSerializer serializer = new MeasurementSerializer();
            final File compressedTransferTempFile = serializer.writeSerializedCompressed(loader,
                    measurement.getIdentifier(), persistence);
            Log.d(TAG, "CompressedTransferTempFile size: "
                    + humanReadableSize(compressedTransferTempFile.length(), true));

            // Prepare transmission
            final SyncResult syncResult = new SyncResult();

            // Load meta data
            final List<Track> tracks = persistence.loadTracks(measurementIdentifier);
            final var startLocation = tracks.get(0).getGeoLocations().get(0);
            final var lastTrack = tracks.get(tracks.size() - 1).getGeoLocations();
            final var endLocation = lastTrack.get(lastTrack.size() - 1);
            final String deviceId = "testDevi-ce00-42b6-a840-1b70d30094b8"; // Must be a valid UUID
            final var startRecord = new RequestMetaData.GeoLocation(startLocation.getTimestamp(), startLocation.getLat(),
                    startLocation.getLon());
            final var endRecord = new RequestMetaData.GeoLocation(endLocation.getTimestamp(), endLocation.getLat(),
                    endLocation.getLon());
            final var metaData = new RequestMetaData(deviceId, String.valueOf(measurementIdentifier),
                    "testOsVersion", "testDeviceType", "testAppVersion",
                    measurement.getDistance(), locationCount, startRecord, endRecord,
                    Modality.BICYCLE.getDatabaseIdentifier(), 2);

            // Mock the actual post request
            doThrow(new ConflictException("Test ConflictException"))
                    .when(mockedHttp).upload(any(URL.class), anyString(), any(RequestMetaData.class),
                            any(File.class), any(UploadProgressListener.class));

            // Act
            try {
                // In the mock settings above we faked a ConflictException from the server
                final HttpConnection.Result result = oocut.sendData(mockedHttp, syncResult, TEST_API_URL, metaData,
                        compressedTransferTempFile, percent -> Log.d(TAG, String.format("Upload Progress %f", percent)),
                        TEST_TOKEN);

                // Assert:
                verify(mockedHttp, times(1)).upload(any(URL.class), anyString(),
                        any(RequestMetaData.class), any(File.class), any(UploadProgressListener.class));
                // because of the ConflictException true should be returned
                assertThat(result, is(equalTo(HttpConnection.Result.UPLOAD_SUCCESSFUL)));
                // Make sure the ConflictException is actually called (instead of no exception because of mock)
                assertThat(syncResult.stats.numSkippedEntries, is(equalTo(1L)));

                // Cleanup
            } finally {
                if (compressedTransferTempFile.exists()) {
                    Validate.isTrue(compressedTransferTempFile.delete());
                }
            }
        }
    }

    @SuppressWarnings("RedundantSuppression")
    @Test
    @Ignore("Still uses an actual API")
    public void testUpload_toActualApi() throws IOException, CursorIsNullException, NoSuchMeasurementException,
            BadRequestException, EntityNotParsableException, ForbiddenException, ConflictException,
            NetworkUnavailableException, SynchronizationInterruptedException, InternalServerErrorException,
            SynchronisationException, UnauthorizedException, TooManyRequestsException, HostUnresolvable,
            ServerUnavailableException, MeasurementTooLarge, UploadSessionExpired, UnexpectedResponseCode, AccountNotActivated {

        // Arrange
        // 24 hours test data ~ 108 MB which is more then the currently supported upload size (100)
        final int hours = 1;
        final int locationCount = hours * 3_600;
        final Measurement measurement = insertSampleMeasurementWithData(context, AUTHORITY, MeasurementStatus.FINISHED,
                persistence, locationCount * 100, locationCount);
        final long measurementIdentifier = measurement.getIdentifier();
        final MeasurementStatus loadedStatus = persistence.loadMeasurementStatus(measurementIdentifier);
        assertThat(loadedStatus, is(equalTo(MeasurementStatus.FINISHED)));
        File file = null;
        try (final ContentProviderClient client = contentResolver.acquireContentProviderClient(AUTHORITY)) {
            Validate.notNull(client, String.format("Unable to acquire client for content provider %s", AUTHORITY));
            file = loadSerializedCompressed(client, measurementIdentifier);
            final var metaData = loadMetaData(measurement, locationCount);

            final URL url = new URL(String.format("%s%s/measurements", TEST_API_URL, "/api/v3"));

            // Act
            final HttpConnection.Result result = new HttpConnection().upload(url, TEST_TOKEN, metaData, file,
                    percent -> {
                    });

            // Assert
            assertThat(result, is(equalTo(HttpConnection.Result.UPLOAD_SUCCESSFUL)));

            // Cleanup
        } finally {
            if (file != null && file.exists()) {
                Validate.isTrue(file.delete());
            }
        }
    }

    /**
     * Tests the basic transmission code to a actual Cyface API.
     * <p>
     * Can be used to reproduce bugs in the interaction between an actual API and our client.
     * <p>
     * <b>Attention:</b> for this you need to adjust {@link #TEST_API_URL} and {@link #TEST_TOKEN}.
     */
    @SuppressWarnings("RedundantSuppression")
    @Test
    @Ignore("Still uses an actual API")
    public void testSendData_toActualApi() throws CursorIsNullException, NoSuchMeasurementException {

        // Arrange
        // Adjust depending on your test case: (600k, 3k) ~ 27 MB compressed data ~ 5 min test execution
        // noinspection PointlessArithmeticExpression
        final var point3DCount = 600 * 1_000;
        // noinspection PointlessArithmeticExpression
        final int locationCount = 3 * 1_000;

        // Insert data to be synced
        final var measurement = insertSampleMeasurementWithData(context, AUTHORITY, MeasurementStatus.FINISHED,
                persistence, point3DCount, locationCount);
        final long measurementIdentifier = measurement.getIdentifier();
        final MeasurementStatus loadedStatus = persistence.loadMeasurementStatus(measurementIdentifier);
        assertThat(loadedStatus, is(equalTo(MeasurementStatus.FINISHED)));

        try (final ContentProviderClient client = contentResolver.acquireContentProviderClient(AUTHORITY)) {
            Validate.notNull(client, String.format("Unable to acquire client for content provider %s", AUTHORITY));
            final File compressedTransferTempFile = loadSerializedCompressed(client, measurementIdentifier);
            final var metaData = loadMetaData(measurement, locationCount);

            // Prepare transmission
            final SyncResult syncResult = new SyncResult();

            // Act
            final HttpConnection.Result result = oocut.sendData(new HttpConnection(), syncResult,
                    TEST_API_URL + "/api/v3/", metaData,
                    compressedTransferTempFile, percent -> Log.d(TAG, String.format("Upload Progress %f", percent)),
                    TEST_TOKEN);

            // Assert
            assertThat(result, is(equalTo(HttpConnection.Result.UPLOAD_SUCCESSFUL)));
        }
    }

    private RequestMetaData loadMetaData(Measurement measurement,
            @SuppressWarnings("SameParameterValue") int locationCount) throws CursorIsNullException {
        // Load meta data
        final List<Track> tracks = persistence.loadTracks(measurement.getIdentifier());
        final var startLocation = tracks.get(0).getGeoLocations().get(0);
        final var lastTrack = tracks.get(tracks.size() - 1).getGeoLocations();
        final var endLocation = lastTrack.get(lastTrack.size() - 1);
        final String deviceId = "testDevi-ce00-42b6-a840-1b70d30094b8"; // Must be a valid UUID
        final var id = new MeasurementIdentifier(deviceId, measurement.getIdentifier());
        final var startRecord = new RequestMetaData.GeoLocation(startLocation.getTimestamp(), startLocation.getLat(),
                startLocation.getLon());
        final var endRecord = new RequestMetaData.GeoLocation(endLocation.getTimestamp(), endLocation.getLat(),
                endLocation.getLon());
        return new RequestMetaData(deviceId, String.valueOf(id.getMeasurementIdentifier()),
                "testOsVersion", "testDeviceType", "testAppVersion",
                measurement.getDistance(), locationCount, startRecord, endRecord,
                Modality.BICYCLE.getDatabaseIdentifier(), 2);
    }

    private File loadSerializedCompressed(ContentProviderClient client, long measurementIdentifier)
            throws CursorIsNullException {

        // Load measurement serialized compressed
        final MeasurementContentProviderClient loader = new MeasurementContentProviderClient(measurementIdentifier,
                client, AUTHORITY);
        final MeasurementSerializer serializer = new MeasurementSerializer();
        final File compressedTransferTempFile = serializer.writeSerializedCompressed(loader, measurementIdentifier,
                persistence);
        Log.d(TAG, "CompressedTransferTempFile size: "
                + humanReadableSize(compressedTransferTempFile.length(), true));
        return compressedTransferTempFile;
    }
}