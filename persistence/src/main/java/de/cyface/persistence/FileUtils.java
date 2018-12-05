package de.cyface.persistence;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;

import android.content.Context;
import android.util.Log;

/**
 * Methods to access and find data in the filesystem.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.0.0
 */
public class FileUtils {
    /**
     * Returns the path of the parent directory containing all measurement relevant data.
     * This directory is deleted when the app is uninstalled and can only be accessed by the app.
     */
    private String measurementsDirPath;
    /**
     * Returns the path of the parent directory containing all folders of open measurements.
     * This directory is deleted when the app is uninstalled and can only be accessed by the app.
     */
    private String openMeasurementsDirPath;
    /**
     * Returns the path of the parent directory containing all folders of finished measurements.
     * This directory is deleted when the app is uninstalled and can only be accessed by the app.
     */
    private String finishedMeasurementsDirPath;
    /**
     * Returns the path of the parent directory containing all folders of synchronized measurements.
     * This directory is deleted when the app is uninstalled and can only be accessed by the app.
     */
    private String synchronizedMeasurementsDirPath;

    /**
     * @param context The {@link Context} required to locate the app's internal storage directory.
     */
    public FileUtils(final Context context) {
        this.measurementsDirPath = context.getFilesDir() + File.separator + "measurements";
        this.openMeasurementsDirPath = measurementsDirPath + File.separator + "open";
        this.finishedMeasurementsDirPath = measurementsDirPath + File.separator + "finished";
        this.synchronizedMeasurementsDirPath = measurementsDirPath + File.separator + "synced";
    }

    /**
     * Returns the path to the folder containing the open measurement data.
     * 
     * @param measurementId The identifier of the measurement.
     * @return The folder path.
     */
    public String getOpenFolderName(final long measurementId) {
        return openMeasurementsDirPath + File.separator + measurementId;
    }

    /**
     * Returns the path to the folder containing the finished measurement data.
     *
     * @param measurementId The identifier of the measurement.
     * @return The folder path.
     */
    public String getFinishedFolderName(final long measurementId) {
        return finishedMeasurementsDirPath + File.separator + measurementId;
    }

    /**
     * Returns the path to the folder containing the synchronized measurement data.
     *
     * @param measurementId The identifier of the measurement.
     * @return The folder path.
     */
    public String getSyncedFolderName(final long measurementId) {
        return synchronizedMeasurementsDirPath + File.separator + measurementId;
    }

    /**
     * Creates the path to a file containing data in the Cyface binary format.
     *
     * @return The path to the file as an URL.
     */
    public File createFile(final long measurementId, final String fileName, final String fileExtension) {
        final File file = new File(getOpenFolderName(measurementId) + File.separator + fileName + "." + fileExtension);
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

    /**
     * Creates and returns the path to the folder where a new open measurement can be stored.
     *
     * @param measurementId The identifier of the measurement.
     * @return The {@link File} pointing to the measurement folder.
     */
    public File getAndCreateDirectory(final long measurementId) {
        final File measurementDir = new File(getOpenFolderName(measurementId));
        if (!measurementDir.exists()) {
            if (!measurementDir.mkdirs()) {
                throw new IllegalStateException(
                        "Unable to create directory for measurement data: " + measurementDir.getAbsolutePath());
            }
        }
        return measurementDir;
    }

    /**
     * Returns a {@link FileFilter} which can be used to get directories from a file list.
     *
     * @return the {@code FileFilter}
     */
    public static FileFilter directoryFilter() {
        return new FileFilter() {
            public boolean accept(File pathname) {
                return pathname.isDirectory();
            }
        };
    }

    /**
     * Returns the {@link File} pointing to the actual file. There is no need to define if the measurement
     * is open or finished as there is only one such file allowed at all times.
     *
     * @param measurementId The identifier of the measurement of which the file is part of
     * @param fileName The name of the file
     * @param fileExtension The file extension of the file
     * @return A File pointer to the file.
     */
    public File getFile(final long measurementId, final String fileName, final String fileExtension) {
        final File fileFinished = new File(
                getFinishedFolderName(measurementId) + File.separator + fileName + "." + fileExtension);
        final File fileOpen = new File(
                getOpenFolderName(measurementId) + File.separator + fileName + "." + fileExtension);
        final File fileSynced = new File(
                getSyncedFolderName(measurementId) + File.separator + fileName + "." + fileExtension);
        if (!fileFinished.exists() && !fileOpen.exists() && !fileSynced.exists()) {
            throw new IllegalStateException("Cannot load file because it does not yet exist");
        }
        if ((fileOpen.exists() && fileFinished.exists()) || (fileOpen.exists() && fileSynced.exists())
                || (fileFinished.exists() && fileSynced.exists())) {
            throw new IllegalStateException("Cannot load file because there is are multiple instances of the file");
        }
        return fileFinished.exists() ? fileFinished : fileOpen.exists() ? fileOpen : fileSynced;
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

    public String getMeasurementsDirPath() {
        return measurementsDirPath;
    }

    public String getOpenMeasurementsDirPath() {
        return openMeasurementsDirPath;
    }

    public String getFinishedMeasurementsDirPath() {
        return finishedMeasurementsDirPath;
    }

    public String getSynchronizedMeasurementsDirPath() {
        return synchronizedMeasurementsDirPath;
    }
}
