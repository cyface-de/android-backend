package de.cyface.persistence;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import de.cyface.persistence.model.Measurement;

/**
 * Utility class containing file methods used by multiple classes.
 *
 * @author Armin Schnabel
 * @version 2.1.0
 * @since 3.0.0
 */
public final class FileUtils {

    /**
     * The name of the file containing acceleration data
     */
    public final static String ACCELERATIONS_FILE_NAME = "a";
    /**
     * The name of the file containing acceleration data
     */
    public final static String ACCELERATIONS_FILE_EXTENSION = "cyfa";
    /**
     * The name of the file containing rotation data
     */
    public final static String ROTATIONS_FILE_NAME = "r";
    /**
     * The name of the file containing rotation data
     */
    public final static String ROTATION_FILE_EXTENSION = "cyfr";
    /**
     * The name of the file containing direction data
     */
    public final static String DIRECTION_FILE_NAME = "d";
    /**
     * The name of the file containing direction data
     */
    public final static String DIRECTION_FILE_EXTENSION = "cyfd";

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

    /**
     * Returns the path of the parent directory containing all measurement relevant data.
     * This directory is deleted when the app is uninstalled and can only be accessed by the app.
     *
     * @param context The {@link Context} required to access the underlying persistence layer.
     */
    private static File getMeasurementsRootFolder(@NonNull final Context context) {
        return new File(context.getFilesDir() + File.separator + "measurements");
    }

    /**
     * Returns the folder containing measurements of a specific status.
     *
     * @param context The {@link Context} required to access the underlying persistence layer.
     * @param status the {@link Measurement.MeasurementStatus} for the requested measurements folder
     * @return the {@link File} pointing to the measurements folder of the specified status
     */
    static File getMeasurementsFolder(@NonNull final Context context,
            @NonNull final Measurement.MeasurementStatus status) {
        final String folderName;
        switch (status) {
            case OPEN:
                folderName = "open";
                break;
            case PAUSED:
                folderName = "paused";
                break;
            case SYNCED:
                folderName = "synced";
                break;
            case FINISHED:
                folderName = "finished";
                break;
            case CORRUPTED:
                folderName = "corrupted";
                break;
            default:
                throw new IllegalStateException("Undefined MeasurementState");
        }
        return new File(getMeasurementsRootFolder(context) + File.separator + folderName);
    }

    /**
     * Generates the {@link Measurement} folder path for a specified {@link Measurement.MeasurementStatus}.
     *
     * @param context The {@link Context} required to access the underlying persistence layer.
     * @param status The status for which the path should be generates.
     * @return The {@link File} link to the measurement's folder.
     */
    public static File generateMeasurementFolderPath(@NonNull final Context context,
            @NonNull final Measurement.MeasurementStatus status, final long measurementId) {
        final File measurementFolder = getMeasurementsFolder(context, status);
        return new File(measurementFolder.getPath() + File.separator + measurementId);
    }

    /**
     * Generates the path to a specific {@link Measurement} binary file.
     *
     * @param measurement the measurement for which the file name is to be generated.
     * @param fileName the name of the file
     * @param fileExtension the extension of the file
     * @return The {@link File} link.
     */
    public static File generateMeasurementFilePath(@NonNull final Measurement measurement, final String fileName,
            final String fileExtension) {
        final File measurementFolder = measurement.getMeasurementFolder();
        return new File(measurementFolder.getPath() + File.separator + fileName + "." + fileExtension);
    }
}
