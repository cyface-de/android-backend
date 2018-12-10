package de.cyface.persistence.serialization;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import android.content.Context;

import de.cyface.persistence.FileUtils;
import de.cyface.persistence.model.GeoLocation;

/**
 * The file format to persist the captured geolocation points.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.0.0
 */
public class GeoLocationsFile implements FileSupport<GeoLocation> {

    /**
     * The {@link File} pointer to the actual file.
     */
    private final File file;
    /**
     * The name of the file containing the data
     */
    public static final String FILE_NAME = "g";
    /**
     * The name of the file containing the data
     */
    public static final String FILE_EXTENSION = "cyfg";

    /**
     * @param context The {@link Context} required to access the persistence layer.
     * @param measurementId The identifier of the measurement
     */
    public GeoLocationsFile(final Context context, final long measurementId) {
        this.file = new FileUtils(context).createFile(measurementId, FILE_NAME, FILE_EXTENSION);
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

    public static File loadFile(final Context context, final long measurementId) {
        return new FileUtils(context).getFile(measurementId, FILE_NAME, FILE_EXTENSION);
    }

    /**
     * In order to display geolocations this method helps to load the stored track from {@link GeoLocationsFile}.
     *
     * @param context The {@link Context} required to access the persistence layer.
     * @param measurementId The identifier of the measurement to resume
     * @return the {@link GeoLocation}s restored from the {@code GeoLocationFile}
     */
    public static List<GeoLocation> deserialize(final Context context, final long measurementId) {
        final File file = loadFile(context, measurementId);
        final byte[] bytes = FileUtils.loadBytes(file);
        return MeasurementSerializer.deserializeGeoLocationFile(context, bytes, measurementId);
    }
}
