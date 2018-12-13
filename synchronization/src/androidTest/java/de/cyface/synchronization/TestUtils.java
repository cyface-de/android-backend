package de.cyface.synchronization;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import androidx.annotation.NonNull;

import de.cyface.persistence.IdentifierTable;

/**
 * Contains utility methods and constants required by the tests within the synchronization project.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.0.5
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
    public final static String DEFAULT_USERNAME = "admin";
    /**
     * A password used by the tests to set up a Cyface account for synchronization.
     */
    public final static String DEFAULT_PASSWORD = "secret";

    /**
     * Path to an API available for testing.
     * TODO: s1 url proxy /api/v2 didn't work with local https destination
     * // testing: https://s1.cyface.de:9090/api/v2
     * // local: https://192.168.1.146:8080/api/v2
     */
    public final static String TEST_API_URL = "https://s1.cyface.de:9090/api/v2";

    static Uri getIdentifierUri() {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(IdentifierTable.URI_PATH).build();
    }

    /**
     * Inserts a test direction into the database content provider accessed by the test.
     *
     * @param resolver The client to access the content provider storing the data.
     * @param deviceId unique id for the device
     * @param nextMeasurementIdentifier The device wide unique identifier of the next test measurement.
     */
    static void insertTestIndentifiers(final @NonNull ContentResolver resolver, final String deviceId, final long nextMeasurementIdentifier) {
        ContentValues values = new ContentValues();
        values.put(IdentifierTable.COLUMN_DEVICE_ID, deviceId);
        values.put(IdentifierTable.COLUMN_NEXT_MEASUREMENT_ID, nextMeasurementIdentifier);
        resolver.insert(getIdentifierUri(), values);
    }

    static int clearDatabase(final @NonNull ContentResolver resolver) {
        int ret = 0;
        ret += resolver.delete(getIdentifierUri(), null, null);
        return ret;
    }
}
