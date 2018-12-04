package de.cyface.persistence;

import java.io.File;
import java.io.IOException;

import android.os.Environment;

/**
 * Final static constants used by multiple classes.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 2.5.0
 */
public final class Constants {

    /**
     * Tag used to identify Logcat messages issued by instances of this package.
     */
    public final static String TAG = "de.cyface.persistence";

    public static final String FILE_EXTENSION = "cyf";

    /**
     * The charset used to parse Strings (e.g. for JSON data)
     */
    public final static String DEFAULT_CHARSET = "UTF-8";
    /**
     * The disk where data can be stored by the SDK, e.g. image material, measurement data.
     */
    public final static String EXTERNAL_ROOT_PATH = Environment.getExternalStorageDirectory().getPath();

    /**
     * The path where files can be stored by the SDK, e.g. image material.
     * This data is not deleted when the app is uninstalled.
     */
    public final static String EXTERNAL_CYFACE_PATH = EXTERNAL_ROOT_PATH + File.separator + "Cyface";

    public static final String MEASUREMENTS_DIRECTORY = EXTERNAL_CYFACE_PATH + File.separator + "measurements";
    public final static String OPEN_MEASUREMENTS_PATH = MEASUREMENTS_DIRECTORY + File.separator + "open";
    public final static String FINISHED_MEASUREMENTS_PATH = MEASUREMENTS_DIRECTORY + File.separator + "finished";

    public static String getFolderName(final boolean isOpen, final long measurementId) {
        return (isOpen ? OPEN_MEASUREMENTS_PATH : FINISHED_MEASUREMENTS_PATH) + File.separator + measurementId;
    }

    /**
     * Creates the path to a file containing data in the Cyface binary format.
     * 
     * @return The path to the file as an URL.
     */
    public static File createFile(final long measurementId, final String fileName, final String fileExtension) {
        final File file = new File(
                Constants.getFolderName(true, measurementId) + File.separator + fileName + "." + fileExtension);
        if (!file.exists()) {
            try {
                final boolean success = file.createNewFile();
                if (!success) {
                    throw new IOException("File not created");
                }
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Unable to create file for measurement data: " + file.getAbsolutePath());
            }
        }
        return file;
    }

    public static File getAndCreateDirectory(final long measurementId) {
        final File measurementDir = new File(Constants.getFolderName(true, measurementId));
        if (!measurementDir.exists()) {
            if (!measurementDir.mkdirs()) {
                throw new IllegalStateException(
                        "Unable to create directory for measurement data: " + measurementDir.getAbsolutePath());
            }
        }
        return measurementDir;
    }

    private Constants() {
        // Nothing to do here.
    }
}
