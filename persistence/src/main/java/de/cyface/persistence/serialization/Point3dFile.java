package de.cyface.persistence.serialization;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import androidx.annotation.NonNull;
import de.cyface.persistence.FileUtils;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.Point3D;
import de.cyface.utils.Validate;

/**
 * The file format to persist the captured 3d sensor data points such as accelerations, rotations and directions.
 *
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 3.0.0
 */
public class Point3dFile implements FileSupport<List<Point3D>> {

    /**
     * The {@link File} pointer to the actual file.
     */
    private final File file;
    /**
     * The {@link Measurement} to which this file is part of.
     */
    private final Measurement measurement;

    /**
     * Constructor which actually creates a new {@link File} in the persistence layer.
     *
     * @param measurement The {@link Measurement} of which this file is part of.
     */
    public Point3dFile(@NonNull final Measurement measurement, @NonNull final String fileName,
            @NonNull final String fileExtension) {
        this.file = measurement.createFile(fileName, fileExtension);
        this.measurement = measurement;
    }

    /**
     * Constructor to reference an existing {@link Point3dFile}.
     *
     * @param measurement The {@link Measurement} to which this file is part of.
     * @param file The already existing file which represents the {@link Point3dFile}
     */
    private Point3dFile(@NonNull final Measurement measurement, @NonNull final File file) {
        this.file = file;
        this.measurement = measurement;
    }

    public File getFile() {
        return file;
    }

    public Measurement getMeasurement() {
        return measurement;
    }

    @Override
    public void append(final List<Point3D> dataPoints) { // was: Serializable not DataPoint

        final byte[] data = serialize(dataPoints);

        try {
            final BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file, true));
            outputStream.write(data);
            outputStream.close();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to append data to file.");
        }
    }

    @Override
    public byte[] serialize(final List<Point3D> dataPoints) {
        return MeasurementSerializer.serialize(dataPoints);
    }

    /**
     * This method is used in tests to load the stored data from a {@link File}.
     *
     * @return the {@link Point3D} data restored from the {@code AccelerationsFile}
     * @throws FileCorruptedException when the {@link MetaFile} is corrupted
     */
    public List<Point3D> deserialize() throws FileCorruptedException {
        final byte[] bytes = FileUtils.loadBytes(file);
        final int pointCount = measurement.getMetaFile().deserialize().getPointMetaData().getCountOfAccelerations();
        return MeasurementSerializer.deserializePoint3dData(bytes, pointCount);
    }

    /**
     * Loads an existing {@link Point3dFile} for a specified {@link Measurement}. The {@link File} must already exist.
     * If you want to create a new {@code Point3dFile} use the Constructor.
     *
     * @return the {@link Point3dFile} link to the file
     * @throws IllegalStateException if there is no such file
     */
    public static Point3dFile loadFile(@NonNull final Measurement measurement, @NonNull final String fileName,
            @NonNull final String fileExtension) {
        final File file = FileUtils.generateMeasurementFilePath(measurement, fileName, fileExtension);
        Validate.isTrue(file.exists());
        return new Point3dFile(measurement, file);
    }
}
