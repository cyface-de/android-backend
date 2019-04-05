/*
 * Copyright 2018 Cyface GmbH
 * This file is part of the Cyface SDK for Android.
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.synchronization;

import static de.cyface.synchronization.TestUtils.AUTHORITY;
import static de.cyface.synchronization.TestUtils.TAG;
import static de.cyface.testutils.SharedTestUtils.insertSampleMeasurementWithData;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;

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
import de.cyface.persistence.model.Track;
import de.cyface.persistence.serialization.MeasurementSerializer;
import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;

/**
 * Tests the actual data transmission code. Since this test requires a running Movebis API server, and communicates with
 * that server, it is a flaky test and a large test.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.3.0
 * @since 2.0.0
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
@FlakyTest // Flaky means (because of build.gradle) that this test is not executed in the Mock flavour (because it
           // required an actual api)
public class DataTransmissionTest {

    // ATTENTION: Depending on the API you test against, you might also need to replace the res/raw/truststore.jks
    private final static String TEST_API_URL = "https://REPLACE.WITH.URL:port";
    private final static String TEST_TOKEN = "ey-REPLACE-WITH-TOKEN";

    /**
     * Tests the basic transmission code to a Movebis backend. This is based on some code from stackoverflow. An example
     * request must be formatted as multipart request, which looks like:
     *
     * <pre>
     * POST / HTTP/1.1
     * Host: localhost:8000
     * User-Agent: Mozilla/5.0 (X11; Ubuntu; Linux i686; rv:29.0) Gecko/20100101 Firefox/29.0
     * Accept: text/html,application/xhtml+xml,application/xml;q=0.9,{@literal *}/{@literal *};q=0.8
     * Accept-Language: en-US,en;q=0.5
     * Accept-Encoding: gzip, deflate
     * Cookie: __atuvc=34%7C7; permanent=0; _gitlab_session=226ad8a0be43681acf38c2fab9497240; __profilin=p%3Dt;
     * request_method=GET
     * Connection: keep-alive
     * Content-Type: multipart/form-data; boundary=---------------------------9051914041544843365972754266
     * Content-Length: 554
     * 
     * -----------------------------9051914041544843365972754266
     * Content-Disposition: form-data; name="text"
     * 
     * text default
     * -----------------------------9051914041544843365972754266
     * Content-Disposition: form-data; name="file1"; filename="a.txt"
     * Content-Type: text/plain
     * 
     * Content of a.txt.
     * 
     * -----------------------------9051914041544843365972754266
     * Content-Disposition: form-data; name="file2"; filename="a.html"
     * Content-Type: text/html
     * 
     * {@literal <}!DOCTYPE html{@literal >}{@literal <}title{@literal >}Content of a.html.{@literal <}/title{@literal >}
     * 
     * -----------------------------9051914041544843365972754266--
     * </pre>
     */
    @Test
    public void testUploadSomeBytesViaMultiPart() throws CursorIsNullException, NoSuchMeasurementException {

        // Arrange
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final ContentResolver resolver = context.getContentResolver();
        final PersistenceLayer<DefaultPersistenceBehaviour> persistence = new PersistenceLayer<>(context, resolver,
                AUTHORITY, new DefaultPersistenceBehaviour());

        // Generate measurement
        // Adjust depending on your test case: (600k, 3k) ~ 27 MB compressed data ~ 5 min test execution
        final int point3dCount = 600_000;
        final int locationCount = 3_000;

        // Insert data to be synced
        final Measurement measurement = insertSampleMeasurementWithData(context, AUTHORITY, MeasurementStatus.FINISHED,
                persistence, point3dCount, locationCount);
        final long measurementIdentifier = measurement.getIdentifier();
        final MeasurementStatus loadedStatus = persistence.loadMeasurementStatus(measurementIdentifier);
        assertThat(loadedStatus, is(equalTo(MeasurementStatus.FINISHED)));

        ContentProviderClient client = null;
        try {
            client = resolver.acquireContentProviderClient(AUTHORITY);

            if (client == null)
                throw new IllegalStateException(
                        String.format("Unable to acquire client for content provider %s", AUTHORITY));

            // Load measurement serialized compressed
            final MeasurementContentProviderClient loader = new MeasurementContentProviderClient(measurementIdentifier,
                    client, AUTHORITY);
            final MeasurementSerializer serializer = new MeasurementSerializer(new DefaultFileAccess());
            final File compressedTransferTempFile = serializer.writeSerializedCompressed(loader,
                    measurement.getIdentifier(), persistence);
            Log.d(TAG, "CompressedTransferTempFile size: "
                    + DefaultFileAccess.humanReadableByteCount(compressedTransferTempFile.length(), true));

            // Prepare transmission
            final SyncPerformer performer = new SyncPerformer(
                    InstrumentationRegistry.getInstrumentation().getTargetContext());
            final SyncResult syncResult = new SyncResult();

            // Load meta data
            final List<Track> tracks = persistence.loadTracks(measurementIdentifier);
            final GeoLocation startLocation = tracks.get(0).getGeoLocations().get(0);
            final List<GeoLocation> lastTrack = tracks.get(tracks.size() - 1).getGeoLocations();
            final GeoLocation endLocation = lastTrack.get(lastTrack.size() - 1);
            final SyncAdapter.MetaData metaData = new SyncAdapter.MetaData(startLocation, endLocation, "testDeviceId",
                    measurementIdentifier, "testDeviceType", "testOsVersion", "testAppVersion",
                    measurement.getDistance(), locationCount);

            // Act
            try {
                final boolean result = performer.sendData(new HttpConnection(), syncResult, TEST_API_URL, metaData,
                        compressedTransferTempFile, new UploadProgressListener() {
                            @Override
                            public void updatedProgress(float percent) {
                                Log.d(TAG, String.format("Upload Progress %f", percent));
                            }
                        }, TEST_TOKEN);

                // Assert
                assertThat(result, is(equalTo(true)));
            } finally {
                if (compressedTransferTempFile.exists()) {
                    Validate.isTrue(compressedTransferTempFile.delete());
                }
            }
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }
}
