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
 * The file format to persist the captured direction points.
 *
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 3.0.0
 */
public class DirectionsFile implements FileSupport<List<Point3D>> {

    /**
     * The {@link File} pointer to the actual file.
     */
    private final File file;
    /**
     * The {@link Measurement} to which this file is part of.
     */
    private final Measurement measurement;
    /**
     * The name of the file containing the data
     */
    private static final String FILE_NAME = "d";
    /**
     * The name of the file containing the data
     */
    private static final String FILE_EXTENSION = "cyfd";

    /**
     * Constructor which actually creates a new {@link DirectionsFile} in the persistence layer.
     *
     * @param measurement The {@link Measurement} to which this file is part of.
     */
    public DirectionsFile(@NonNull final Measurement measurement) {
        this.file = measurement.createFile(FILE_NAME, FILE_EXTENSION);
        this.measurement = measurement;
    }

    /**
     * Constructor to reference existing {@link DirectionsFile}.
     *
     * @param measurement The {@link Measurement} to which this file is part of.
     * @param file The already existing file which represents the {@link DirectionsFile}
     */
    private DirectionsFile(@NonNull final Measurement measurement, @NonNull final File file) {
        this.file = file;
        this.measurement = measurement;
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

    public File getFile() {
        return file;
    }

    public Measurement getMeasurement() {
        return measurement;
    }

    /**
     * For testing this method helps to load the stored data from a {@link DirectionsFile}.
     *
     * @return the {@link Point3D} data restored from the {@code DirectionsFile}
     * @throws FileCorruptedException when the {@link MetaFile} is corrupted
     */
    public List<Point3D> deserialize() throws FileCorruptedException {
        final byte[] bytes = FileUtils.loadBytes(file);
        final int pointCount = measurement.getMetaFile().deserialize().getPointMetaData().getCountOfDirections();
        return MeasurementSerializer.deserializePoint3dData(bytes, pointCount);
    }

    /**
     * Loads an existing {@link DirectionsFile} for a specified {@link Measurement}.
     *
     * @return the {@link DirectionsFile} link to the file
     * @throws IllegalStateException if there is no such file
     */
    public static DirectionsFile loadFile(@NonNull final Measurement measurement) {
        final File file = FileUtils.generateMeasurementFilePath(measurement, FILE_NAME, FILE_EXTENSION);
        Validate.isTrue(file.exists());
        return new DirectionsFile(measurement, file);
    }
}
