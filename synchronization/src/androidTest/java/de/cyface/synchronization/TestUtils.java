package de.cyface.synchronization;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.support.annotation.NonNull;

import de.cyface.persistence.GpsPointsTable;
import de.cyface.persistence.MagneticValuePointTable;
import de.cyface.persistence.MeasurementTable;
import de.cyface.persistence.MeasuringPointsContentProvider;
import de.cyface.persistence.RotationPointTable;
import de.cyface.persistence.SamplePointTable;

/**
 * Contains utility methods and constants required by the tests within the synchronization project.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.1.0
 */
final class TestUtils {
    /**
     * The tag used to identify Logcat messages from this module.
     */
    final static String TAG = "de.cyface.synchronization.test";
    /**
     * The content provider authority used during tests. This must be the same as in the manifest and the authenticator configuration.
     */
    final static String AUTHORITY = "de.cyface.synchronization.provider.test";
    /**
     * The account type used during testing. This must be the same as in the authenticator configuration.
     */
    final static String ACCOUNT_TYPE = "de.cyface.synchronization.account.test";

    static Uri getMeasurementUri() {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(MeasurementTable.URI_PATH).build();
    }

    static Uri getGeoLocationsUri() {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(GpsPointsTable.URI_PATH).build();
    }

    static Uri getAccelerationsUri() {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(SamplePointTable.URI_PATH).build();
    }

    static Uri getRotationsUri() {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(RotationPointTable.URI_PATH).build();
    }

    static Uri getDirectionsUri() {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(MagneticValuePointTable.URI_PATH).build();
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
    static void insertTestDirection(final @NonNull ContentResolver resolver, final long measurementIdentifier,
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
    static void insertTestRotation(final @NonNull ContentResolver resolver, final long measurementIdentifier,
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
    static void insertTestAcceleration(final @NonNull ContentResolver resolver, final long measurementIdentifier,
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
    static void insertTestGeoLocation(final @NonNull ContentResolver resolver, final long measurementIdentifier,
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
    static long insertTestMeasurement(final @NonNull ContentResolver resolver, final @NonNull String vehicle) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTable.COLUMN_FINISHED, true);
        values.put(MeasurementTable.COLUMN_VEHICLE, vehicle);
        Uri resultUri = resolver.insert(MeasuringPointsContentProvider.MEASUREMENT_URI, values);
        if (resultUri == null) {
            throw new IllegalStateException();
        }

        return Long.parseLong(resultUri.getLastPathSegment());
    }

    static int clearDatabase(final @NonNull ContentResolver resolver) {
        int ret = 0;
        ret += resolver.delete(MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI,null,null);
        ret += resolver.delete(MeasuringPointsContentProvider.ROTATION_POINTS_URI, null, null);
        ret += resolver.delete(MeasuringPointsContentProvider.SAMPLE_POINTS_URI, null, null);
        ret += resolver.delete(MeasuringPointsContentProvider.GPS_POINTS_URI, null, null);
        ret += resolver.delete(MeasuringPointsContentProvider.MEASUREMENT_URI, null, null);
        return ret;
    }
}
