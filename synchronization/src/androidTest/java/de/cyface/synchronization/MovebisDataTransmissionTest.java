package de.cyface.synchronization;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.InputStream;

import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.FlakyTest;
import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import de.cyface.persistence.BuildConfig;
import de.cyface.persistence.GpsPointsTable;
import de.cyface.persistence.MagneticValuePointTable;
import de.cyface.persistence.MeasurementTable;
import de.cyface.persistence.MeasuringPointsContentProvider;
import de.cyface.persistence.RotationPointTable;
import de.cyface.persistence.SamplePointTable;

/**
 * Tests the actual data transmission code. Since this test requires a running Movebis API server, and communicates with
 * that server, it is a flaky test and a large test.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
@FlakyTest
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
    public void testUploadSomeBytesViaMultiPart() {
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
            client = resolver.acquireContentProviderClient(BuildConfig.provider);

            if (client == null)
                throw new IllegalStateException(
                        String.format("Unable to acquire client for content provider %s", BuildConfig.provider));

            MeasurementLoader loader = new MeasurementLoader(measurementIdentifier, client);
            MeasurementSerializer serializer = new MeasurementSerializer();
            InputStream measurementData = serializer.serialize(loader);

            SyncPerformer performer = new SyncPerformer(InstrumentationRegistry.getTargetContext());
            int result = performer.sendData("https://localhost:8080", measurementIdentifier, "garbage", measurementData,
                    new UploadProgressListener() {
                        @Override
                        public void updatedProgress(float percent) {
                            Log.d(TAG, String.format("Upload Progress %f", percent));
                        }
                    });
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
     * Inserts a test direction into the database content provider accessed by the test.
     *
     * @param resolver The client to access the content provider storing the data.
     * @param measurementIdentifier The device wide unique identifier of the test measurement.
     * @param timestamp A fake test timestamp of the direction.
     * @param x A fake test x coordinate of the direction.
     * @param y A fake test y coordinate of the direction.
     * @param z A fake test z coordinate of the direction.
     */
    private void insertTestDirection(final @NonNull ContentResolver resolver, final long measurementIdentifier,
            final long timestamp, final double x, final double y, final double z) {
        ContentValues values = new ContentValues();
        values.put(MagneticValuePointTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
        values.put(MagneticValuePointTable.COLUMN_IS_SYNCED, false);
        values.put(MagneticValuePointTable.COLUMN_MX, x);
        values.put(MagneticValuePointTable.COLUMN_MY, y);
        values.put(MagneticValuePointTable.COLUMN_MZ, z);
        values.put(MagneticValuePointTable.COLUMN_TIME, timestamp);
        resolver.insert(MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI, values);
    }

    /**
     * Inserts a test rotation into the database content provider accessed by the test.
     *
     * @param resolver The client to access the content provider storing the data.
     * @param measurementIdentifier The device wide unique identifier of the test measurement.
     * @param timestamp A fake test timestamp of the direction.
     * @param x A fake test x coordinate of the direction.
     * @param y A fake test y coordinate of the direction.
     * @param z A fake test z coordinate of the direction.
     */
    private void insertTestRotation(final @NonNull ContentResolver resolver, final long measurementIdentifier,
            final long timestamp, final double x, final double y, final double z) {
        ContentValues values = new ContentValues();
        values.put(RotationPointTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
        values.put(RotationPointTable.COLUMN_IS_SYNCED, false);
        values.put(RotationPointTable.COLUMN_RX, x);
        values.put(RotationPointTable.COLUMN_RY, y);
        values.put(RotationPointTable.COLUMN_RZ, z);
        values.put(RotationPointTable.COLUMN_TIME, timestamp);
        resolver.insert(MeasuringPointsContentProvider.ROTATION_POINTS_URI, values);
    }

    /**
     * Inserts a test acceleration into the database content provider accessed by the test.
     *
     * @param resolver The client to access the content provider storing the data.
     * @param measurementIdentifier The device wide unique identifier of the test measurement.
     * @param timestamp A fake test timestamp of the acceleration.
     * @param x A fake test x coordinate of the acceleration.
     * @param y A fake test y coordinate of the acceleration.
     * @param z A fake test z coordinate of the acceleration.
     */
    private void insertTestAcceleration(final @NonNull ContentResolver resolver, final long measurementIdentifier,
            final long timestamp, final double x, final double y, final double z) {
        ContentValues values = new ContentValues();
        values.put(SamplePointTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
        values.put(SamplePointTable.COLUMN_IS_SYNCED, false);
        values.put(SamplePointTable.COLUMN_AX, x);
        values.put(SamplePointTable.COLUMN_AY, y);
        values.put(SamplePointTable.COLUMN_AZ, z);
        values.put(SamplePointTable.COLUMN_TIME, timestamp);
        resolver.insert(MeasuringPointsContentProvider.SAMPLE_POINTS_URI, values);
    }

    /**
     * Inserts a test geo location into the database content provider accessed by the test.
     * 
     * @param resolver The client to access the content provider storing the data.
     * @param measurementIdentifier The device wide unique identifier of the test measurement.
     * @param timestamp A fake test timestamp of the geo location.
     * @param lat The fake test latitude of the geo location.
     * @param lon The fake test longitude of the geo location.
     * @param speed The fake test speed of the geo location.
     * @param accuracy The fake test accuracy of the geo location.
     */
    private void insertTestGeoLocation(final @NonNull ContentResolver resolver, final long measurementIdentifier,
            final long timestamp, final double lat, final double lon, final double speed, final int accuracy) {
        ContentValues values = new ContentValues();
        values.put(GpsPointsTable.COLUMN_ACCURACY, accuracy);
        values.put(GpsPointsTable.COLUMN_GPS_TIME, timestamp);
        values.put(GpsPointsTable.COLUMN_IS_SYNCED, false);
        values.put(GpsPointsTable.COLUMN_LAT, lat);
        values.put(GpsPointsTable.COLUMN_LON, lon);
        values.put(GpsPointsTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
        values.put(GpsPointsTable.COLUMN_SPEED, speed);
        resolver.insert(MeasuringPointsContentProvider.GPS_POINTS_URI, values);
    }

    /**
     * Inserts a test measurement into the database content provider accessed by the test. To add data to the
     * measurement use some or all of
     * {@link #insertTestGeoLocation(ContentResolver, long, long, double, double, double, int)},
     * {@link #insertTestAcceleration(ContentResolver, long, long, double, double, double)},
     * {@link #insertTestDirection(ContentResolver, long, long, double, double, double)} and
     * {@link #insertTestRotation(ContentResolver, long, long, double, double, double)}.
     * 
     * @param resolver The client to access the content provider storing the data.
     * @param vehicle The vehicle type of the measurement. A common value is "UNKNOWN" if you do not care.
     * @return The database identifier of the created measurement.
     */
    private long insertTestMeasurement(final @NonNull ContentResolver resolver, final @NonNull String vehicle) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTable.COLUMN_FINISHED, true);
        values.put(MeasurementTable.COLUMN_VEHICLE, vehicle);
        Uri resultUri = resolver.insert(MeasuringPointsContentProvider.MEASUREMENT_URI, values);
        if (resultUri == null) {
            throw new IllegalStateException();
        }

        return Long.parseLong(resultUri.getLastPathSegment());
    }
}
