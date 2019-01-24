package de.cyface.persistence;

import java.io.File;
import java.io.FileFilter;

import android.content.Context;
import androidx.annotation.NonNull;
import de.cyface.persistence.model.Point3d;
import de.cyface.persistence.serialization.Point3dFile;

/**
 * Interface access {@link File}s. This helps to mock the file access away during testing.
 *
 * @author Amin Schnabel
 * @version 1.0.0
 * @since 3.0.0
 */
public interface FileAccessLayer {

    /**
     * Returns a {@link FileFilter} which can be used to get directories from a file list.
     *
     * @return the {@code FileFilter}
     */
    @NonNull
    FileFilter directoryFilter();

    /**
     * Loads the bytes form a file.
     *
     * @param file The {@link File} to load the bytes from.
     * @return The bytes.
     */
    @NonNull
    public byte[] loadBytes(final File file);

    /**
     * Returns the path of the parent directory containing all {@link Point3dFile}s of a specified type.
     * This directory is deleted when the app is uninstalled and can only be accessed by the app.
     *
     * @param context The {@link Context} required to access the underlying persistence layer.
     * @param folderName The folder name defining the type of {@link Point3d}
     */
    @NonNull
    public File getFolderPath(@NonNull final Context context, @NonNull String folderName);

    /**
     * Generates the path to a specific binary file.
     *
     * @param context The {@link Context} required to access the underlying persistence layer.
     * @param measurementId the identifier of the measurement for which the file is to be found
     * @param folderName The folder name defining the {@link Point3d} type of the file
     * @param fileExtension the extension of the file type
     * @return The {@link File}
     */
    @NonNull
    public File getFilePath(@NonNull final Context context, final long measurementId, final String folderName,
            final String fileExtension);

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
    @NonNull
    public File createFile(@NonNull final Context context, final long measurementId, final String folderName,
            final String fileExtension);

    /**
     * Method to write data to a file.
     *
     * @param file The {@link File} referencing the path to write the data to
     * @param data The bytes to write to the file
     * @param append True if the data should be appended to an existing file.
     */
    public void write(final File file, final byte[] data, final boolean append);
}