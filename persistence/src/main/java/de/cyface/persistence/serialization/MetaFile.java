package de.cyface.persistence.serialization;

import static de.cyface.persistence.serialization.MeasurementSerializer.PERSISTENCE_FILE_FORMAT_VERSION;

import java.io.File;

import androidx.annotation.NonNull;
import de.cyface.persistence.FileUtils;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.Vehicle;
import de.cyface.utils.Validate;

/**
 * The file format to persist meta information of a measurement.
 *
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 3.0.0
 */
public class MetaFile implements FileSupport<MetaFile.PointMetaData> {

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
    private static final String FILE_NAME = "m";
    /**
     * The name of the file containing the data
     */
    private static final String FILE_EXTENSION = "cyfm";

    /**
     * @param measurement The {@link Measurement} to which this file is part of.
     * @param vehicle The {@link Vehicle} used in the measurement
     */
    public MetaFile(@NonNull final Measurement measurement, @NonNull final Vehicle vehicle) {
        Validate.isTrue(measurement.getStatus() == Measurement.MeasurementStatus.OPEN, "Unsupported");
        this.file = measurement.createFile(MetaFile.FILE_NAME, MetaFile.FILE_EXTENSION);
        this.measurement = measurement;
        // In case the PointMetaData is not persisted as the end of the capturing, appending the
        // vehicle at the beginning of the capturing allows to restore the corrupted data later on
        write(vehicle);
    }

    /**
     * Appends {@link PointMetaData} to an existing {@link MetaFile}, e.g. to pause or stop a measurement.
     *
     * @param metaData the {@code PointMetaData} to append
     */
    @Override
    public void append(final PointMetaData metaData) {
        final byte[] data = serialize(metaData);
        FileUtils.write(file, data, true);
    }

    public File getFile() {
        return file;
    }

    public Measurement getMeasurement() {
        return measurement;
    }

    /**
     * To resume a paused measurement this method loads the point counters from the {@link MetaFile}
     * and removes the counters from it so the updates counts can be stored when finishing or pausing
     * the measurement later on (again).
     *
     * @return the {@link MetaData} containing the point counters
     */
    public MetaData resume() {
        final MetaData metaData;
        try {
            metaData = deserialize();
        } catch (final FileCorruptedException e) {
            throw new IllegalStateException(e); // should not happen
        }

        // Remove counters from MetaFile by creating a new MetaFile
        Validate.isTrue(file.delete());
        new MetaFile(measurement, metaData.vehicle);
        Validate.isTrue(file.exists());
        return metaData;
    }

    /**
     * In order to resume a measurement from the {@link MetaFile} this method helps to load the stored counter states.
     *
     * @return the {@link MetaData} restored from the {@code MetaFile}
     * @throws FileCorruptedException when the DataCapturingBackgroundService did not finish a measurement by writing
     *             the <code>PointMetaData</code> to the <code>MetaFile</code>
     */
    public MetaData deserialize() throws FileCorruptedException {
        final byte[] bytes = FileUtils.loadBytes(file);
        return MeasurementSerializer.deserializeMetaFile(bytes);
    }

    @Override
    public byte[] serialize(final PointMetaData metaData) {
        return MeasurementSerializer.serialize(metaData);
    }

    /**
     * Method to create a new {@link MetaFile} and write {@code #PERSISTENCE_FILE_FORMAT_VERSION} and the
     * {@link Vehicle} id into it.
     *
     * @param vehicle the vehicle used in the {@link Measurement}
     */
    private void write(final Vehicle vehicle) {
        final byte[] dataFormatVersionBytes = new byte[2];
        dataFormatVersionBytes[0] = (byte)(PERSISTENCE_FILE_FORMAT_VERSION >> 8);
        dataFormatVersionBytes[1] = (byte)PERSISTENCE_FILE_FORMAT_VERSION;
        final byte[] vehicleIdBytes = MeasurementSerializer.serialize(vehicle);

        FileUtils.write(file, dataFormatVersionBytes, false);
        FileUtils.write(file, vehicleIdBytes, true);
    }

    /**
     * Contains the number of points stored in the files associated with a measurement.
     *
     * @author Armin Schnabel
     * @version 1.0.0
     * @since 3.0.0
     */
    public static class PointMetaData {
        private final int countOfGeoLocations;
        private final int countOfAccelerations;
        private final int countOfRotations;
        private final int countOfDirections;

        /**
         * @param countOfGeoLocations The number of geolocations stored in the {@link GeoLocationsFile}
         * @param countOfAccelerations The number of acceleration points stored in the {@link Point3dFile}
         * @param countOfRotations The number of rotation points stored in the {@link Point3dFile}
         * @param countOfDirections The number of direction points stored in the {@link Point3dFile}
         */
        public PointMetaData(final int countOfGeoLocations, final int countOfAccelerations, final int countOfRotations,
                final int countOfDirections) {
            this.countOfGeoLocations = countOfGeoLocations;
            this.countOfAccelerations = countOfAccelerations;
            this.countOfRotations = countOfRotations;
            this.countOfDirections = countOfDirections;
        }

        @Override
        @NonNull
        public String toString() {
            return "PointMetaData{" + "countOfGeoLocations=" + countOfGeoLocations + ", countOfAccelerations="
                    + countOfAccelerations + ", countOfRotations=" + countOfRotations + ", countOfDirections="
                    + countOfDirections + '}';
        }

        public int getCountOfGeoLocations() {
            return countOfGeoLocations;
        }

        public int getCountOfAccelerations() {
            return countOfAccelerations;
        }

        public int getCountOfRotations() {
            return countOfRotations;
        }

        public int getCountOfDirections() {
            return countOfDirections;
        }
    }

    /**
     * Contains the meta data of a measurement
     *
     * @author Armin Schnabel
     * @version 1.0.0
     * @since 3.0.0
     */
    public static class MetaData {
        private final Vehicle vehicle;
        private final PointMetaData pointMetaData;

        /**
         * @param vehicle The {@link Vehicle} used in this measurement
         * @param pointMetaData The {@link PointMetaData} containing the number of points captured
         */
        MetaData(Vehicle vehicle, PointMetaData pointMetaData) {
            this.vehicle = vehicle;
            this.pointMetaData = pointMetaData;
        }

        public Vehicle getVehicle() {
            return vehicle;
        }

        public PointMetaData getPointMetaData() {
            return pointMetaData;
        }
    }
}
