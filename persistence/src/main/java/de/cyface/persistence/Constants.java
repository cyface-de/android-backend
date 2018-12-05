package de.cyface.persistence;

import java.io.File;

import android.os.Environment;

/**
 * Final static constants used by multiple classes.
 *
 * @author Armin Schnabel
 * @version 1.1.0
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
    public static final String TRANSFER_FILE_EXTENSION = "cyf";
    /**
     * The charset used to parse Strings (e.g. for JSON data)
     */
    public final static String DEFAULT_CHARSET = "UTF-8";
    /**
     * The path where files can be stored by the SDK, e.g. image material.
     * This data is not deleted when the app is uninstalled and has no access restrictions.
     */
    public final static String EXTERNAL_CYFACE_FOLDER_PATH = Environment.getExternalStorageDirectory().getPath()
            + File.separator + "Cyface";

    private Constants() {
        // Nothing to do here.
    }
}
