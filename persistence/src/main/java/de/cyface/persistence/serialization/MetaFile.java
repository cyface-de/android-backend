package de.cyface.persistence.serialization;

import static de.cyface.persistence.Constants.TAG;
import static de.cyface.persistence.serialization.MeasurementSerializer.PERSISTENCE_FILE_FORMAT_VERSION;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import android.content.Context;
import android.util.Log;

import de.cyface.persistence.FileUtils;
import de.cyface.persistence.model.Vehicle;

/**
 * The file format to persist meta information of a measurement.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.0.0
 */
public class MetaFile implements FileSupport<MetaFile.PointMetaData> {

    /**
     * The {@link File} pointer to the actual file.
     */
    private final File file;
    /**
     * The name of the file containing the data
     */
    public static final String FILE_NAME = "m";
    /**
     * The name of the file containing the data
     */
    public static final String FILE_EXTENSION = "cyfm";

    /**
     * @param context The {@link Context} required to access the persistence layer.
     * @param measurementId The identifier of the measurement
     * @param vehicle The {@link Vehicle} used in the measurement
     */
    public MetaFile(final Context context, final long measurementId, final Vehicle vehicle) {
        this.file = new FileUtils(context).createFile(measurementId, FILE_NAME, FILE_EXTENSION);
        // In case the PointMetaData is not persisted as the end of the capturing, appending the
        // vehicle at the beginning of the capturing allows to restore the corrupted data later on
        write(vehicle);
    }

    @Override
    public void append(final PointMetaData metaData) {
        final byte[] data = serialize(metaData);
        append(data, this.file);
    }

    private static void append(final byte[] data, final File file) {
        try {
            final BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file, true));
            Log.d(TAG, "Writing " + data.length + " bytes (4 counters)");
            outputStream.write(data);
            outputStream.close();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to append data to file.");
        }
    }

    /**
     * This needs to be static as we don't want to create a new file when we append just the {@link PointMetaData}
     * when finishing a measurement as the {@link MetaFile} is already created at the beginning to store
     * the {@link Vehicle}.
     */
    public static void append(final Context context, final long measurementId, final PointMetaData metaData) {
        final File file = loadFile(context, measurementId);

        final byte[] data = MeasurementSerializer.serialize(metaData);

        append(data, file);
    }

    private static File loadFile(final Context context, final long measurementId) {
        return new FileUtils(context).getFile(measurementId, FILE_NAME, FILE_EXTENSION);
    }

    /**
     * To resume a paused measurement this method loads the point counters from the {@link MetaFile}
     * and removes the counters from it so the updates counts can be stored when finishing or pausing
     * the measurement later on (again).
     *
     * @param context The {@link Context} required to access the persistence layer.
     * @param measurementId The identifier of the measurement to resume
     * @return the {@link MetaData} containing the point counters
     */
    public static MetaData resume(final Context context, final long measurementId) {
        final MetaData metaData = deserialize(context, measurementId);

        // Remove counters from MetaFile by creating a new MetaFile
        new MetaFile(context, measurementId, metaData.vehicle);
        return metaData;
    }

    /**
     * In order to resume a measurement from the {@link MetaFile} this method helps to load the stored counter states.
     *
     * @param context The {@link Context} required to access the persistence layer.
     * @param measurementId The identifier of the measurement to resume
     * @return the {@link MetaData} restored from the {@code MetaFile}
     */
    public static MetaData deserialize(final Context context, final long measurementId) {
        final File file = loadFile(context, measurementId);
        final byte[] bytes = FileUtils.loadBytes(file);
        return MeasurementSerializer.deserializeMetaFile(bytes);
    }

    @Override
    public byte[] serialize(final PointMetaData metaData) {
        return MeasurementSerializer.serialize(metaData);
    }

    private void write(final Vehicle vehicle) {
        final byte[] dataFormatVersionBytes = new byte[2];
        dataFormatVersionBytes[0] = (byte)(PERSISTENCE_FILE_FORMAT_VERSION >> 8);
        dataFormatVersionBytes[1] = (byte)PERSISTENCE_FILE_FORMAT_VERSION;
        final byte[] vehicleIdBytes = MeasurementSerializer.serialize(vehicle);

        try {
            final BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file, false));
            Log.d(TAG, "Writing " + dataFormatVersionBytes.length + " bytes (File Format)");
            outputStream.write(dataFormatVersionBytes);
            Log.d(TAG, "Writing " + vehicleIdBytes.length + " bytes (vehicle id)");
            outputStream.write(vehicleIdBytes);
            outputStream.close();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to append data to file.");
        }
    }

    /**
     * Contains the number of points stored in the files associated with a measurement.
     */
    public static class PointMetaData {
        private final int countOfGeoLocations;
        private final int countOfAccelerations;
        private final int countOfRotations;
        private final int countOfDirections;

        /**
         * @param countOfGeoLocations The number of geolocations stored in the {@link GeoLocationsFile}
         * @param countOfAccelerations The number of points stored in the {@link AccelerationsFile}
         * @param countOfRotations The number of points stored in the {@link RotationsFile}
         * @param countOfDirections The number of points stored in the {@link DirectionsFile}
         */
        public PointMetaData(final int countOfGeoLocations, final int countOfAccelerations, final int countOfRotations,
                final int countOfDirections) {
            this.countOfGeoLocations = countOfGeoLocations;
            this.countOfAccelerations = countOfAccelerations;
            this.countOfRotations = countOfRotations;
            this.countOfDirections = countOfDirections;
        }

        @Override
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
