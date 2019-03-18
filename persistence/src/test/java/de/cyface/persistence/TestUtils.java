package de.cyface.persistence;

/**
 * Contains constants and utility methods required during testing.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 4.0.0
 */
public final class TestUtils {

    /**
     * The content provider authority used for testing.
     */
    public final static String AUTHORITY = "de.cyface.persistence.provider.test";
    /**
     * The account type used during testing. This must be the same as in the authenticator configuration.
     */
    public final static String ACCOUNT_TYPE = "de.cyface.persistence.account.test";

    /**
     * Private constructor to avoid instantiation of utility class.
     */
    private TestUtils() {
        // Nothing to do here.
    }
}
