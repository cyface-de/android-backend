package de.cyface.persistence;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import de.cyface.persistence.model.GeoLocation;

public class GeoLocationsFile implements FileSupport<GeoLocation> {

    private final File file;
    public final String fileName = "g";
    public final String fileExtension = "cyfg";

    public GeoLocationsFile(final long measurementId) {
        this.file = Constants.createFile(measurementId, fileName, fileExtension);
    }

    @Override
    public void append(final GeoLocation location) { // was: Serializable not DataPoint

        final byte[] data = serialize(location);

        try {
            final BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file, true));
            outputStream.write(data);
            outputStream.close();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to append data to file.");
        }
    }

    @Override
    public byte[] serialize(final GeoLocation location) {
        return MeasurementSerializer.serialize(location);
    }
}
