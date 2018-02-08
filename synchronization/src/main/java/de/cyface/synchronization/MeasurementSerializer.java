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
 * Created by muthmann on 08.02.18.
 */

public final class MeasurementSerializer {

    private final static int LONG_BYTES = Long.SIZE / 8;
    private final static int INT_BYTES = Integer.SIZE / 8;
    private final static int DOUBLE_BYTES = Double.SIZE / 8;

    private final ContentProviderClient databaseClient;

    public MeasurementSerializer(final @NonNull ContentProviderClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    public InputStream serialize(final long measurementIdentifier) {
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

    private abstract static class Point3DSerializer {

        private ContentProviderClient databaseClient;

        public Point3DSerializer(final @NonNull ContentProviderClient databaseClient) {
            this.databaseClient = databaseClient;
        }

        protected abstract Uri getTableUri();

        protected abstract String getXColumnName();

        protected abstract String getYColumnName();

        protected abstract String getZColumnName();

        protected abstract String getMeasurementKeyColumnName();

        protected abstract String getTimestampColumnName();

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
