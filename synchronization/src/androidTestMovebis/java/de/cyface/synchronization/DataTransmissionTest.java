package de.cyface.synchronization;

import static de.cyface.synchronization.TestUtils.AUTHORITY;
import static de.cyface.synchronization.TestUtils.TAG;
import static de.cyface.testutils.SharedTestUtils.insertGeoLocation;
import static de.cyface.testutils.SharedTestUtils.insertMeasurementEntry;
import static de.cyface.testutils.SharedTestUtils.insertPoint3d;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncResult;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import de.cyface.persistence.DefaultFileAccess;
import de.cyface.persistence.DefaultPersistenceBehaviour;
import de.cyface.persistence.MeasurementContentProviderClient;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.PointMetaData;
import de.cyface.persistence.model.Vehicle;
import de.cyface.persistence.serialization.MeasurementSerializer;
import de.cyface.persistence.serialization.Point3dFile;
import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;

/**
 * Tests the actual data transmission code. Since this test requires a running Movebis API server, and communicates with
 * that server, it is a flaky test and a large test.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.2.1
 * @since 2.0.0
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
@FlakyTest // Flaky means (because of build.gradle) that this test is not executed in the Mock flavour (because it
           // required an actual api)
public class DataTransmissionTest {

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
    public void testUploadSomeBytesViaMultiPart() throws BadRequestException, RequestParsingException, IOException,
            CursorIsNullException, NoSuchMeasurementException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ContentResolver resolver = context.getContentResolver();
        PersistenceLayer<DefaultPersistenceBehaviour> persistence = new PersistenceLayer<>(context, resolver, AUTHORITY,
                new DefaultPersistenceBehaviour());
        Measurement measurement = insertMeasurementEntry(persistence, Vehicle.UNKNOWN);
        long measurementIdentifier = measurement.getIdentifier();
        insertGeoLocation(resolver, AUTHORITY, measurement.getIdentifier(), 1503055141000L, 49.9304133333333,
                8.82831833333333, 0.0, 940);
        insertGeoLocation(resolver, AUTHORITY, measurement.getIdentifier(), 1503055142000L, 49.9305066666667, 8.82814,
                8.78270530700684, 840);

        // Insert file base data
        final Point3dFile accelerationsFile = new Point3dFile(context, measurementIdentifier,
                Point3dFile.ACCELERATIONS_FOLDER_NAME, Point3dFile.ACCELERATIONS_FILE_EXTENSION);
        final Point3dFile rotationsFile = new Point3dFile(context, measurementIdentifier,
                Point3dFile.ROTATIONS_FOLDER_NAME, Point3dFile.ROTATION_FILE_EXTENSION);
        final Point3dFile directionsFile = new Point3dFile(context, measurementIdentifier,
                Point3dFile.DIRECTIONS_FOLDER_NAME, Point3dFile.ROTATION_FILE_EXTENSION);
        insertPoint3d(accelerationsFile, 1501662635973L, 10.1189575, -0.15088624, 0.2921924);
        insertPoint3d(accelerationsFile, 1501662635981L, 10.116563, -0.16765137, 0.3544629);
        insertPoint3d(accelerationsFile, 1501662635983L, 10.171648, -0.2921924, 0.3784131);
        insertPoint3d(rotationsFile, 1501662635981L, 0.001524045, 0.0025423833, -0.0010279021);
        insertPoint3d(rotationsFile, 1501662635990L, 0.001524045, 0.0025423833, -0.016474236);
        insertPoint3d(rotationsFile, 1501662635993L, -0.0064654383, -0.0219587, -0.014343708);
        insertPoint3d(directionsFile, 1501662636010L, 7.65, -32.4, -71.4);
        insertPoint3d(directionsFile, 1501662636030L, 7.65, -32.550003, -71.700005);
        insertPoint3d(directionsFile, 1501662636050L, 7.65, -33.15, -71.700005);

        persistence.storePointMetaData(
                new PointMetaData(3, 3, 3, MeasurementSerializer.PERSISTENCE_FILE_FORMAT_VERSION),
                measurementIdentifier);
        persistence.setStatus(measurement.getIdentifier(), MeasurementStatus.FINISHED);

        ContentProviderClient client = null;
        try {
            client = resolver.acquireContentProviderClient(AUTHORITY);

            if (client == null)
                throw new IllegalStateException(
                        String.format("Unable to acquire client for content provider %s", AUTHORITY));

            MeasurementContentProviderClient loader = new MeasurementContentProviderClient(measurementIdentifier,
                    client, AUTHORITY);
            MeasurementSerializer serializer = new MeasurementSerializer(new DefaultFileAccess());
            File compressedTransferTempFile = serializer.loadSerializedCompressed(loader, measurement.getIdentifier(),
                    persistence);

            String jwtAuthToken = "replace me";
            SyncPerformer performer = new SyncPerformer(
                    InstrumentationRegistry.getInstrumentation().getTargetContext());
            SyncResult syncResult = new SyncResult();
            try {
                boolean result = performer.sendData(new HttpConnection(), syncResult, "https://localhost:8080",
                        measurementIdentifier, "garbage", compressedTransferTempFile, new UploadProgressListener() {
                            @Override
                            public void updatedProgress(float percent) {
                                Log.d(TAG, String.format("Upload Progress %f", percent));
                            }
                        }, jwtAuthToken);
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

    /**
     * Prints the MD5 of an input stream. This is useful for debugging purposes.
     *
     * @param stream The stream to print the MD5 sum for.
     * @throws IOException Thrown if the stream is not readable.
     * @throws NoSuchAlgorithmException Thrown if MD5 Algorithm is not supported
     */
    @SuppressWarnings("unused") // TODO - because?
    private void printMD5(final @NonNull InputStream stream) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] content = new byte[stream.available()];
        // noinspection ResultOfMethodCallIgnored - because we don't care
        stream.read(content);
        byte[] theDigest = md.digest(content);
        StringBuilder sb = new StringBuilder(theDigest.length * 2);
        for (byte b : theDigest)
            sb.append(String.format("%02x", b));
        Log.i(TAG, sb.toString());
    }
}
