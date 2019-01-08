package de.cyface.synchronization;

/**
 * Final static constants used by multiple classes.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.0.3
 * @since 2.0.0
 *
 * * FIXME: Check in pull request if the AUTH_TOKEN_TYPE was moved since dev or even since before the BINARY epic. They must be the same as before but are differently defined for each project
 */
public final class Constants {

    /**
     * Tag used to identify Logcat messages issued by instances of this package.
     */
    public final static String TAG = "de.cyface.sync";

    // TODO: Change these strings between two (STADTRADELN) campaigns.

    /**
     * The Cyface account type used to identify all Cyface system accounts.
     */
    public final static String ACCOUNT_TYPE = "de.cyface";
    public final static String AUTH_TOKEN_TYPE = "de.cyface.jwt";

    private Constants() {
        // Nothing to do here.
    }
}