package de.cyface.persistence.serialization;

import java.io.File;
import java.util.List;

import androidx.annotation.NonNull;
import de.cyface.persistence.FileUtils;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.utils.Validate;

/**
 * The file format to persist the captured geolocation points.
 *
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 3.0.0
 */
public class GeoLocationsFile implements FileSupport<GeoLocation> {

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
    private static final String FILE_NAME = "g";
    /**
     * The name of the file containing the data
     */
    private static final String FILE_EXTENSION = "cyfg";

    /**
     * Constructor which actually creates a new {@link GeoLocationsFile} in the persistence layer.
     *
     * @param measurement The {@link Measurement} to which this file is part of.
     */
    public GeoLocationsFile(@NonNull final Measurement measurement) {
        this.file = measurement.createFile(FILE_NAME, FILE_EXTENSION);
        this.measurement = measurement;
    }

    /**
     * Constructor to reference existing {@link GeoLocationsFile}.
     *
     * @param measurement The {@link Measurement} to which this file is part of.
     * @param file The already existing file which represents the {@link GeoLocationsFile}
     */
    private GeoLocationsFile(@NonNull final Measurement measurement, @NonNull final File file) {
        this.file = file;
        this.measurement = measurement;
    }

    @Override
    public void append(final GeoLocation location) { // was: Serializable not DataPoint
        final byte[] data = serialize(location);
        FileUtils.write(file, data, true);
    }

    public File getFile() {
        return file;
    }

    public Measurement getMeasurement() {
        return measurement;
    }

    @Override
    public byte[] serialize(final GeoLocation location) {
        return MeasurementSerializer.serialize(location);
    }

    /**
     * In order to display geolocations this method helps to load the stored track from {@link GeoLocationsFile}.
     *
     * @return the {@link GeoLocation}s restored from the {@code GeoLocationFile}
     */
    public List<GeoLocation> deserialize() {
        final byte[] bytes = FileUtils.loadBytes(file);
        return MeasurementSerializer.deserializeGeoLocationFile(bytes, measurement);
    }

    /**
     * Loads an existing {@link GeoLocationsFile} for a specified {@link Measurement}.
     *
     * @return the {@link GeoLocationsFile} link to the file
     * @throws IllegalStateException if there is no such file
     */
    public static GeoLocationsFile loadFile(@NonNull final Measurement measurement) {
        final File file = FileUtils.generateMeasurementFilePath(measurement, FILE_NAME, FILE_EXTENSION);
        Validate.isTrue(file.exists());
        return new GeoLocationsFile(measurement, file);
    }
}
