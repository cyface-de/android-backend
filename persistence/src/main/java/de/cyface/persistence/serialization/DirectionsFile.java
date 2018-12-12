package de.cyface.persistence.serialization;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import android.content.Context;

import de.cyface.persistence.FileUtils;
import de.cyface.persistence.model.Point3D;

/**
 * The file format to persist the captured direction points.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.0.0
 */
public class DirectionsFile implements FileSupport<List<Point3D>> {

    /**
     * The {@link File} pointer to the actual file.
     */
    private final File file;
    /**
     * The name of the file containing the data
     */
    public static final String FILE_NAME = "d";
    /**
     * The name of the file containing the data
     */
    public static final String FILE_EXTENSION = "cyfd";

    /**
     * @param context The {@link Context} required to access the persistence layer.
     * @param measurementId The identifier of the measurement
     */
    public DirectionsFile(final Context context, final long measurementId) {
        this.file = new FileUtils(context).createFile(measurementId, FILE_NAME, FILE_EXTENSION);
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

    public static File loadFile(final Context context, final long measurementId) {
        return new FileUtils(context).getFile(measurementId, FILE_NAME, FILE_EXTENSION);
    }

    /**
     * For testing this method helps to load the stored data from a {@link DirectionsFile}.
     *
     * @param context The {@link Context} required to access the persistence layer.
     * @param measurementId The identifier of the measurement to resume
     * @return the {@link Point3D} data restored from the {@code DirectionsFile}
     * @throws FileCorruptedException when the {@link MetaFile} is corrupted
     */
    public static List<Point3D> deserialize(final Context context, final long measurementId) throws FileCorruptedException {
        final File file = loadFile(context, measurementId);
        final byte[] bytes = FileUtils.loadBytes(file);
        final int pointCount = MetaFile.deserialize(context, measurementId).getPointMetaData().getCountOfDirections();
        return MeasurementSerializer.deserializePoint3dData(bytes, pointCount);
    }
}
