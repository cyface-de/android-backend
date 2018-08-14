package de.cyface.synchronization;

import static de.cyface.synchronization.TestUtils.AUTHORITY;
import static de.cyface.synchronization.TestUtils.insertTestAcceleration;
import static de.cyface.synchronization.TestUtils.insertTestDirection;
import static de.cyface.synchronization.TestUtils.insertTestGeoLocation;
import static de.cyface.synchronization.TestUtils.insertTestMeasurement;
import static de.cyface.synchronization.TestUtils.insertTestRotation;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.FlakyTest;
import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

/**
 * Tests the actual data transmission code. Since this test requires a running Movebis API server, and communicates with
 * that server, it is a flaky test and a large test.
 *
 * @author Klemens Muthmann
 * @version 1.0.1
 * @since 2.0.0
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
@FlakyTest
@Ignore
public class MovebisDataTransmissionTest {

    /**
     * The tag used to identify messages from logcat.
     */
    private static final String TAG = "de.cyface.test";

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
    public void testUploadSomeBytesViaMultiPart() throws SynchronisationException {
        ContentResolver resolver = InstrumentationRegistry.getTargetContext().getContentResolver();
        long measurementIdentifier = insertTestMeasurement(resolver, "UNKOWN");
        insertTestGeoLocation(resolver, measurementIdentifier, 1503055141000L, 49.9304133333333, 8.82831833333333, 0.0,
                940);
        insertTestGeoLocation(resolver, measurementIdentifier, 1503055142000L, 49.9305066666667, 8.82814,
                8.78270530700684, 840);
        insertTestAcceleration(resolver, measurementIdentifier, 1501662635973L, 10.1189575, -0.15088624, 0.2921924);
        insertTestAcceleration(resolver, measurementIdentifier, 1501662635981L, 10.116563, -0.16765137, 0.3544629);
        insertTestAcceleration(resolver, measurementIdentifier, 1501662635983L, 10.171648, -0.2921924, 0.3784131);
        insertTestRotation(resolver, measurementIdentifier, 1501662635981L, 0.001524045, 0.0025423833, -0.0010279021);
        insertTestRotation(resolver, measurementIdentifier, 1501662635990L, 0.001524045, 0.0025423833, -0.016474236);
        insertTestRotation(resolver, measurementIdentifier, 1501662635993L, -0.0064654383, -0.0219587, -0.014343708);
        insertTestDirection(resolver, measurementIdentifier, 1501662636010L, 7.65, -32.4, -71.4);
        insertTestDirection(resolver, measurementIdentifier, 1501662636030L, 7.65, -32.550003, -71.700005);
        insertTestDirection(resolver, measurementIdentifier, 1501662636050L, 7.65, -33.15, -71.700005);

        ContentProviderClient client = null;
        try {
            client = resolver.acquireContentProviderClient(AUTHORITY);

            if (client == null)
                throw new IllegalStateException(
                        String.format("Unable to acquire client for content provider %s", AUTHORITY));

            MeasurementContentProviderClient loader = new MeasurementContentProviderClient(measurementIdentifier,
                    client, AUTHORITY);
            MeasurementSerializer serializer = new MeasurementSerializer();
            InputStream measurementData = serializer.serialize(loader);
            // printMD5(measurementData);

            String jwtAuthToken = "replace me";
            SyncPerformer performer = new SyncPerformer(InstrumentationRegistry.getTargetContext());
            int result = performer.sendData("https://localhost:8080", measurementIdentifier, "garbage", measurementData,
                    new UploadProgressListener() {
                        @Override
                        public void updatedProgress(float percent) {
                            Log.d(TAG, String.format("Upload Progress %f", percent));
                        }
                    }, jwtAuthToken);
            assertThat(result, is(equalTo(201)));
        } finally {
            if (client != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    client.close();
                } else {
                    client.release();
                }
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
    private void printMD5(final @NonNull InputStream stream) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] content = new byte[stream.available()];
        stream.read(content);
        byte[] thedigest = md.digest(content);
        StringBuilder sb = new StringBuilder(thedigest.length * 2);
        for (byte b : thedigest)
            sb.append(String.format("%02x", b));
        Log.i(TAG, sb.toString());
    }
}
