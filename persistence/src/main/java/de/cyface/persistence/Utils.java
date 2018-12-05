package de.cyface.persistence;

import static de.cyface.persistence.Constants.FINISHED_MEASUREMENTS_PATH;
import static de.cyface.persistence.Constants.OPEN_MEASUREMENTS_PATH;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;

import android.util.Log;

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

    public static FileFilter directoryFilter() {
        return new FileFilter() {
            public boolean accept(File pathname) {
                return pathname.isDirectory();
            }
        };
    }

    public static File loadFile(final long measurementId, final String fileName, final String fileExtension) {
        final File fileFinished = new File(
                Utils.getFolderName(false, measurementId) + File.separator + fileName + "." + fileExtension);
        final File fileOpen = new File(
                Utils.getFolderName(true, measurementId) + File.separator + fileName + "." + fileExtension);
        if (!fileFinished.exists() && !fileOpen.exists()) {
            throw new IllegalStateException("Cannot load file because it does not yet exist");
        }
        return fileFinished.exists() ? fileFinished : fileOpen;
    }

    public static byte[] loadBytes(final File file) {
        try {
            final byte[] bytes = new byte[(int)file.length()];
            final BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(file));
            final DataInputStream inputStream = new DataInputStream(bufferedInputStream);
            inputStream.readFully(bytes);
            Log.d(Constants.TAG, "Read " + bytes.length + " bytes (from " + file.getPath() + ")");
            inputStream.close();
            bufferedInputStream.close();
            return bytes;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read file.");
        }
    }
}
