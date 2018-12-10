package de.cyface.persistence.serialization;

import static de.cyface.persistence.Constants.DEFAULT_CHARSET;
import static de.cyface.persistence.Constants.TAG;
import static de.cyface.persistence.serialization.ByteSizes.CHARACTER_BYTES;
import static de.cyface.persistence.serialization.ByteSizes.INT_BYTES;
import static de.cyface.persistence.serialization.ByteSizes.LONG_BYTES;
import static de.cyface.persistence.serialization.ByteSizes.SHORT_BYTES;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import de.cyface.persistence.model.Event;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Point3D;
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
     * The current version of the transferred file. This is always specified by the first two bytes of the file
     * transferred and helps compatible APIs to process data from different client versions.
     */
    public final static short TRANSFER_FILE_FORMAT_VERSION = 1;
    /**
     * The current version of the file format used to persist captured data.
     * This is always specified by the first two bytes of the {@link MetaFile} and allows to have stored and
     * process different version at the same time.
     */
    public final static short PERSISTENCE_FILE_FORMAT_VERSION = 1;

    public final static int BYTES_IN_HEADER = 2 + 4 * 4;

    /**
     * A constant with the number of bytes for one uncompressed geo location entry in the Cyface binary format.
     */
    public final static int BYTES_IN_ONE_GEO_LOCATION_ENTRY = ByteSizes.LONG_BYTES + 3 * ByteSizes.DOUBLE_BYTES
            + ByteSizes.INT_BYTES;

    /**
     * Creates the later part of the header field for a serialized measurement in big endian format.
     * Attention! Changes to this format must be discussed with compatible API providers.
     *
     * @param metaData Number of data points in the serialized measurement.
     * @return The header byte array.
     */
    public static byte[] serialize(final MetaFile.PointMetaData metaData) {
        byte[] ret = new byte[16];
        ret[0] = (byte)(metaData.getCountOfGeoLocations() >> 24);
        ret[1] = (byte)(metaData.getCountOfGeoLocations() >> 16);
        ret[2] = (byte)(metaData.getCountOfGeoLocations() >> 8);
        ret[3] = (byte)metaData.getCountOfGeoLocations();
        ret[4] = (byte)(metaData.getCountOfAccelerations() >> 24);
        ret[5] = (byte)(metaData.getCountOfAccelerations() >> 16);
        ret[6] = (byte)(metaData.getCountOfAccelerations() >> 8);
        ret[7] = (byte)metaData.getCountOfAccelerations();
        ret[8] = (byte)(metaData.getCountOfRotations() >> 24);
        ret[9] = (byte)(metaData.getCountOfRotations() >> 16);
        ret[10] = (byte)(metaData.getCountOfRotations() >> 8);
        ret[11] = (byte)metaData.getCountOfRotations();
        ret[12] = (byte)(metaData.getCountOfDirections() >> 24);
        ret[13] = (byte)(metaData.getCountOfDirections() >> 16);
        ret[14] = (byte)(metaData.getCountOfDirections() >> 8);
        ret[15] = (byte)metaData.getCountOfDirections();
        return ret;
    }

    /**
     * Serializes a {@link GeoLocation}.
     *
     * @param location The {@code GeoLocation} to serialize.
     * @return A <code>byte</code> array containing all the data.
     */
    public static byte[] serialize(final @NonNull GeoLocation location) {
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
    public static byte[] serialize(final @NonNull List<Point3D> dataPoints) {
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
    public static byte[] serialize(final @NonNull Vehicle vehicle) {
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
    public static byte[] serialize(final @NonNull Event event) {
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

    /**
     * Attention: the {@code PERSISTENCE_FILE_FORMAT_VERSION} only tells in which format the stored data
     * is serialized. During synchronisation this data can be reassembled to match a
     * {@code TRANSFER_FILE_FORMAT_VERSION}
     * so you need to make sure to write the TRANSFER_FILE_FORMAT_VERSION to the header of the file transferred.
     *
     * @param metaFileBytes The bytes of a {@link MetaFile}
     * @return the {@link MetaFile.MetaData} of the file
     * @throws FileCorruptedException when the DataCapturingBackgroundService did not finish a measurement by writing
     *             the <code>PointMetaData</code> to the <code>MetaFile</code>
     */
    static MetaFile.MetaData deserializeMetaFile(final byte[] metaFileBytes) throws FileCorruptedException {

        final ByteBuffer buffer = ByteBuffer.wrap(metaFileBytes);
        final short dataFormatVersion = buffer.order(ByteOrder.BIG_ENDIAN).getShort();
        Validate.isTrue(dataFormatVersion == 1, "PERSISTENCE_FILE_FORMAT_VERSION != 1 not yet supported");
        final short vehicleIdLength = buffer.order(ByteOrder.BIG_ENDIAN).getShort();
        if (metaFileBytes.length != SHORT_BYTES * 2 + vehicleIdLength * CHARACTER_BYTES + 4 * INT_BYTES) {
            if (metaFileBytes.length == SHORT_BYTES * 2 + vehicleIdLength * CHARACTER_BYTES) {
                throw new FileCorruptedException(
                        "MetaFile is missing the PointMetaData. Did the DataCapturingBackgroundService end with an Exception?");
            }
            throw new IllegalStateException("Unknown file corruption cause");
        }

        // Deserialize vehicle_id
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

    /**
     * Allows to load a stored track.
     *
     * @param context The {@link Context} required to access the persistence layer
     * @param geoLocationFileBytes The bytes loaded from the {@link GeoLocationsFile}
     * @param measurementId The id of the measurement of the file
     * @return The {@link GeoLocation} loaded from the file
     */
    static List<GeoLocation> deserializeGeoLocationFile(final Context context, final byte[] geoLocationFileBytes,
            final long measurementId) {

        final MetaFile.MetaData metaData;
        try {
            metaData = MetaFile.deserialize(context, measurementId);
        } catch (FileCorruptedException e) {
            throw new IllegalStateException(e); // Should not happen
        }
        final int geoLocationCount = metaData.getPointMetaData().getCountOfGeoLocations();
        Validate.isTrue(geoLocationFileBytes.length == geoLocationCount * BYTES_IN_ONE_GEO_LOCATION_ENTRY);
        if (geoLocationCount == 0) {
            return new ArrayList<>();
        }

        // Deserialize bytes
        final List<GeoLocation> geoLocations = new ArrayList<>();
        final ByteBuffer buffer = ByteBuffer.wrap(geoLocationFileBytes);
        for (int i = 0; i < geoLocationCount; i++) {
            final long timestamp = buffer.order(ByteOrder.BIG_ENDIAN).getLong();
            final double lat = buffer.order(ByteOrder.BIG_ENDIAN).getDouble();
            final double lon = buffer.order(ByteOrder.BIG_ENDIAN).getDouble();
            final double speed = buffer.order(ByteOrder.BIG_ENDIAN).getDouble();
            final float accuracyInMeter = buffer.order(ByteOrder.BIG_ENDIAN).getInt() / 100.0f;
            geoLocations.add(new GeoLocation(lat, lon, timestamp, speed, accuracyInMeter));
        }

        Log.d(TAG, "Deserialized GeoLocations: " + geoLocations.size());
        return geoLocations;
    }
}
