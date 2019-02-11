package de.cyface.persistence.serialization;

import static de.cyface.persistence.AbstractCyfaceMeasurementTable.DATABASE_QUERY_LIMIT;
import static de.cyface.persistence.Constants.TAG;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.zip.Deflater;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import de.cyface.persistence.DefaultFileAccess;
import de.cyface.persistence.FileAccessLayer;
import de.cyface.persistence.GeoLocationsTable;
import de.cyface.persistence.MeasurementContentProviderClient;
import de.cyface.persistence.MeasurementTable;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.Point3d;
import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;

/**
 * This class implements the serialization from data stored in a <code>MeasuringPointContentProvider</code> and
 * Cyface {@link #PERSISTENCE_FILE_FORMAT_VERSION} binary format into the Cyface {@link #TRANSFER_FILE_FORMAT_VERSION}
 * binary format. The later consists of a header with the following information:
 * <ul>
 * <li>2 Bytes format version</li>
 * <li>4 Bytes amount of geo locations</li>
 * <li>4 Bytes amount of accelerations</li>
 * <li>4 Bytes amount of rotations</li>
 * <li>4 Bytes amount of directions</li>
 * <li>All geo locations as: 8 Bytes long timestamp, 8 Bytes double lat, 8 Bytes double lon, 8 Bytes double speed and 4
 * Bytes integer accuracy</li>
 * <li>All accelerations as: 8 Bytes long timestamp, 8 Bytes double x acceleration, 8 Bytes double y acceleration, 8
 * Bytes double z acceleration</li>
 * <li>All rotations as: 8 Bytes long timestamp, 8 Bytes double x rotation, 8 Bytes double y rotation, 8 Bytes double z
 * rotation</li>
 * <li>All directions as: 8 Bytes long timestamp, 8 Bytes double x direction, 8 Bytes double y direction, 8 Bytes double
 * z direction</li>
 * </ul>
 * WARNING: This implementation loads all data from one measurement into memory. So be careful with large measurements.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 3.1.4
 * @since 2.0.0
 */
public final class MeasurementSerializer {

    /**
     * A constant with the number of bytes for one uncompressed {@link Point3d} entry in the Cyface binary format.
     */
    public static final int BYTES_IN_ONE_POINT_3D_ENTRY = ByteSizes.LONG_BYTES + 3 * ByteSizes.DOUBLE_BYTES;
    /**
     * The current version of the transferred file. This is always specified by the first two bytes of the file
     * transferred and helps compatible APIs to process data from different client versions.
     */
    public final static short TRANSFER_FILE_FORMAT_VERSION = 1;
    /**
     * The current version of the file format used to persist {@link Point3d} data.
     * It's stored in each {@link Measurement}'s {@link MeasurementTable} entry and allows to have stored and process
     * measurements and files with different {@code #PERSISTENCE_FILE_FORMAT_VERSION} at the same time.
     */
    public final static short PERSISTENCE_FILE_FORMAT_VERSION = 1;
    /**
     * A constant with the number of bytes for the header of the {@link #TRANSFER_FILE_FORMAT_VERSION} file.
     */
    public final static int BYTES_IN_HEADER = 2 + 4 * 4;
    /**
     * A constant with the number of bytes for one uncompressed geo location entry in the Cyface binary format.
     */
    public final static int BYTES_IN_ONE_GEO_LOCATION_ENTRY = ByteSizes.LONG_BYTES + 3 * ByteSizes.DOUBLE_BYTES
            + ByteSizes.INT_BYTES;
    /**
     * The {@link FileAccessLayer} used to interact with files.
     */
    private final FileAccessLayer fileAccessLayer;

    /**
     * @param fileAccessLayer The {@link FileAccessLayer} used to interact with files.
     */
    public MeasurementSerializer(@NonNull final FileAccessLayer fileAccessLayer) {
        this.fileAccessLayer = fileAccessLayer;
    }

    /**
     * Loads the {@link Measurement} with the provided identifier from the persistence layer as compressed and and
     * serialized in the {@link MeasurementSerializer#TRANSFER_FILE_FORMAT_VERSION} format, ready to be transferred.
     *
     * The standard Android GZIP compression is used.
     * 
     * @param loader {@link MeasurementContentProviderClient} to load the {@code Measurement} data from the database.
     * @param measurementId The id of the {@link Measurement} to load
     * @param persistenceLayer The {@link PersistenceLayer} to load the file based {@code Measurement} data from
     * @return An {@link InputStream} containing the serialized compressed data.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    public InputStream loadSerializedCompressed(@NonNull final MeasurementContentProviderClient loader,
            final long measurementId, @NonNull final PersistenceLayer persistenceLayer) throws CursorIsNullException {

        final Deflater compressor = new Deflater();
        final byte[] serializedMeasurement = loadSerialized(loader, measurementId, persistenceLayer);
        compressor.setInput(serializedMeasurement);
        compressor.finish();
        byte[] output = new byte[serializedMeasurement.length];
        int lengthOfCompressedData = compressor.deflate(output);
        Log.d(TAG, String.format("Compressed data to %s.",
                DefaultFileAccess.humanReadableByteCount(lengthOfCompressedData, true)));
        return new ByteArrayInputStream(output, 0, lengthOfCompressedData);
    }

    /**
     * Serializes all the {@link GeoLocation}s from the {@link Measurement} identified by the provided
     * {@code measurementIdentifier}.
     *
     * @param geoLocationsCursor A {@link Cursor} returned by a {@link ContentResolver} to load {@code GeoLocation}s
     *            from.
     * @return A <code>byte</code> array containing all the data.
     */
    private byte[] serializeGeoLocations(final @NonNull Cursor geoLocationsCursor) {
        // Allocate enough space for all geo locations
        Log.v(TAG, String.format("Serializing %d GeoLocations for synchronization.", geoLocationsCursor.getCount()));
        final ByteBuffer buffer = ByteBuffer.allocate(geoLocationsCursor.getCount() * BYTES_IN_ONE_GEO_LOCATION_ENTRY);

        while (geoLocationsCursor.moveToNext()) {
            buffer.putLong(geoLocationsCursor
                    .getLong(geoLocationsCursor.getColumnIndex(GeoLocationsTable.COLUMN_GEOLOCATION_TIME)));
            buffer.putDouble(
                    geoLocationsCursor.getDouble(geoLocationsCursor.getColumnIndex(GeoLocationsTable.COLUMN_LAT)));
            buffer.putDouble(
                    geoLocationsCursor.getDouble(geoLocationsCursor.getColumnIndex(GeoLocationsTable.COLUMN_LON)));
            buffer.putDouble(
                    geoLocationsCursor.getDouble(geoLocationsCursor.getColumnIndex(GeoLocationsTable.COLUMN_SPEED)));
            buffer.putInt(
                    geoLocationsCursor.getInt(geoLocationsCursor.getColumnIndex(GeoLocationsTable.COLUMN_ACCURACY)));
        }

        // TODO: missing documentation - what's this for?
        byte[] payload = new byte[buffer.capacity()];
        ((ByteBuffer)buffer.duplicate().clear()).get(payload);
        // if we want to switch from write to read mode on the byte buffer we need to .flip() !!
        return payload;
    }

    /**
     * Serializes the provided {@link Point3d} points.
     *
     * @return A <code>byte</code> array containing all the data.
     */
    public static byte[] serialize(final @NonNull List<Point3d> dataPoints) {
        Log.v(TAG, String.format("Serializing %d Point3d points!", dataPoints.size()));

        final ByteBuffer buffer = ByteBuffer.allocate(dataPoints.size() * BYTES_IN_ONE_POINT_3D_ENTRY);
        for (final Point3d point : dataPoints) {
            buffer.putLong(point.getTimestamp());
            buffer.putDouble(point.getX());
            buffer.putDouble(point.getY());
            buffer.putDouble(point.getZ());
        }

        // TODO: missing documentation - what's this for?
        byte[] payload = new byte[buffer.capacity()];
        ((ByteBuffer)buffer.duplicate().clear()).get(payload);
        // if we want to switch from write to read mode on the byte buffer we need to .flip() !!
        return payload;
    }

    /**
     * Creates the header field for a serialized {@link Measurement} in big endian format for synchronization.
     *
     * (!) Attention: Changes to this format must be discussed with compatible API providers.
     *
     * @param geoLocationCount Number of {@link GeoLocation} in the serialized {@code Measurement}.
     * @param measurement the {@link Measurement} containing the number of {@link Point3d} points in the serialized
     *            {@code Measurement}.
     * @return The header byte array.
     */
    private byte[] serializeTransferFileHeader(final int geoLocationCount, final Measurement measurement) {
        Validate.isTrue(measurement.getFileFormatVersion() == PERSISTENCE_FILE_FORMAT_VERSION, "Unsupported");
        byte[] ret = new byte[18];
        ret[0] = (byte)(TRANSFER_FILE_FORMAT_VERSION >> 8);
        ret[1] = (byte)TRANSFER_FILE_FORMAT_VERSION;
        ret[2] = (byte)(geoLocationCount >> 24);
        ret[3] = (byte)(geoLocationCount >> 16);
        ret[4] = (byte)(geoLocationCount >> 8);
        ret[5] = (byte)geoLocationCount;
        ret[6] = (byte)(measurement.getAccelerations() >> 24);
        ret[7] = (byte)(measurement.getAccelerations() >> 16);
        ret[8] = (byte)(measurement.getAccelerations() >> 8);
        ret[9] = (byte)measurement.getAccelerations();
        ret[10] = (byte)(measurement.getRotations() >> 24);
        ret[11] = (byte)(measurement.getRotations() >> 16);
        ret[12] = (byte)(measurement.getRotations() >> 8);
        ret[13] = (byte)measurement.getRotations();
        ret[14] = (byte)(measurement.getDirections() >> 24);
        ret[15] = (byte)(measurement.getDirections() >> 16);
        ret[16] = (byte)(measurement.getDirections() >> 8);
        ret[17] = (byte)measurement.getDirections();
        return ret;
    }

    /**
     * Implements the core algorithm of loading a {@link Measurement} with all its data from the
     * {@link PersistenceLayer} and serializing it into an array of bytes in the
     * {@link MeasurementSerializer#TRANSFER_FILE_FORMAT_VERSION} format, ready to be compressed.
     *<p>
     * We use the {@param loader} to access the measurement data.
     *
     * TODO: test this on weak devices for large measurements
     *
     * @param loader The loader providing access to the {@link ContentProvider} storing all the {@link GeoLocation}s.
     * @param measurementIdentifier The id of the {@code Measurement} to load
     * @param persistence The {@code PersistenceLayer} to access the file based data
     * @return A byte array containing the serialized data.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    public byte[] loadSerialized(@NonNull final MeasurementContentProviderClient loader,
            final long measurementIdentifier, @NonNull final PersistenceLayer persistence)
            throws CursorIsNullException {

        // GeoLocations
        Cursor geoLocationsCursor = null;
        final byte[] serializedGeoLocations;
        final int geoLocationCount;
        try {
            final Uri geoLocationTableUri = loader.createGeoLocationTableUri();
            geoLocationCount = loader.countData(geoLocationTableUri, GeoLocationsTable.COLUMN_MEASUREMENT_FK);

            // Serialize GeoLocations
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            for (int startIndex = 0; startIndex < geoLocationCount; startIndex += DATABASE_QUERY_LIMIT) {
                geoLocationsCursor = loader.loadGeoLocations(startIndex, DATABASE_QUERY_LIMIT);
                outputStream.write(serializeGeoLocations(geoLocationsCursor));
            }
            serializedGeoLocations = outputStream.toByteArray();

        } catch (final RemoteException | IOException e) {
            throw new IllegalStateException(e);
        } finally {
            if (geoLocationsCursor != null) {
                geoLocationsCursor.close();
            }
        }

        // Generate transfer file header
        final Measurement measurement = persistence.loadMeasurement(measurementIdentifier);
        final byte[] transferFileHeader = serializeTransferFileHeader(geoLocationCount, measurement);

        // Load already serialized Point3ds
        final File accelerationFile = fileAccessLayer.getFilePath(persistence.getContext(), measurementIdentifier,
                Point3dFile.ACCELERATIONS_FOLDER_NAME, Point3dFile.ACCELERATIONS_FILE_EXTENSION);
        final File rotationFile = fileAccessLayer.getFilePath(persistence.getContext(), measurementIdentifier,
                Point3dFile.ROTATIONS_FOLDER_NAME, Point3dFile.ROTATION_FILE_EXTENSION);
        final File directionFile = fileAccessLayer.getFilePath(persistence.getContext(), measurementIdentifier,
                Point3dFile.DIRECTIONS_FOLDER_NAME, Point3dFile.DIRECTION_FILE_EXTENSION);
        final byte[] serializedAccelerations = measurement.getAccelerations() > 0
                ? fileAccessLayer.loadBytes(accelerationFile)
                : new byte[] {};
        final byte[] serializedRotations = measurement.getRotations() > 0 ? fileAccessLayer.loadBytes(rotationFile)
                : new byte[] {};
        final byte[] serializedDirections = measurement.getDirections() > 0 ? fileAccessLayer.loadBytes(directionFile)
                : new byte[] {};

        // Create transfer file
        final byte[] transferFileBytes;
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            outputStream.write(transferFileHeader);
            outputStream.write(serializedGeoLocations);
            outputStream.write(serializedAccelerations);
            outputStream.write(serializedRotations);
            outputStream.write(serializedDirections);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
        transferFileBytes = outputStream.toByteArray();

        /*final int headerLength = transferFileHeader.length;
        final int serializedGeoLocationsLength = serializedGeoLocations.length;
        final int serializedAccelerationsLength = serializedAccelerations.length;
        final int serializedRotationsLength = serializedRotations.length;
        final int serializedDirectionsLength = serializedDirections.length;
        final ByteBuffer buffer = ByteBuffer.allocate(headerLength + serializedGeoLocationsLength
                + serializedAccelerationsLength + serializedRotationsLength + serializedDirectionsLength);
        buffer.put(transferFileHeader);
        buffer.put(serializedGeoLocations);
        buffer.put(serializedAccelerations);
        buffer.put(serializedRotations);
        buffer.put(serializedDirections);
        final byte[] result = buffer.array();*/

        Log.d(TAG, String.format("Serialized measurement with an uncompressed size of %s.",
                DefaultFileAccess.humanReadableByteCount(transferFileBytes.length, true)));
        return transferFileBytes;
    }
}
