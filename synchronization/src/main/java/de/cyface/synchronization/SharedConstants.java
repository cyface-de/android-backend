package de.cyface.synchronization;

/**
 * Final static constants used by multiple classes.
 * FIXME: until tests run through again I wont merge the constants of both flavours, thus we have a 3rd shared const file
 *
 * @author Armin Schnabel
 * @version 1.0.2
 * @since 2.5.0
 */
public final class SharedConstants {

    /**
     * Tag used to identify Logcat messages issued by instances of this package.
     */
    public final static String TAG = "de.cyface.sync";

    //FIXME: following two vars are copied from androidTestMOVEBIS
    /**
     * The Cyface account type used to identify all Cyface system accounts.
     */
    public final static String ACCOUNT_TYPE = "de.cyface";
    public final static String AUTH_TOKEN_TYPE = "de.cyface.jwt";

    /**
     * The settings key used to identify the settings storing the device or rather installation identifier of the
     * current app. This identifier is used to anonymously group measurements from the same device together.
     */
    public static final String DEVICE_IDENTIFIER_KEY = "de.cyface.identifier.device";

    private SharedConstants() {
        // Nothing to do here.
    }
}
