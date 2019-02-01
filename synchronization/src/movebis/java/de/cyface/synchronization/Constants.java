package de.cyface.synchronization;

/**
 * Final static constants used by multiple classes.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.0
 * @since 2.0.0
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
    @SuppressWarnings("unused") // Because this allows the sdk integrating app to add a sync account
    public final static String AUTH_TOKEN_TYPE = "de.cyface.jwt";

    private Constants() {
        // Nothing to do here.
    }
}