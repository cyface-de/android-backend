package de.cyface.synchronization;

/**
 * Final static constants used by multiple classes.
 * FIXME: until tests run through again I wont merge the constants of both flavours, thus we have a 3rd shared const
 * file
 *
 * @author Armin Schnabel
 * @version 1.0.3
 * @since 2.5.0
 */
public final class SharedConstants {

    /**
     * Tag used to identify Logcat messages issued by instances of this package.
     */
    public final static String TAG = "de.cyface.sync";

    /**
     * The settings key used to identify the settings storing the device or rather installation identifier of the
     * current app. This identifier is used to anonymously group measurements from the same device together.
     * FIXME: is it a problem to use the same key for multiple apps?
     */
    public static final String DEVICE_IDENTIFIER_KEY = "de.cyface.identifier.device";

    private SharedConstants() {
        // Nothing to do here.
    }
}
