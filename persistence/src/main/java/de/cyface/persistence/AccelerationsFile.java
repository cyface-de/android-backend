package de.cyface.persistence;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import de.cyface.persistence.model.Point3D;

public class AccelerationsFile implements FileSupport<List<Point3D>> {

    private final File file;
    public final String fileName = "a";
    public final String fileExtension = "cyfa";

    public AccelerationsFile(final long measurementId) {
        this.file = Constants.createFile(measurementId, fileName, fileExtension);
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
}
