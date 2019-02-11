package de.cyface.synchronization;

/**
 * Contains constants and utility methods required during testing.
 *
 * @author Klemens Muthmann
 * @version 1.1.1
 * @since 2.1.0
 */
public final class TestUtils {

    /**
     * The content provider authority used for testing.
     */
    public final static String AUTHORITY = "de.cyface.synchronization.provider.test";
    /**
     * The account type used during testing. This must be the same as in the authenticator configuration.
     */
    public final static String ACCOUNT_TYPE = "de.cyface.synchronization.account.test";

    /**
     * Private constructor to avoid instantiation of utility class.
     */
    private TestUtils() {
        // Nothing to do here.
    }
}
