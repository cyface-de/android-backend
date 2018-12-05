package de.cyface.persistence.serialization;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import de.cyface.persistence.Utils;
import de.cyface.persistence.model.GeoLocation;

public class GeoLocationsFile implements FileSupport<GeoLocation> {

    private final File file;
    public static final String FILE_NAME = "g";
    public static final String FILE_EXTENSION = "cyfg";

    public GeoLocationsFile(final long measurementId) {
        this.file = Utils.createFile(measurementId, FILE_NAME, FILE_EXTENSION);
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

    public static File loadFile(final long measurementId) {
        return Utils.loadFile(measurementId, FILE_NAME, FILE_EXTENSION);
    }

    public static List<GeoLocation> deserialize(final long measurementId) {
        final File file = loadFile(measurementId);
        final byte[] bytes = Utils.loadBytes(file);
        return MeasurementSerializer.deserializeGeoLocationFile(bytes, measurementId);
    }
}
