package de.cyface.synchronization;

/**
 * Contains utility methods and constants required by the tests within the synchronization project.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.3.2
 * @since 2.1.0
 */
public final class TestUtils {
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
    final static String DEFAULT_USERNAME = "test";
    /**
     * A password used by the tests to set up a Cyface account for synchronization.
     */
    final static String DEFAULT_PASSWORD = "secret";
    /**
     * Path to an API available for testing.
     */
    @SuppressWarnings("unused") // because this is used in the cyface flavour
    final static String TEST_API_URL = "https://s1.cyface.de/api/v2";
}
