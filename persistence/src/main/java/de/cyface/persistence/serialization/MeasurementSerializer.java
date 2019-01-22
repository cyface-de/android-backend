package de.cyface.persistence.serialization;

import static de.cyface.persistence.AbstractCyfaceMeasurementTable.DATABASE_QUERY_LIMIT;
import static de.cyface.persistence.Constants.TAG;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;
import androidx.annotation.NonNull;
import de.cyface.persistence.GeoLocationsTable;
import de.cyface.persistence.MeasurementContentProviderClient;
import de.cyface.persistence.MeasurementTable;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.Point3d;
import de.cyface.persistence.model.PointMetaData;
import de.cyface.utils.DataCapturingException;
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
 * @version 2.1.0
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
     * Loads the {@link Measurement} with the provided identifier from the <code>ContentProvider</code> accessible via
     * the client given to the constructor and serializes it, using standard Android GZIP compression on the described
     * binary format to an <code>InputStream</code>.
     * 
     * @param loader The device wide unique identifier of the {@code Measurement} to serialize.
     * @return An <code>InputStream</code> containing the serialized compressed data.
     */
    InputStream serializeCompressed(final @NonNull MeasurementContentProviderClient loader) {
        final Deflater compressor = new Deflater();
        final byte[] serializedGeoLocations = serialize(loader); // FIXME: serialize the whole measurement I think
        compressor.setInput(serializedGeoLocations);
        compressor.finish();
        byte[] output = new byte[serializedGeoLocations.length];
        int lengthOfCompressedData = compressor.deflate(output);
        Log.d(TAG, String.format("Compressed data to %d bytes.", lengthOfCompressedData));
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
        Log.d(TAG, String.format("Serializing %d GeoLocations for synchronization.", geoLocationsCursor.getCount()));
        final ByteBuffer buffer = ByteBuffer.allocate(geoLocationsCursor.getCount() * BYTES_IN_ONE_GEO_LOCATION_ENTRY);

        while (geoLocationsCursor.moveToNext()) {
            buffer.putLong(
                    geoLocationsCursor.getLong(geoLocationsCursor.getColumnIndex(GeoLocationsTable.COLUMN_GPS_TIME)));
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
        Log.d(TAG, String.format("Serializing %d Point3d points!", dataPoints.size()));
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
     * @param metaData Number of {@link Point3d} points in the serialized {@link Measurement}.
     * @return The header byte array.
     */
    public byte[] serializeTransferFileHeader(final int geoLocationCount, final PointMetaData metaData) {
        Validate.isTrue(metaData.getPersistenceFileFormatVersion() == PERSISTENCE_FILE_FORMAT_VERSION, "Unsupported");
        byte[] ret = new byte[18];
        ret[0] = (byte)(TRANSFER_FILE_FORMAT_VERSION >> 8);
        ret[1] = (byte)TRANSFER_FILE_FORMAT_VERSION;
        ret[2] = (byte)(geoLocationCount >> 24);
        ret[3] = (byte)(geoLocationCount >> 16);
        ret[4] = (byte)(geoLocationCount >> 8);
        ret[5] = (byte)geoLocationCount;
        ret[6] = (byte)(metaData.getAccelerationPointCounter() >> 24);
        ret[7] = (byte)(metaData.getAccelerationPointCounter() >> 16);
        ret[8] = (byte)(metaData.getAccelerationPointCounter() >> 8);
        ret[9] = (byte)metaData.getAccelerationPointCounter();
        ret[10] = (byte)(metaData.getRotationPointCounter() >> 24);
        ret[11] = (byte)(metaData.getRotationPointCounter() >> 16);
        ret[12] = (byte)(metaData.getRotationPointCounter() >> 8);
        ret[13] = (byte)metaData.getRotationPointCounter();
        ret[14] = (byte)(metaData.getDirectionPointCounter() >> 24);
        ret[15] = (byte)(metaData.getDirectionPointCounter() >> 16);
        ret[16] = (byte)(metaData.getDirectionPointCounter() >> 8);
        ret[17] = (byte)metaData.getDirectionPointCounter();
        return ret;
    }

    /**
     * Implements the core algorithm of loading {@link GeoLocation}s and {@link Point3d} data and serializing them into
     * an array of bytes.
     *
     * @param loader The loader providing access to the {@link ContentProvider} storing all the {@link GeoLocation}s.
     * @return A byte array containing the serialized data.
     * @throws DataCapturingException If content provider was inaccessible.
     */
    private byte[] serialize(@NonNull final MeasurementContentProviderClient loader, final long measurementIdentifier, @NonNull final MeasurementPersistence persistence) throws DataCapturingException {
        Cursor geoLocationsCursor = null;

        try {
            final Uri geoLocationTableUri = loader.createGeoLocationTableUri();
            final int geoLocationCount = loader.countData(geoLocationTableUri, GeoLocationsTable.COLUMN_MEASUREMENT_FK);

            // Generate transfer file header
            final PointMetaData pointMetaData = persistence.loadPointMetaData(measurementIdentifier);
            final byte[] header = serializeTransferFileHeader(geoLocationCount, pointMetaData);

            // Serialize GeoLocations
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            for (int startIndex = 0; startIndex < geoLocationCount; startIndex += DATABASE_QUERY_LIMIT) {
                geoLocationsCursor = loader.loadGeoLocations(startIndex, DATABASE_QUERY_LIMIT);
                outputStream.write(serializeGeoLocations(geoLocationsCursor));
            }
            final byte[] serializedGeoLocations = outputStream.toByteArray();

            // Serialize Point3ds
            // FIXME: missing Point3d data
            final byte[] serializedAccelerations = serialize(accelerationsCursor);
            final byte[] serializedRotations = serialize(rotationsCursor);
            final byte[] serializedDirections = serialize(directionsCursor);

            // Create transfer file
            final ByteBuffer buffer = ByteBuffer.allocate(header.length + serializedGeoLocations.length
                    + serializedAccelerations.length + serializedRotations.length + serializedDirections.length);
            buffer.put(header);
            buffer.put(serializedGeoLocations);
            buffer.put(serializedAccelerations);
            buffer.put(serializedRotations);
            buffer.put(serializedDirections);
            final byte[] result = buffer.array();
            // FIXME: not sure if this "uncompressed size" calculation is still correct
            Log.d(TAG, String.format("Serialized measurement with an uncompressed size of %d bytes.", result.length));

            return result;
        } catch (final RemoteException | IOException e) {
            throw new IllegalStateException(e);
        } finally {
            if (geoLocationsCursor != null) {
                geoLocationsCursor.close();
            }
        }
    }

    /**
     * Deserialized {@link Point3d} data.
     *
     * @param point3dFileBytes The bytes loaded from the {@link Point3dFile}
     * @return The {@link Point3d} loaded from the file
     */
    static List<Point3d> deserializePoint3dData(final byte[] point3dFileBytes, final int pointCount) {

        Validate.isTrue(point3dFileBytes.length == pointCount * BYTES_IN_ONE_POINT_3D_ENTRY);
        if (pointCount == 0) {
            return new ArrayList<>();
        }

        // Deserialize bytes
        final List<Point3d> points = new ArrayList<>();
        final ByteBuffer buffer = ByteBuffer.wrap(point3dFileBytes);
        for (int i = 0; i < pointCount; i++) {
            final long timestamp = buffer.order(ByteOrder.BIG_ENDIAN).getLong();
            final double x = buffer.order(ByteOrder.BIG_ENDIAN).getDouble();
            final double y = buffer.order(ByteOrder.BIG_ENDIAN).getDouble();
            final double z = buffer.order(ByteOrder.BIG_ENDIAN).getDouble();
            // final long timestamp = buffer.order(ByteOrder.BIG_ENDIAN).getLong();
            points.add(new Point3d((float)x, (float)y, (float)z, timestamp));
        }

        Log.d(TAG, "Deserialized Points: " + points.size());
        return points;
    }
}
