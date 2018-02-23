package de.cyface.synchronization;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;

import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.support.annotation.NonNull;

import de.cyface.persistence.GpsPointsTable;
import de.cyface.persistence.MagneticValuePointTable;
import de.cyface.persistence.MeasuringPointsContentProvider;
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
 * @version 1.0.0
 * @since 2.0.0
 */
final class MeasurementSerializer {

    /**
     * The current version of the data format. This is always specified by the first two bytes and allows to process
     * different version in parallel.
     */
    private final static short DATA_FORMAT_VERSION = 1;

    public final static int BYTES_IN_ONE_GEO_LOCATION_ENTRY = ByteSizes.LONG_BYTES + 3 * ByteSizes.DOUBLE_BYTES
            + ByteSizes.INT_BYTES;

    /**
     * Serializer for transforming acceleration points into a byte representation.
     */
    private final Point3DSerializer accelerationsSerializer = new Point3DSerializer() {
        @Override
        protected Uri getTableUri() {
            return MeasuringPointsContentProvider.SAMPLE_POINTS_URI;
        }

        @Override
        protected String getXColumnName() {
            return SamplePointTable.COLUMN_AX;
        }

        @Override
        protected String getYColumnName() {
            return SamplePointTable.COLUMN_AY;
        }

        @Override
        protected String getZColumnName() {
            return SamplePointTable.COLUMN_AZ;
        }

        @Override
        protected String getMeasurementKeyColumnName() {
            return SamplePointTable.COLUMN_MEASUREMENT_FK;
        }

        @Override
        protected String getTimestampColumnName() {
            return SamplePointTable.COLUMN_TIME;
        }
    };

    /**
     * Serializer for transforming rotation points into a byte representation.
     */
    private final Point3DSerializer rotationsSerializer = new Point3DSerializer() {
        @Override
        protected Uri getTableUri() {
            return MeasuringPointsContentProvider.ROTATION_POINTS_URI;
        }

        @Override
        protected String getXColumnName() {
            return RotationPointTable.COLUMN_RX;
        }

        @Override
        protected String getYColumnName() {
            return RotationPointTable.COLUMN_RY;
        }

        @Override
        protected String getZColumnName() {
            return RotationPointTable.COLUMN_RZ;
        }

        @Override
        protected String getMeasurementKeyColumnName() {
            return RotationPointTable.COLUMN_MEASUREMENT_FK;
        }

        @Override
        protected String getTimestampColumnName() {
            return RotationPointTable.COLUMN_TIME;
        }
    };

    /**
     * Serializer for transforming direction points into a byte representation.
     */
    private final Point3DSerializer directionsSerializer = new Point3DSerializer() {
        @Override
        protected Uri getTableUri() {
            return MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI;
        }

        @Override
        protected String getXColumnName() {
            return MagneticValuePointTable.COLUMN_MX;
        }

        @Override
        protected String getYColumnName() {
            return MagneticValuePointTable.COLUMN_MY;
        }

        @Override
        protected String getZColumnName() {
            return MagneticValuePointTable.COLUMN_MZ;
        }

        @Override
        protected String getMeasurementKeyColumnName() {
            return MagneticValuePointTable.COLUMN_MEASUREMENT_FK;
        }

        @Override
        protected String getTimestampColumnName() {
            return MagneticValuePointTable.COLUMN_TIME;
        }
    };

    /**
     * Loads the measurement with the provided identifier from the <code>ContentProvider</code> accessible via the
     * client given to the constructor and serializes it in the described binary format to an <code>InputStream</code>.
     *
     * @param loader The device wide unqiue identifier of the measurement to serialize.
     * @return An <code>InputStream</code> containing the serialized data.
     */
    InputStream serialize(final MeasurementLoader loader) {
        Cursor geoLocationsCursor = null;
        Cursor accelerationsCursor = null;
        Cursor rotationsCursor = null;
        Cursor directionsCursor = null;

        try {
            geoLocationsCursor = loader.loadGeoLocations();
            accelerationsCursor = loader.load3DPoint(accelerationsSerializer);
            rotationsCursor = loader.load3DPoint(rotationsSerializer);
            directionsCursor = loader.load3DPoint(directionsSerializer);

            byte[] header = createHeader(geoLocationsCursor.getCount(), accelerationsCursor.getCount(),
                    rotationsCursor.getCount(), directionsCursor.getCount());
            byte[] serializedGeoLocations = serializeGeoLocations(geoLocationsCursor);
            byte[] serializedAccelerations = accelerationsSerializer.serialize(accelerationsCursor);
            byte[] serializedRotations = rotationsSerializer.serialize(rotationsCursor);
            byte[] serializedDirections = directionsSerializer.serialize(directionsCursor);

            ByteBuffer buffer = ByteBuffer.allocate(header.length + serializedGeoLocations.length
                    + serializedAccelerations.length + serializedRotations.length + serializedDirections.length);
            buffer.put(header);
            buffer.put(serializedGeoLocations);
            buffer.put(serializedAccelerations);
            buffer.put(serializedRotations);
            buffer.put(serializedDirections);

            return new ByteArrayInputStream(buffer.array());
        } catch (RemoteException e) {
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
     * Serializes all the geo locations from the measurement identified by the provided
     * <code>measurementIdentifier</code>.
     *
     * @param geoLocationsCursor A <code>Cursor</code> returned by a <code>ContentResolver</code> to load geo locations
     *            from.
     * @return A <code>byte</code> array containing all the data.
     */
    private byte[] serializeGeoLocations(final @NonNull Cursor geoLocationsCursor) {
        // Allocate enough space for all geo locations
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
    private byte[] createHeader(final int countOfGeoLocations, final int countOfAccelerations,
            final int countOfRotations, final int countOfDirections) {
        byte[] ret = new byte[18];
        ret[0] = (byte)(DATA_FORMAT_VERSION >> 8);
        ret[1] = (byte)DATA_FORMAT_VERSION;
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

}
