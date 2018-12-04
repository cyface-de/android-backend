package de.cyface.persistence;

import static de.cyface.persistence.ByteSizes.LONG_BYTES;
import static de.cyface.persistence.ByteSizes.SHORT_BYTES;
import static de.cyface.persistence.Constants.DEFAULT_CHARSET;
import static de.cyface.persistence.Constants.TAG;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import android.support.annotation.NonNull;
import android.util.Log;

import de.cyface.persistence.model.AccelerationsSerializer;
import de.cyface.persistence.model.DirectionsSerializer;
import de.cyface.persistence.model.Event;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Point3D;
import de.cyface.persistence.model.Point3DSerializer;
import de.cyface.persistence.model.RotationsSerializer;
import de.cyface.persistence.model.Vehicle;
import de.cyface.utils.Validate;

/**
 * This class implements the serialization into the Cyface binary format.
 * This format consists of a header with the following information:
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
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 2.0.0
 */
public final class MeasurementSerializer {

    public static final int BYTES_IN_ONE_POINT_ENTRY = ByteSizes.LONG_BYTES + 3 * ByteSizes.DOUBLE_BYTES;

    /**
     * The current version of the data format. This is always specified by the first two bytes and allows to process
     * different version in parallel.
     */
    final static short DATA_FORMAT_VERSION = 1;

    public final static int BYTES_IN_HEADER = 2 + 4 * 4;

    /**
     * A constant with the number of bytes for one uncompressed geo location entry in the Cyface binary format.
     */
    public final static int BYTES_IN_ONE_GEO_LOCATION_ENTRY = ByteSizes.LONG_BYTES + 3 * ByteSizes.DOUBLE_BYTES
            + ByteSizes.INT_BYTES;

    /**
     * Serializer for transforming acceleration points into a byte representation.
     */
    private final static Point3DSerializer accelerationsSerializer = new AccelerationsSerializer();

    /**
     * Serializer for transforming rotation points into a byte representation.
     */
    private final static Point3DSerializer rotationsSerializer = new RotationsSerializer();

    /**
     * Serializer for transforming direction points into a byte representation.
     */
    private final static Point3DSerializer directionsSerializer = new DirectionsSerializer();

    /**
     * Loads the measurement with the provided identifier from the <code>ContentProvider</code> accessible via the
     * client given to the constructor and serializes it, using standard Android GZIP compression on the described
     * binary
     * format to an <code>InputStream</code>.
     * 
     * @param loader The device wide unique identifier of the measurement to serialize.
     * @return An <code>InputStream</code> containing the serialized compressed data.
     *         /
     *         public InputStream serializeCompressed(final @NonNull MeasurementContentProviderClient loader) {
     *         Deflater compressor = new Deflater();
     *         byte[] data = serializeToByteArray(loader);
     *         compressor.setInput(data);
     *         compressor.finish();
     *         byte[] output = new byte[data.length];
     *         int lengthOfCompressedData = compressor.deflate(output);
     *         Log.d(TAG, String.format("Compressed data to %d bytes.", lengthOfCompressedData));
     *         return new ByteArrayInputStream(output, 0, lengthOfCompressedData);
     *         }
     */

    /**
     * Creates the later part of the header field for a serialized measurement in big endian format.
     * Attention! Changes to this format must be discussed with compatible API providers.
     *
     * @param metaData Number of data points in the serialized measurement.
     * @return The header byte array.
     */
    static byte[] serialize(final MetaFile.PointMetaData metaData) {
        byte[] ret = new byte[16];
        ret[0] = (byte)(metaData.countOfGeoLocations >> 24);
        ret[1] = (byte)(metaData.countOfGeoLocations >> 16);
        ret[2] = (byte)(metaData.countOfGeoLocations >> 8);
        ret[3] = (byte)metaData.countOfGeoLocations;
        ret[4] = (byte)(metaData.countOfAccelerations >> 24);
        ret[5] = (byte)(metaData.countOfAccelerations >> 16);
        ret[6] = (byte)(metaData.countOfAccelerations >> 8);
        ret[7] = (byte)metaData.countOfAccelerations;
        ret[8] = (byte)(metaData.countOfRotations >> 24);
        ret[9] = (byte)(metaData.countOfRotations >> 16);
        ret[10] = (byte)(metaData.countOfRotations >> 8);
        ret[11] = (byte)metaData.countOfRotations;
        ret[12] = (byte)(metaData.countOfDirections >> 24);
        ret[13] = (byte)(metaData.countOfDirections >> 16);
        ret[14] = (byte)(metaData.countOfDirections >> 8);
        ret[15] = (byte)metaData.countOfDirections;
        return ret;
    }

    /**
     * Serializes a {@link GeoLocation}.
     *
     * @param location The {@code GeoLocation} to serialize.
     * @return A <code>byte</code> array containing all the data.
     */
    static byte[] serialize(final @NonNull GeoLocation location) {
        // Allocate enough space for all geo locations
        Log.d(TAG, "Serializing 1 geo location.");
        final ByteBuffer buffer = ByteBuffer.allocate(BYTES_IN_ONE_GEO_LOCATION_ENTRY);

        buffer.putLong(location.getTimestamp());
        buffer.putDouble(location.getLat());
        buffer.putDouble(location.getLon());
        buffer.putDouble(location.getSpeed());
        buffer.putInt(Math.round(location.getAccuracy() * 100.0f));

        final byte[] payload = new byte[buffer.capacity()];
        ((ByteBuffer)buffer.duplicate().clear()).get(payload);
        // if we want to switch from write to read mode on the byte buffer we need to .flip() !!
        return payload;
    }

    /**
     * Serializes all the points from the provided
     * <code>pointCursor</code>.
     *
     * @return A <code>byte</code> array containing all the data.
     */
    static byte[] serialize(final @NonNull List<Point3D> dataPoints) {
        Log.d(TAG, String.format("Serializing %d data points!", dataPoints.size()));
        final ByteBuffer buffer = ByteBuffer.allocate(dataPoints.size() * BYTES_IN_ONE_POINT_ENTRY);
        for (Point3D point : dataPoints) {
            buffer.putLong(point.getTimestamp());
            buffer.putDouble(point.getX());
            buffer.putDouble(point.getY());
            buffer.putDouble(point.getZ());
        }
        byte[] payload = new byte[buffer.capacity()];
        ((ByteBuffer)buffer.duplicate().clear()).get(payload);
        // if we want to switch from write to read mode on the byte buffer we need to .flip() !!
        return payload;
    }

    /**
     * Serializes a {@link Vehicle} context to be stored to a {@link MetaFile}.
     * - the first 2 Bytes tell how many characters the vehicle id has
     * - for each character of the vehicle id {@code BYTES_IN_ONE_CHARACTER} Bytes follow
     *
     * @param vehicle A {@code Vehicle} context to serialize
     * @return A <code>byte</code> array containing the data
     */
    static byte[] serialize(final @NonNull Vehicle vehicle) {
        Log.d(TAG, "Serializing vehicle " + vehicle.name());
        final byte[] bytes;
        try {
            bytes = vehicle.name().getBytes(DEFAULT_CHARSET);
        } catch (final UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
        Validate.isTrue(bytes.length <= Short.MAX_VALUE);
        final ByteBuffer buffer = ByteBuffer.allocate(SHORT_BYTES + bytes.length);
        buffer.putShort((short)bytes.length);
        buffer.put(bytes);

        // FIXME: missing docu: what's this for? (from serialize(pointCursor))
        final byte[] payload = new byte[buffer.capacity()];
        ((ByteBuffer)buffer.duplicate().clear()).get(payload);
        // if we want to switch from write to read mode on the byte buffer we need to .flip() !!

        return payload;
    }

    /**
     * Serializes an {@link Event} to be stored to a {@link EventsFile}.
     * - the first 4 Bytes contain the timestamp as long
     * - the next 2 Bytes tell how many characters the event id has
     * - for each character of the event id {@code BYTES_IN_ONE_CHARACTER} Bytes follow
     *
     * @param event The {@code Event} to serialize.
     * @return A <code>byte</code> array containing all the data.
     */
    static byte[] serialize(final @NonNull Event event) {
        Log.d(TAG, "Serializing event of type: " + event.name());
        final byte[] bytes;
        try {
            bytes = event.name().getBytes(DEFAULT_CHARSET);
        } catch (final UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
        Validate.isTrue(bytes.length <= Short.MAX_VALUE);
        final ByteBuffer buffer = ByteBuffer.allocate(LONG_BYTES + SHORT_BYTES + bytes.length);

        buffer.putLong(System.currentTimeMillis());
        buffer.putShort((short)bytes.length);
        buffer.put(bytes);

        // FIXME: missing docu: what's this for? (from serialize(pointCursor))
        final byte[] payload = new byte[buffer.capacity()];
        ((ByteBuffer)buffer.duplicate().clear()).get(payload);
        // if we want to switch from write to read mode on the byte buffer we need to .flip() !!
        return payload;
    }

    static MetaFile.MetaData deserialize(final byte[] metaFileBytes) {

        final ByteBuffer buffer = ByteBuffer.wrap(metaFileBytes); // FIXME big-endian by default
        final short dataFormatVersion = buffer.order(ByteOrder.BIG_ENDIAN).getShort();
        Validate.isTrue(dataFormatVersion == 1, "DATA_FORMAT_VERSION != 1 not yet supported");

        final short vehicleIdLength = buffer.order(ByteOrder.BIG_ENDIAN).getShort();
        final byte[] vehicleIdBytes = new byte[vehicleIdLength];
        for (int i = 0; i < vehicleIdLength; i++) {
            vehicleIdBytes[i] = buffer.order(ByteOrder.BIG_ENDIAN).get();
        }
        final String vehicleId;
        try {
            vehicleId = new String(vehicleIdBytes, DEFAULT_CHARSET);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }

        Log.d(TAG, "Restored vehicle id: " + vehicleId);
        final Vehicle vehicle = Vehicle.valueOf(vehicleId);
        final MetaFile.PointMetaData pointMetaData = new MetaFile.PointMetaData(
                buffer.order(ByteOrder.BIG_ENDIAN).getInt(), buffer.order(ByteOrder.BIG_ENDIAN).getInt(),
                buffer.order(ByteOrder.BIG_ENDIAN).getInt(), buffer.order(ByteOrder.BIG_ENDIAN).getInt());
        Log.d(TAG, "Restored PointMetaData: " + pointMetaData);
        return new MetaFile.MetaData(vehicle, pointMetaData);
    }
}
