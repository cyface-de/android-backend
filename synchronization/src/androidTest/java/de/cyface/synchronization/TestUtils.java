package de.cyface.synchronization;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import androidx.annotation.NonNull;
import de.cyface.persistence.GeoLocationsTable;
import de.cyface.persistence.IdentifierTable;
import de.cyface.persistence.MeasurementTable;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.Point3d;
import de.cyface.persistence.model.Vehicle;
import de.cyface.utils.Validate;

/**
 * Contains utility methods and constants required by the tests within the synchronization project.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.2.0
 * @since 2.1.0
 */
final class TestUtils {
    /**
     * The tag used to identify Logcat messages from this module.
     */
    final static String TAG = Constants.TAG + ".test";
    /**
     * The content provider authority used during tests. This must be the same as in the manifest and the authenticator
     * configuration.
     */
    final static String AUTHORITY = "de.cyface.synchronization.test.provider";
    /**
     * The account type used during testing. This must be the same as in the authenticator configuration.
     */
    final static String ACCOUNT_TYPE = "de.cyface.synchronization.test";
    /**
     * An username used by the tests to set up a Cyface account for synchronization.
     */
    final static String DEFAULT_USERNAME = "admin";
    /**
     * A password used by the tests to set up a Cyface account for synchronization.
     */
    final static String DEFAULT_PASSWORD = "secret";

    /**
     * Path to an API available for testing.
     */
    final static String TEST_API_URL = "https://s1.cyface.de:9090/api/v2";

    static Uri getMeasurementUri() {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(MeasurementTable.URI_PATH).build();
    }

    static Uri getGeoLocationsUri() {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(GeoLocationsTable.URI_PATH).build();
    }

    static Uri getIdentifierUri() {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(IdentifierTable.URI_PATH).build();
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
        values.put(GeoLocationsTable.COLUMN_ACCURACY, accuracy);
        values.put(GeoLocationsTable.COLUMN_GPS_TIME, timestamp);
        values.put(GeoLocationsTable.COLUMN_LAT, lat);
        values.put(GeoLocationsTable.COLUMN_LON, lon);
        values.put(GeoLocationsTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
        values.put(GeoLocationsTable.COLUMN_SPEED, speed);
        resolver.insert(getGeoLocationsUri(), values);
    }

    /**
     * Inserts a test {@link Measurement} into the database {@link ContentProvider} accessed by the test. To add data to
     * the measurement use some or all of
     * {@link #insertTestGeoLocation(ContentResolver, long, long, double, double, double, int)},
     * {@link #insertTestAcceleration(ContentResolver, long, long, double, double, double)},
     * {@link #insertTestDirection(ContentResolver, long, long, double, double, double)} and
     * {@link #insertTestRotation(ContentResolver, long, long, double, double, double)}.
     *
     * @param resolver The client to access the content provider storing the data.
     * @param vehicle The {@link Vehicle} type of the measurement. A common value is {@link Vehicle#UNKNOWN} if you do
     *            not care.
     * @return The database identifier of the created measurement.
     */
    static long insertTestMeasurement(final @NonNull ContentResolver resolver, final @NonNull String vehicle) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTable.COLUMN_STATUS, MeasurementStatus.FINISHED.getDatabaseIdentifier());
        values.put(MeasurementTable.COLUMN_VEHICLE, vehicle);
        Uri resultUri = resolver.insert(getMeasurementUri(), values);
        if (resultUri == null) {
            throw new IllegalStateException();
        }

        final String measurementIdString = resultUri.getLastPathSegment();
        Validate.notNull(measurementIdString);
        return Long.parseLong(resultUri.getLastPathSegment());
    }

    /**
     * Delete all persistent database storage, i.e.:
     * - {@link IdentifierTable} content
     * - {@link Measurement} entries
     * - {@link GeoLocation}s
     * (!) This does not delete any {@link Point3d} data as it is not stored in the database.
     *
     * @param resolver The {@link ContentResolver} used to access the database
     * @return the number of rows deleted
     */
    static int clearDatabase(final @NonNull ContentResolver resolver) {
        int ret = 0;
        ret += resolver.delete(getGeoLocationsUri(), null, null);
        ret += resolver.delete(getMeasurementUri(), null, null);
        ret += resolver.delete(getIdentifierUri(), null, null);
        return ret;
    }
}
