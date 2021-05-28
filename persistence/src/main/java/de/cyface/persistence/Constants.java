package de.cyface.persistence;

/**
 * Final static constants used by multiple classes.
 *
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 2.5.0
 */
public final class Constants {

    /**
     * Tag used to identify Logcat messages issued by instances of this package.
     */
    public final static String TAG = "de.cyface.persistence";
    /**
     * The file extension of the measurement file which is transmitted on synchronization.
     */
    @SuppressWarnings("SpellCheckingInspection")
    public static final String TRANSFER_FILE_EXTENSION = "ccyf";
    /**
     * The charset used to parse Strings (e.g. for JSON data)
     */
    public final static String DEFAULT_CHARSET = "UTF-8";

    private Constants() {
        // Nothing to do here.
    }
}
