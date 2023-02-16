package de.cyface.persistence;

import java.io.BufferedOutputStream;
import java.io.File;

import android.content.Context;

import androidx.annotation.NonNull;

import de.cyface.persistence.serialization.Point3DFile;

/**
 * Interface access {@link File}s. This helps to mock the file access away during testing.
 *
 * @author Amin Schnabel
 * @version 1.1.0
 * @since 3.0.0
 */
public interface FileAccessLayer {

    /**
     * Writes the content of the {@param file} to the provided {@param bufferedOutputStream} using a buffer for
     * performance reasons.
     *
     * @param file the {@link File} which content should be written to the output stream
     * @param bufferedOutputStream the {@link BufferedOutputStream} the {@param file} content should be written to
     */
    void writeToOutputStream(@NonNull File file, @NonNull BufferedOutputStream bufferedOutputStream);

    /**
     * Loads the bytes form a file.
     * <p>
     * This code is only used in the test to deserialize the serialized data for testing.
     * However, it's in here to be able to inject a mocked file access layer in the tests.
     *
     * @param file The {@link File} to load the bytes from.
     * @return The bytes.
     */
    byte[] loadBytes(final File file);

    /**
     * Returns the path of the parent directory containing all {@link Point3DFile}s of a specified type.
     * This directory is deleted when the app is uninstalled and can only be accessed by the app.
     *
     * @param context The {@link Context} required to access the underlying persistence layer.
     * @param folderName The folder name defining the type of point 3d
     */
    @NonNull
    File getFolderPath(@NonNull final Context context, @NonNull String folderName);

    /**
     * Generates the path to a specific binary file.
     *
     * @param context The {@link Context} required to access the underlying persistence layer.
     * @param measurementId the identifier of the measurement for which the file is to be found
     * @param folderName The folder name defining the point 3d type of the file
     * @param fileExtension the extension of the file type
     * @return The {@link File}
     */
    @NonNull
    File getFilePath(@NonNull final Context context, final long measurementId, final String folderName,
            final String fileExtension);

    /**
     * Creates a {@link File} for Cyface binary data.
     *
     * @param context The {@link Context} required to access the underlying persistence layer.
     * @param measurementId the identifier of the measurement for which the file is to be found
     * @param folderName The folder name defining the point 3d type of the file
     * @param fileExtension the extension of the file type
     * @return The create {@code File}.
     * @throws IllegalStateException when the measurement folder does not exist.
     */
    @NonNull
    File createFile(@NonNull final Context context, final long measurementId, final String folderName,
            final String fileExtension);

    /**
     * Method to write data to a file.
     *
     * @param file The {@link File} referencing the path to write the data to
     * @param data The bytes to write to the file
     * @param append True if the data should be appended to an existing file.
     */
    void write(final File file, final byte[] data, final boolean append);
}
