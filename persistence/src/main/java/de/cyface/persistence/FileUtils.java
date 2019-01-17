package de.cyface.persistence;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import de.cyface.persistence.model.Point3d;
import de.cyface.persistence.serialization.Point3dFile;
import de.cyface.utils.Validate;

/**
 * Utility class containing file methods used by multiple classes.
 *
 * @author Armin Schnabel
 * @version 2.2.0
 * @since 3.0.0
 */
public final class FileUtils {

    /**
     * Returns a {@link FileFilter} which can be used to get directories from a file list.
     *
     * @return the {@code FileFilter}
     */
    static FileFilter directoryFilter() {
        return new FileFilter() {
            public boolean accept(File pathname) {
                return pathname.isDirectory();
            }
        };
    }

    /**
     * Loads the bytes form a file.
     *
     * @param file The {@link File} to load the bytes from.
     * @return The bytes.
     */
    public static byte[] loadBytes(final File file) {
        Validate.isTrue(file.exists());
        final byte[] bytes = new byte[(int)file.length()];

        try {
            BufferedInputStream bufferedInputStream = null;
            try {
                bufferedInputStream = new BufferedInputStream(new FileInputStream(file));
                DataInputStream inputStream = null;
                try {
                    inputStream = new DataInputStream(bufferedInputStream);
                    try {
                        inputStream.readFully(bytes);
                        Log.d(Constants.TAG, "Read " + bytes.length + " bytes (from " + file.getPath() + ")");
                    } finally {
                        inputStream.close();
                        bufferedInputStream.close();
                    }
                } finally {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                }
                return bytes;
            } finally {
                if (bufferedInputStream != null) {
                    bufferedInputStream.close();
                }
            }
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to read file.");
        }
    }

    /**
     * Returns the path of the parent directory containing all {@link Point3dFile}s of a specified type.
     * This directory is deleted when the app is uninstalled and can only be accessed by the app.
     *
     * @param context The {@link Context} required to access the underlying persistence layer.
     * @param folderName The folder name defining the type of {@link Point3d}
     */
    private static File getFolderPath(@NonNull final Context context, @NonNull String folderName) {
        return new File(context.getFilesDir() + File.separator + folderName);
    }

    /**
     * Generates the path to a specific binary file.
     *
     * @param context The {@link Context} required to access the underlying persistence layer.
     * @param measurementId the identifier of the measurement for which the file is to be found
     * @param folderName The folder name defining the {@link Point3d} type of the file
     * @param fileExtension the extension of the file type
     * @return The {@link File}
     */
    public static File getFilePath(@NonNull final Context context, final long measurementId, final String folderName,
            final String fileExtension) {
        final File folder = getFolderPath(context, folderName);
        return new File(folder.getPath() + File.separator + measurementId + "." + fileExtension);
    }

    /**
     * Creates a {@link File} for Cyface binary data.
     *
     * @param context The {@link Context} required to access the underlying persistence layer.
     * @param measurementId the identifier of the measurement for which the file is to be found
     * @param folderName The folder name defining the {@link Point3d} type of the file
     * @param fileExtension the extension of the file type
     * @return The create {@code File}.
     * @throws IllegalStateException when the measurement folder does not exist.
     */
    public static File createFile(@NonNull final Context context, final long measurementId, final String folderName,
            final String fileExtension) {
        final File file = getFilePath(context, measurementId, folderName, fileExtension);
        Validate.isTrue(!file.exists(), "Failed to createFile as it already exists: " + file.getPath());
        try {
            if (!file.createNewFile()) {
                throw new IOException("Failed to createFile: " + file.getPath());
            }
        } catch (final IOException e) {
            throw new IllegalStateException("Failed createFile: " + file.getPath());
        }
        return file;
    }

    /**
     * Method to write data to a file.
     *
     * @param file The {@link File} referencing the path to write the data to
     * @param data The bytes to write to the file
     * @param append True if the data should be appended to an existing file.
     */
    public static void write(final File file, final byte[] data, final boolean append) {
        Validate.isTrue(file.exists(), "Failed to write to file as it does not exist: " + file.getPath());
        try {
            BufferedOutputStream outputStream = null;
            try {
                outputStream = new BufferedOutputStream(new FileOutputStream(file, append));
                try {
                    outputStream.write(data);
                } finally {
                    outputStream.close();
                }
            } finally {
                if (outputStream != null) {
                    outputStream.close();
                }
            }
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to append data to file.");
        }
    }
}