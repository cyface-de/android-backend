package de.cyface.synchronization;

import static de.cyface.persistence.AbstractCyfaceMeasurementTable.DATABASE_QUERY_LIMIT;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.zip.Deflater;

import android.database.Cursor;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.util.Log;

import de.cyface.persistence.GpsPointsTable;
import de.cyface.persistence.MagneticValuePointTable;
import de.cyface.persistence.RotationPointTable;
import de.cyface.persistence.SamplePointTable;

/**
 * This class implements the serialization from data stored in a <code>MeasuringPointContentProvider</code> into the
 * Cyface binary format. This format consists of a header with the following information:
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
 * @version 1.0.2
 * @since 2.0.0
 */
public final class MeasurementSerializer {

    public static final int BYTES_IN_ONE_POINT_ENTRY = ByteSizes.LONG_BYTES + 3 * ByteSizes.DOUBLE_BYTES;

    private final static String TAG = "de.cyface.sync";

    /**
     * The current version of the data format. This is always specified by the first two bytes and allows to process
     * different version in parallel.
     */
    private final static short DATA_FORMAT_VERSION = 1;

    final static int BYTES_IN_HEADER = 2 + 4 * 4;

    /**
     * A constant with the number of bytes for one uncompressed geo location entry in the Cyface binary format.
     */
    final static int BYTES_IN_ONE_GEO_LOCATION_ENTRY = ByteSizes.LONG_BYTES + 3 * ByteSizes.DOUBLE_BYTES
            + ByteSizes.INT_BYTES;

    final static int BYTES_IN_ONE_POINT_3D_ENTRY = ByteSizes.LONG_BYTES + 3 * ByteSizes.DOUBLE_BYTES;

    /**
     * Serializer for transforming acceleration points into a byte representation.
     */
    final static Point3DSerializer accelerationsSerializer = new AccelerationsSerializer();

    /**
     * Serializer for transforming rotation points into a byte representation.
     */
    final static Point3DSerializer rotationsSerializer = new RotationsSerializer();

    /**
     * Serializer for transforming direction points into a byte representation.
     */
    public final static Point3DSerializer directionsSerializer = new DirectionsSerializer();

    /**
     * Loads the measurement with the provided identifier from the <code>ContentProvider</code> accessible via the
     * client given to the constructor and serializes it in the described binary format to an <code>InputStream</code>.
     *
     * @param loader The device wide unique identifier of the measurement to serialize.
     * @return An <code>InputStream</code> containing the serialized data.
     */
    InputStream serialize(final @NonNull MeasurementContentProviderClient loader) {
        return new ByteArrayInputStream(serializeToByteArray(loader));
    }

    /**
     * Loads the measurement with the provided identifier from the <code>ContentProvider</code> accessible via the
     * client given to the constructor and serializes it, using standard Android GZIP compression on the described
     * binary
     * format to an <code>InputStream</code>.
     * 
     * @param loader The device wide unique identifier of the measurement to serialize.
     * @return An <code>InputStream</code> containing the serialized compressed data.
     */
    InputStream serializeCompressed(final @NonNull MeasurementContentProviderClient loader) {
        Deflater compressor = new Deflater();
        byte[] data = serializeToByteArray(loader);
        compressor.setInput(data);
        compressor.finish();
        byte[] output = new byte[data.length];
        int lengthOfCompressedData = compressor.deflate(output);
        Log.d(TAG, String.format("Compressed data to %d bytes.", lengthOfCompressedData));
        return new ByteArrayInputStream(output, 0, lengthOfCompressedData);
    }

    /**
     * Serializes all the geo locations from the measurement identified by the provided
     * <code>measurementIdentifier</code>.
     *
     * @param geoLocationsCursor A <code>Cursor</code> returned by a <code>ContentResolver</code> to load geo locations
     *            from.
     * @return A <code>byte</code> array containing all the data.
     */
    private byte[] serializeGeoLocations(final @NonNull Cursor geoLocationsCursor) {
        // Allocate enough space for all geo locations
        Log.d(TAG, String.format("Serializing %d geo locations for synchronization.", geoLocationsCursor.getCount()));
        ByteBuffer buffer = ByteBuffer.allocate(geoLocationsCursor.getCount() * BYTES_IN_ONE_GEO_LOCATION_ENTRY);

        while (geoLocationsCursor.moveToNext()) {
            buffer.putLong(
                    geoLocationsCursor.getLong(geoLocationsCursor.getColumnIndex(GpsPointsTable.COLUMN_GPS_TIME)));
            buffer.putDouble(
                    geoLocationsCursor.getDouble(geoLocationsCursor.getColumnIndex(GpsPointsTable.COLUMN_LAT)));
            buffer.putDouble(
                    geoLocationsCursor.getDouble(geoLocationsCursor.getColumnIndex(GpsPointsTable.COLUMN_LON)));
            buffer.putDouble(
                    geoLocationsCursor.getDouble(geoLocationsCursor.getColumnIndex(GpsPointsTable.COLUMN_SPEED)));
            buffer.putInt(geoLocationsCursor.getInt(geoLocationsCursor.getColumnIndex(GpsPointsTable.COLUMN_ACCURACY)));
        }
        byte[] payload = new byte[buffer.capacity()];
        ((ByteBuffer)buffer.duplicate().clear()).get(payload);
        // if we want to switch from write to read mode on the byte buffer we need to .flip() !!
        return payload;
    }

    /**
     * Creates the header field for a serialized measurement in big endian format.
     *
     * @param countOfGeoLocations Number of geo locations in the serialized measurement.
     * @param countOfAccelerations Number of accelerations in the serialized measurement.
     * @param countOfRotations Number of rotations in the serialized measurement.
     * @param countOfDirections Number of directions in the serialized measurement.
     * @return The header byte array.
     */
    private byte[] createHeader(final long countOfGeoLocations, final int countOfAccelerations,
            final int countOfRotations, final int countOfDirections) {
        byte[] ret = new byte[18];
        ret[0] = (byte)(DATA_FORMAT_VERSION >> 8);
        ret[1] = (byte)DATA_FORMAT_VERSION;
        // TODO: This puts a long into only 4 bytes, which will not work
        ret[2] = (byte)(countOfGeoLocations >> 24);
        ret[3] = (byte)(countOfGeoLocations >> 16);
        ret[4] = (byte)(countOfGeoLocations >> 8);
        ret[5] = (byte)countOfGeoLocations;
        ret[6] = (byte)(countOfAccelerations >> 24);
        ret[7] = (byte)(countOfAccelerations >> 16);
        ret[8] = (byte)(countOfAccelerations >> 8);
        ret[9] = (byte)countOfAccelerations;
        ret[10] = (byte)(countOfRotations >> 24);
        ret[11] = (byte)(countOfRotations >> 16);
        ret[12] = (byte)(countOfRotations >> 8);
        ret[13] = (byte)countOfRotations;
        ret[14] = (byte)(countOfDirections >> 24);
        ret[15] = (byte)(countOfDirections >> 16);
        ret[16] = (byte)(countOfDirections >> 8);
        ret[17] = (byte)countOfDirections;
        return ret;
    }

    /**
     * Implements the core algorithm of loading data from a content provider and serializing it into an array of bytes.
     *
     * @param loader The loader providing access to the content provider storing all the measurements.
     * @return A byte array containing the serialized data.
     */
    private byte[] serializeToByteArray(final @NonNull MeasurementContentProviderClient loader) {
        Cursor geoLocationsCursor = null;
        Cursor accelerationsCursor = null;
        Cursor rotationsCursor = null;
        Cursor directionsCursor = null;

        try {
            final long geoLocationCount = loader.countData(MeasuringPointsContentProvider.GPS_POINTS_URI,
                    GpsPointsTable.COLUMN_MEASUREMENT_FK);
            accelerationsCursor = loader.load3DPoint(accelerationsSerializer);
            rotationsCursor = loader.load3DPoint(rotationsSerializer);
            directionsCursor = loader.load3DPoint(directionsSerializer);

            byte[] header = createHeader(geoLocationCount, accelerationsCursor.getCount(), rotationsCursor.getCount(),
                    directionsCursor.getCount());

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            for (int startIndex = 0; startIndex < geoLocationCount; startIndex += DATABASE_QUERY_LIMIT) {
                geoLocationsCursor = loader.loadGeoLocations(startIndex, DATABASE_QUERY_LIMIT);
                outputStream.write(serializeGeoLocations(geoLocationsCursor));
            }
            byte[] serializedGeoLocations = outputStream.toByteArray();

            // TODO: Write Point3D data to a separate file because it's too much db work ...
            byte[] serializedAccelerations = serialize(accelerationsCursor, accelerationsSerializer);
            byte[] serializedRotations = serialize(rotationsCursor, rotationsSerializer);
            byte[] serializedDirections = serialize(directionsCursor, directionsSerializer);

            ByteBuffer buffer = ByteBuffer.allocate(header.length + serializedGeoLocations.length
                    + serializedAccelerations.length + serializedRotations.length + serializedDirections.length);
            buffer.put(header);
            buffer.put(serializedGeoLocations);
           buffer.put(serializedAccelerations);
            buffer.put(serializedRotations);
            buffer.put(serializedDirections);

            byte[] result = buffer.array();
            Log.d(TAG, String.format("Serialized measurement with an uncompressed size of %d bytes.", result.length));
            return result;
        } catch (RemoteException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            if (geoLocationsCursor != null) {
                geoLocationsCursor.close();
            }
            if (accelerationsCursor != null) {
                accelerationsCursor.close();
            }
            if (rotationsCursor != null) {
                rotationsCursor.close();
            }
            if (directionsCursor != null) {
                directionsCursor.close();
            }
        }
    }

    /**
     * Serializes all the points from the provided
     * <code>pointCursor</code>.
     *
     * @param pointCursor A content provider cursor providing access to the points to serialize
     * @param serializer Wrapped information about the points to serialize.
     * @return A <code>byte</code> array containing all the data.
     */
    byte[] serialize(final @NonNull Cursor pointCursor, final @NonNull Point3DSerializer serializer) {
        Log.d(TAG, String.format("Serializing %d data points!", pointCursor.getCount()));
        ByteBuffer buffer = ByteBuffer.allocate(pointCursor.getCount() * BYTES_IN_ONE_POINT_ENTRY);
        while (pointCursor.moveToNext()) {
            buffer.putLong(pointCursor.getLong(pointCursor.getColumnIndex(serializer.getTimestampColumnName())));
            buffer.putDouble(pointCursor.getDouble(pointCursor.getColumnIndex(serializer.getXColumnName())));
            buffer.putDouble(pointCursor.getDouble(pointCursor.getColumnIndex(serializer.getYColumnName())));
            buffer.putDouble(pointCursor.getDouble(pointCursor.getColumnIndex(serializer.getZColumnName())));
        }
        byte[] payload = new byte[buffer.capacity()];
        ((ByteBuffer)buffer.duplicate().clear()).get(payload);
        // if we want to switch from write to read mode on the byte buffer we need to .flip() !!
        return payload;
    }
}