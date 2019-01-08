package de.cyface.synchronization;

/**
 * Final static constants used by multiple classes.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.3.1
 * @since 2.0.0
 */
public final class Constants {
    /**
     * Tag used to identify Logcat messages issued by instances of this package.
     */
    public final static String TAG = "de.cyface.sync";
    /**
     * This may be used by all implementing apps, thus, public
     */
    public final static String AUTH_TOKEN_TYPE = "de.cyface.auth_token_type";

    private Constants() {
        // Nothing to do here.
    }
}