package de.cyface.persistence.serialization;

import java.io.File;
import java.util.List;

import android.content.Context;
import androidx.annotation.NonNull;
import de.cyface.persistence.FileUtils;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.Point3d;
import de.cyface.utils.Validate;

/**
 * The file format to persist {@link Point3d}s such as accelerations, rotations and directions.
 *
 * @author Armin Schnabel
 * @version 3.0.0
 * @since 3.0.0
 */
public class Point3dFile implements FileSupport<List<Point3d>> {

    /**
     * The name of the folder containing acceleration data. Separating the files of each {@code Point3dFile} type should
     * improve performance when searching for files.
     */
    public static final String ACCELERATIONS_FOLDER_NAME = "accelerations";
    /**
     * The name of the folder containing rotation data. Separating the files of each {@code Point3dFile} type should
     * improve performance when searching for files.
     */
    public static final String ROTATIONS_FOLDER_NAME = "rotations";
    /**
     * The name of the folder containing direction data. Separating the files of each {@code Point3dFile} type should
     * improve performance when searching for files.
     */
    public static final String DIRECTIONS_FOLDER_NAME = "directions";
    /**
     * The file extension of files containing acceleration data. This makes sure no system-generated files in the
     * {@link #ACCELERATIONS_FOLDER_NAME} are identified as {@link Point3dFile}s.
     */
    public final static String ACCELERATIONS_FILE_EXTENSION = "cyfa";
    /**
     * The file extension of files containing rotation data. This makes sure no system-generated files in the
     * {@link #ROTATIONS_FOLDER_NAME} are identified as {@link Point3dFile}s.
     */
    public final static String ROTATION_FILE_EXTENSION = "cyfr";
    /**
     * The file extension of files containing direction data. This makes sure no system-generated files in the
     * {@link #DIRECTIONS_FOLDER_NAME} are identified as {@link Point3dFile}s.
     */
    public final static String DIRECTION_FILE_EXTENSION = "cyfd";
    /**
     * The {@link File} pointer to the actual file.
     */
    private final File file;
    /**
     * The identifier of the {@link Measurement} for this file
     */
    private long measurementId;

    /**
     * Constructor which actually creates a new {@link File} in the persistence layer.
     *
     * @param context The {@link Context} required to access the underlying persistence layer.
     * @param measurementId the identifier of the {@link Measurement} for which the file is to be created
     * @param folderName The folder name defining the {@link Point3d} type of the file
     * @param fileExtension the extension of the file type
     */
    public Point3dFile(@NonNull final Context context, final long measurementId, @NonNull final String folderName,
            @NonNull final String fileExtension) {
        this.file = FileUtils.createFile(context, measurementId, folderName, fileExtension);
        this.measurementId = measurementId;
    }

    /**
     * Constructor to reference an existing {@link Point3dFile}.
     *
     * @param measurementId the identifier of the measurement for this file
     * @param file The already existing file which represents the {@link Point3dFile}
     */
    private Point3dFile(final long measurementId, @NonNull final File file) {
        this.file = file;
        this.measurementId = measurementId;
    }

    public File getFile() {
        return file;
    }

    @Override
    public void append(final List<Point3d> dataPoints) {
        final byte[] data = serialize(dataPoints);
        FileUtils.write(file, data, true);
    }

    @Override
    public byte[] serialize(final List<Point3d> dataPoints) {
        return MeasurementSerializer.serialize(dataPoints);
    }

    /**
     * This method is used in tests to load the stored data from a {@link File}.
     *
     * @param pointCount The number of points in this file. This number is stored in the associated measurement
     * @return the {@link Point3d} data restored from the {@code Point3dFile}
     * @throws FileCorruptedException when the {@link File} is corrupted
     */
    public List<Point3d> deserialize(final int pointCount) throws FileCorruptedException {
        final byte[] bytes = FileUtils.loadBytes(file);
        return MeasurementSerializer.deserializePoint3dData(bytes, pointCount);
    }

    /**
     * Loads an existing {@link Point3dFile} for a specified measurement. The {@link File} must already exist.
     * If you want to create a new {@code Point3dFile} use the Constructor.
     *
     * @param context The {@link Context} required to access the underlying persistence layer.
     * @param measurementId the identifier of the measurement for which the file is to be found
     * @param folderName The folder name defining the {@link Point3d} type of the file
     * @param fileExtension the extension of the file type
     * @return the {@link Point3dFile} link to the file
     * @throws IllegalStateException if there is no such file
     */
    public static Point3dFile loadFile(@NonNull final Context context, final long measurementId,
            @NonNull final String folderName, @NonNull final String fileExtension) {
        final File file = FileUtils.getFilePath(context, measurementId, folderName, fileExtension);
        Validate.isTrue(file.exists());
        return new Point3dFile(measurementId, file);
    }
}