package de.cyface.synchronization;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import de.cyface.persistence.IdentifierTable;
import de.cyface.persistence.Persistence;

/**
 * Contains utility methods and constants required by the tests within the synchronization project.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.1.1
 * @since 2.1.0
 */
final class TestUtils {
    /**
     * The tag used to identify Logcat messages from this module.
     */
    final static String TAG = SharedConstants.TAG + ".test";
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
     * Path to an API available for testing
     */
    final static String TEST_API_URL = "https://s1.cyface.de/api/v2";

    static Uri getIdentifierUri() {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(IdentifierTable.URI_PATH).build();
    }

    /**
     * Delete all persistent storage such as identifiers and measurements.
     *
     * @return the number of measurements deleted
     */
    static int clear(final @NonNull Context context) {
        int ret = 0;
        Persistence persistence = new Persistence(context, AUTHORITY);
        persistence.clear();
        context.getContentResolver().delete(getIdentifierUri(), null, null);
        return ret;
    }
}
