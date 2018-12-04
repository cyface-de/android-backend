package de.cyface.persistence.serialization;

import static de.cyface.persistence.Constants.TAG;
import static de.cyface.persistence.serialization.MeasurementSerializer.DATA_FORMAT_VERSION;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import android.util.Log;

import de.cyface.persistence.Utils;
import de.cyface.persistence.model.Vehicle;

public class MetaFile implements FileSupport<MetaFile.PointMetaData> {

    private final File file;
    public static final String FILE_NAME = "m";
    public static final String FILE_EXTENSION = "cyfm";

    public MetaFile(final long measurementId, final Vehicle vehicle) {
        this.file = Utils.createFile(measurementId, FILE_NAME, FILE_EXTENSION);
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

    // FIXME: static interface to avoid refactoring while still prototyping
    public static void append(final long measurementId, final PointMetaData metaData) {
        final File file = loadFile(measurementId);

        final byte[] data = MeasurementSerializer.serialize(metaData);

        append(data, file);
    }

    private static File loadFile(final long measurementId) {
        final File fileFinished = new File(
                Utils.getFolderName(false, measurementId) + File.separator + FILE_NAME + "." + FILE_EXTENSION);
        final File fileOpen = new File(
                Utils.getFolderName(true, measurementId) + File.separator + FILE_NAME + "." + FILE_EXTENSION);
        if (!fileFinished.exists() && !fileOpen.exists()) {
            throw new IllegalStateException("Cannot append PointMetaData to MetaFile because it does not yet exist");
        }
        return fileFinished.exists() ? fileFinished : fileOpen;
    }

    public static MetaData resume(final long measurementId) {
        final File file = loadFile(measurementId);
        try {
            final byte bytes[] = new byte[(int)file.length()];
            final BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(file));
            final DataInputStream inputStream = new DataInputStream(bufferedInputStream);
            inputStream.readFully(bytes);
            Log.d(TAG, "Read " + bytes.length + " bytes (from MetaFile)");
            inputStream.close();
            bufferedInputStream.close();
            final MetaData metaData = MeasurementSerializer.deserialize(bytes);

            // Remove counters from MetaFile by creating a new MetaFile
            new MetaFile(measurementId, metaData.vehicle);

            return metaData;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read MetaFile.");
        }
    }

    @Override
    public byte[] serialize(final PointMetaData metaData) {
        return MeasurementSerializer.serialize(metaData);
    }

    private void write(final Vehicle vehicle) {

        final byte[] dataFormatVersionBytes = new byte[2];
        dataFormatVersionBytes[0] = (byte)(DATA_FORMAT_VERSION >> 8);
        dataFormatVersionBytes[1] = (byte)DATA_FORMAT_VERSION;
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

    public static class PointMetaData {
        private final int countOfGeoLocations;
        private final int countOfAccelerations;
        private final int countOfRotations;
        private final int countOfDirections;

        public PointMetaData(int countOfGeoLocations, int countOfAccelerations, int countOfRotations,
                int countOfDirections) {
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

        int getCountOfGeoLocations() {
            return countOfGeoLocations;
        }

        int getCountOfAccelerations() {
            return countOfAccelerations;
        }

        int getCountOfRotations() {
            return countOfRotations;
        }

        int getCountOfDirections() {
            return countOfDirections;
        }
    }

    public static class MetaData {
        private final Vehicle vehicle;
        private final PointMetaData pointMetaData;

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
