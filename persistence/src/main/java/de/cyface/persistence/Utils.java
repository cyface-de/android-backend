package de.cyface.persistence;

import static de.cyface.persistence.Constants.FINISHED_MEASUREMENTS_PATH;
import static de.cyface.persistence.Constants.OPEN_MEASUREMENTS_PATH;

import java.io.File;
import java.io.IOException;

/**
 * Methods to access and find data in the filesystem.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 2.5.0
 */
public class Utils {

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
                getFolderName(true, measurementId) + File.separator + fileName + "." + fileExtension);
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
        final File measurementDir = new File(getFolderName(true, measurementId));
        if (!measurementDir.exists()) {
            if (!measurementDir.mkdirs()) {
                throw new IllegalStateException(
                        "Unable to create directory for measurement data: " + measurementDir.getAbsolutePath());
            }
        }
        return measurementDir;
    }
}
