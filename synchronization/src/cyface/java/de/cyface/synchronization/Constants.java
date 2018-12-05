package de.cyface.synchronization;

/**
 * Final static constants used by multiple classes.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.3.0
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

    /**
     * The settings key used to identify the settings storing the device or rather installation identifier of the
     * current app. This identifier is used to anonymously group measurements from the same device together.
     */
    public static final String DEVICE_IDENTIFIER_KEY = "de.cyface.identifier.device";

    private Constants() {
        // Nothing to do here.
    }
}