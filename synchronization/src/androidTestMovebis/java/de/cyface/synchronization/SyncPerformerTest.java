/*
 * Copyright 2018 - 2020 Cyface GmbH
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

import static de.cyface.synchronization.TestUtils.AUTHORITY;
import static de.cyface.synchronization.TestUtils.TAG;
import static de.cyface.testutils.SharedTestUtils.clearPersistenceLayer;
import static de.cyface.testutils.SharedTestUtils.insertSampleMeasurementWithData;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.junit.After;
import org.junit.Before;
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
import androidx.test.filters.FlakyTest;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import de.cyface.persistence.DefaultFileAccess;
import de.cyface.persistence.DefaultPersistenceBehaviour;
import de.cyface.persistence.MeasurementContentProviderClient;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.Modality;
import de.cyface.persistence.model.Track;
import de.cyface.persistence.serialization.EventsFileSerializerStrategy;
import de.cyface.persistence.serialization.MeasurementFileSerializerStrategy;
import de.cyface.persistence.serialization.MeasurementSerializer;
import de.cyface.synchronization.exception.HostUnresolvable;
import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;

/**
 * Tests the actual data transmission code. Since this test requires a running Movebis API server, and communicates with
 * that server, it is a flaky test and a large test.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.0.10
 * @since 2.0.0
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class SyncPerformerTest {

    // ATTENTION: Depending on the API you test against, you might also need to replace the res/raw/truststore.jks
    private final static String TEST_API_URL = "https://REPLACE.WITH.URL:1234"; // never use a non-numeric port here!
    private final static String TEST_TOKEN = "ey-REPLACE-WITH-TOKEN";

    private Context context;
    private ContentResolver contentResolver;
    private SyncPerformer oocut;
    private PersistenceLayer<DefaultPersistenceBehaviour> persistence;
    @Mock
    private Http mockedHttp;
    @Mock
    private HttpURLConnection mockedConnection;

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
    public void testSendData_returnsTrueWhenServerReturns409() throws CursorIsNullException, NoSuchMeasurementException,
      ServerUnavailableException, ForbiddenException, BadRequestException, ConflictException,
      UnauthorizedException, InternalServerErrorException, EntityNotParsableException, SynchronisationException,
      NetworkUnavailableException, SynchronizationInterruptedException, TooManyRequestsException, HostUnresolvable {

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
                    measurement.getIdentifier(), persistence, new MeasurementFileSerializerStrategy());
            final File compressedEventsTransferTempFile = serializer.writeSerializedCompressed(loader,
                    measurement.getIdentifier(), persistence, new EventsFileSerializerStrategy());
            Log.d(TAG, "CompressedTransferTempFile size: "
                    + DefaultFileAccess.humanReadableByteCount(compressedTransferTempFile.length(), true));

            // Prepare transmission
            final SyncResult syncResult = new SyncResult();

            // Load meta data
            final List<Track> tracks = persistence.loadTracks(measurementIdentifier);
            final GeoLocation startLocation = tracks.get(0).getGeoLocations().get(0);
            final List<GeoLocation> lastTrack = tracks.get(tracks.size() - 1).getGeoLocations();
            final GeoLocation endLocation = lastTrack.get(lastTrack.size() - 1);
            final SyncAdapter.MetaData metaData = new SyncAdapter.MetaData(startLocation, endLocation, "testDeviceId",
                    measurementIdentifier, "testDeviceType", "testOsVersion", "testAppVersion",
                    measurement.getDistance(), locationCount, Modality.BICYCLE);

            // Mock the actual post request
            when(mockedHttp.openHttpConnection(any(URL.class), any(SSLContext.class), anyBoolean(), anyString()))
                    .thenReturn(mockedConnection);
            when(mockedHttp.post(any(HttpURLConnection.class), any(SyncAdapter.MetaData.class),
                    any(UploadProgressListener.class), any(FilePart.class), any(FilePart.class)))
                            .thenThrow(new ConflictException("Test ConflictException"));

            // Act
            try {
                // In the mock settings above we faked a ConflictException from the server
                final boolean result = oocut.sendData(mockedHttp, syncResult, TEST_API_URL, metaData,
                        compressedTransferTempFile, compressedEventsTransferTempFile, new UploadProgressListener() {
                            @Override
                            public void updatedProgress(float percent) {
                                Log.d(TAG, String.format("Upload Progress %f", percent));
                            }
                        }, TEST_TOKEN);

                // Assert:
                verify(mockedHttp, times(1)).openHttpConnection(any(URL.class), any(SSLContext.class), anyBoolean(),
                        anyString());
                verify(mockedHttp, times(1)).post(any(HttpURLConnection.class),
                        any(SyncAdapter.MetaData.class), any(UploadProgressListener.class), any(FilePart.class),
                        any(FilePart.class));
                // because of the ConflictException true should be returned
                assertThat(result, is(equalTo(true)));
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

    /**
     * Tests the basic transmission code to a actual Cyface API.
     * <p>
     * Can be used to reproduce bugs in the interaction between an actual API and our client.
     * <p>
     * <b>Attention: </b> for this you need to adjust {@link #TEST_API_URL}, {@link #TEST_TOKEN} and
     * `res/raw/truststore.jks`
     */
    @Test
    @FlakyTest // still uses an actual API. Flaky currently means it's not executed in the mock flavour test
    public void testSendData_toActualApi() throws CursorIsNullException, NoSuchMeasurementException {

        // Arrange
        // Adjust depending on your test case: (600k, 3k) ~ 27 MB compressed data ~ 5 min test execution
        final int point3dCount = 600_000;
        final int locationCount = 3_000;

        // Insert data to be synced
        final Measurement measurement = insertSampleMeasurementWithData(context, AUTHORITY, MeasurementStatus.FINISHED,
                persistence, point3dCount, locationCount);
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
                    measurement.getIdentifier(), persistence, new MeasurementFileSerializerStrategy());
            final File compressedEventsTransferTempFile = serializer.writeSerializedCompressed(loader,
                    measurement.getIdentifier(), persistence, new EventsFileSerializerStrategy());
            Log.d(TAG, "CompressedTransferTempFile size: "
                    + DefaultFileAccess.humanReadableByteCount(compressedTransferTempFile.length(), true));

            // Prepare transmission
            final SyncResult syncResult = new SyncResult();

            // Load meta data
            final List<Track> tracks = persistence.loadTracks(measurementIdentifier);
            final GeoLocation startLocation = tracks.get(0).getGeoLocations().get(0);
            final List<GeoLocation> lastTrack = tracks.get(tracks.size() - 1).getGeoLocations();
            final GeoLocation endLocation = lastTrack.get(lastTrack.size() - 1);
            final String deviceId = "testDevi-ce00-42b6-a840-1b70d30094b8"; // Must be a valid UUID
            final SyncAdapter.MetaData metaData = new SyncAdapter.MetaData(startLocation, endLocation, deviceId,
                    measurementIdentifier, "testDeviceType", "testOsVersion", "testAppVersion",
                    measurement.getDistance(), locationCount, Modality.BICYCLE);

            // Act
            try {
                final boolean result = oocut.sendData(new HttpConnection(), syncResult, TEST_API_URL, metaData,
                        compressedTransferTempFile, compressedEventsTransferTempFile, new UploadProgressListener() {
                            @Override
                            public void updatedProgress(float percent) {
                                Log.d(TAG, String.format("Upload Progress %f", percent));
                            }
                        }, TEST_TOKEN);

                // Assert
                assertThat(result, is(equalTo(true)));

                // Cleanup
            } finally {
                if (compressedTransferTempFile.exists()) {
                    Validate.isTrue(compressedTransferTempFile.delete());
                }
            }
        }
    }
}
