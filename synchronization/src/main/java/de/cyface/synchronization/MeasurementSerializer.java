package de.cyface.synchronization;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;

import android.content.ContentProviderClient;
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
     * Since our current API Level does not support <code>Long.Bytes</code>.
     */
    private final static int LONG_BYTES = Long.SIZE / 8;
    /**
     * Since our current API Level does not support <code>Integer.Bytes</code>.
     */
    private final static int INT_BYTES = Integer.SIZE / 8;
    /**
     * Since our current API Level does not support <code>Double.Bytes</code>.
     */
    private final static int DOUBLE_BYTES = Double.SIZE / 8;

    /**
     * The client used to load the data to serialize from the <code>ContentProvider</code>.
     */
    private final ContentProviderClient databaseClient;

    /**
     * Creates a new completely initialized <code>MeasurementSerializer</code> with access to a
     * <code>ContentProvider</code>.
     *
     * @param databaseClient The client used to load the data to serialize from the <code>ContentProvider</code>.
     */
    MeasurementSerializer(final @NonNull ContentProviderClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    /**
     * Loads the measurement with the provided identifier from the <code>ContentProvider</code> accessible via the
     * client given to the constructor and serializes it in the described binary format to an <code>InputStream</code>.
     *
     * @param measurementIdentifier The device wide unqiue identifier of the measurement to serialize.
     * @return An <code>InputStream</code> containing the serialized data.
     */
    InputStream serialize(final long measurementIdentifier) {
        byte[] serializedGeoLocations = serializeGeoLocations(measurementIdentifier);

        Point3DSerializer accelerationsSerializer = new Point3DSerializer(databaseClient) {
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
        Point3DSerializer rotationsSerializer = new Point3DSerializer(databaseClient) {
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
        Point3DSerializer directionsSerializer = new Point3DSerializer(databaseClient) {
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

        byte[] serializedAccelerations = accelerationsSerializer.serialize(measurementIdentifier);
        byte[] serializedRotations = rotationsSerializer.serialize(measurementIdentifier);
        byte[] serializedDirections = directionsSerializer.serialize(measurementIdentifier);

        ByteBuffer buffer = ByteBuffer.allocate(serializedAccelerations.length + serializedAccelerations.length
                + serializedRotations.length + serializedDirections.length);
        buffer.put(serializedGeoLocations);
        buffer.put(serializedAccelerations);
        buffer.put(serializedRotations);
        buffer.put(serializedDirections);

        return new ByteArrayInputStream(buffer.array());
    }

    /**
     * Serializes all the geo locations from the measurement identified by the provided
     * <code>measurementIdentifier</code>.
     *
     * @param measurementIdentifier The device wide unqiue identifier of the measurement to serialize.
     * @return A <code>byte</code> array containing all the data.
     */
    private byte[] serializeGeoLocations(final long measurementIdentifier) {
        Cursor geoLocationsQueryCursor = null;
        try {
            geoLocationsQueryCursor = databaseClient.query(MeasuringPointsContentProvider.GPS_POINTS_URI,
                    new String[] {GpsPointsTable.COLUMN_GPS_TIME, GpsPointsTable.COLUMN_LAT, GpsPointsTable.COLUMN_LON,
                            GpsPointsTable.COLUMN_SPEED, GpsPointsTable.COLUMN_ACCURACY},
                    GpsPointsTable.COLUMN_MEASUREMENT_FK + "=?",
                    new String[] {Long.valueOf(measurementIdentifier).toString()}, null);
            if (geoLocationsQueryCursor == null) {
                throw new IllegalStateException("Unable to query local data store.");
            }

            // Allocate enough space for all geo locations
            ByteBuffer buffer = ByteBuffer
                    .allocate(geoLocationsQueryCursor.getCount() * (LONG_BYTES + 3 * DOUBLE_BYTES + INT_BYTES));

            while (geoLocationsQueryCursor.moveToNext()) {
                buffer.putLong(geoLocationsQueryCursor
                        .getLong(geoLocationsQueryCursor.getColumnIndex(GpsPointsTable.COLUMN_GPS_TIME)));
                buffer.putDouble(geoLocationsQueryCursor
                        .getDouble(geoLocationsQueryCursor.getColumnIndex(GpsPointsTable.COLUMN_LAT)));
                buffer.putDouble(geoLocationsQueryCursor
                        .getDouble(geoLocationsQueryCursor.getColumnIndex(GpsPointsTable.COLUMN_LON)));
                buffer.putDouble(geoLocationsQueryCursor
                        .getDouble(geoLocationsQueryCursor.getColumnIndex(GpsPointsTable.COLUMN_SPEED)));
                buffer.putInt(geoLocationsQueryCursor
                        .getInt(geoLocationsQueryCursor.getColumnIndex(GpsPointsTable.COLUMN_ACCURACY)));
            }
            byte[] payload = new byte[buffer.capacity()];
            ((ByteBuffer)buffer.duplicate().clear()).get(payload);
            // if we want to switch from write to read mode on the byte buffer we need to .flip() !!
            return payload;
        } catch (RemoteException e) {
            throw new IllegalStateException(e);
        } finally {
            if (geoLocationsQueryCursor != null) {
                geoLocationsQueryCursor.close();
            }
        }
    }

    /**
     * Serializes a point with 3 coordinates (i.e. acceleration, rotation, direction) into the Cyface binary format. An
     * actual implementation needs to provide mappings from database column names to the properties required for
     * serialization.
     *
     * @author Klemens Muthmann
     * @version 1.0.0
     * @since 2.0.0
     * @see SamplePointTable
     * @see RotationPointTable
     * @see MagneticValuePointTable
     */
    private abstract static class Point3DSerializer {

        /**
         * The client used to load the data to serialize from the <code>ContentProvider</code>.
         */
        private final ContentProviderClient databaseClient;

        /**
         * Creates a new completely initialized <code>Point3DSerializer</code>, with access to a
         * <code>MeasuringPointContentProvider</code> to load data from.
         *
         * @param databaseClient The client used to load the data to serialize from the <code>ContentProvider</code>.
         */
        Point3DSerializer(final @NonNull ContentProviderClient databaseClient) {
            this.databaseClient = databaseClient;
        }

        /**
         * @return The <code>ContentProvider</code> table URI, containing the data points.
         */
        protected abstract Uri getTableUri();

        /**
         * @return The database name of the column containing the point's X values.
         */
        protected abstract String getXColumnName();

        /**
         * @return The database name of the column containing the point's Y values.
         */
        protected abstract String getYColumnName();

        /**
         * @return The database name of the column containing the point's Z values.
         */
        protected abstract String getZColumnName();

        /**
         * @return The database name of the column containing the point's foreign key column referencing the measurement
         *         table.
         */
        protected abstract String getMeasurementKeyColumnName();

        /**
         * @return The database name of the column containing the point's timestamp.
         */
        protected abstract String getTimestampColumnName();

        /**
         * Serializes all the points from the measurement identified by the provided
         * <code>measurementIdentifier</code>.
         *
         * @param measurementIdentifier The device wide unqiue identifier of the measurement to serialize.
         * @return A <code>byte</code> array containing all the data.
         */
        byte[] serialize(final long measurementIdentifier) {
            Cursor queryCursor = null;
            try {
                queryCursor = databaseClient.query(getTableUri(),
                        new String[] {getTimestampColumnName(), getXColumnName(), getYColumnName(), getZColumnName()},
                        getMeasurementKeyColumnName() + "=?",
                        new String[] {Long.valueOf(measurementIdentifier).toString()}, null);
                if (queryCursor == null) {
                    throw new IllegalStateException("Unable to load accelerations from local data store!");
                }

                ByteBuffer buffer = ByteBuffer.allocate(queryCursor.getCount() * (LONG_BYTES + 3 * DOUBLE_BYTES));
                while (queryCursor.moveToNext()) {
                    buffer.putLong(queryCursor.getLong(queryCursor.getColumnIndex(getTimestampColumnName())));
                    buffer.putDouble(queryCursor.getDouble(queryCursor.getColumnIndex(getXColumnName())));
                    buffer.putDouble(queryCursor.getDouble(queryCursor.getColumnIndex(getYColumnName())));
                    buffer.putDouble(queryCursor.getDouble(queryCursor.getColumnIndex(getZColumnName())));
                }
                byte[] payload = new byte[buffer.capacity()];
                ((ByteBuffer)buffer.duplicate().clear()).get(payload);
                // if we want to switch from write to read mode on the byte buffer we need to .flip() !!
                return payload;
            } catch (RemoteException e) {
                throw new IllegalStateException(e);
            } finally {
                if (queryCursor != null) {
                    queryCursor.close();
                }
            }
        }
    }
}
