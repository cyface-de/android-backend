package de.cyface.datacapturing.persistence;

import java.util.ArrayList;
import java.util.List;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.util.Log;

import de.cyface.datacapturing.Measurement;
import de.cyface.datacapturing.model.CapturedData;
import de.cyface.datacapturing.model.Point3D;
import de.cyface.datacapturing.model.Vehicle;
import de.cyface.persistence.GpsPointsTable;
import de.cyface.persistence.MagneticValuePointTable;
import de.cyface.persistence.MeasurementTable;
import de.cyface.persistence.MeasuringPointsContentProvider;
import de.cyface.persistence.RotationPointTable;
import de.cyface.persistence.SamplePointTable;

/**
 * This class wraps the Cyface Android persistence API as required by the <code>DataCapturingListener</code> and its
 * delegate objects.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
public class MeasurementPersistence {

    private static final String TAG = "cyface.persistence";
    /**
     * <code>ContentResolver</code> that provides access to the {@link MeasuringPointsContentProvider}.
     */
    private final ContentResolver resolver;

    /**
     * Creates a new completely initialized <code>MeasurementPersistence</code>.
     *
     * @param context <code>ContentResolver</code> that provides access to the {@link MeasuringPointsContentProvider}.
     */
    public MeasurementPersistence(final @NonNull Context context) {
        resolver = context.getContentResolver();
    }

    /**
     * Creates a new {@link Measurement} for the provided {@link Vehicle}.
     *
     * @param vehicle The vehicle to create a new measurement for.
     * @return The system wide unique identifier of the newly created <code>Measurement</code>.
     */
    public long newMeasurement(final @NonNull Vehicle vehicle) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTable.COLUMN_VEHICLE, vehicle.getDatabaseIdentifier());
        values.put(MeasurementTable.COLUMN_FINISHED, false);
        Uri resultUri = resolver.insert(MeasuringPointsContentProvider.MEASUREMENT_URI, values);

        if(resultUri==null) {
            throw new IllegalStateException("New measurement could not be created!");
        }

        return Long.valueOf(resultUri.getLastPathSegment());
    }

    /**
     * Close the currently active {@link Measurement}.
     */
    public void closeRecentMeasurement() {
        // For brevity we are closing all open measurements. If we would like to make sure, that no error has occured we
        // would need to check that there is only one such open measurement before closing anything.
        ContentValues values = new ContentValues();
        values.put(MeasurementTable.COLUMN_FINISHED, true);
        resolver.update(MeasuringPointsContentProvider.MEASUREMENT_URI, values, MeasurementTable.COLUMN_FINISHED + "=?",
                new String[] {"false"});
    }

    /**
     * Saves the provided {@link CapturedData} to the local persistent storage of the device.
     *
     * @param data The data to store.
     */
    public void storeData(final @NonNull CapturedData data) {
        Cursor measurementIdentifierQueryCursor = null;
        try {
            measurementIdentifierQueryCursor = resolver.query(MeasuringPointsContentProvider.MEASUREMENT_URI,
                    new String[] {BaseColumns._ID}, MeasurementTable.COLUMN_FINISHED + "=0", null, BaseColumns._ID+" DESC");
            if(measurementIdentifierQueryCursor==null) {
                throw new IllegalStateException("Unable to query for measurement identifier!");
            }

            if (measurementIdentifierQueryCursor.getCount() > 1) {
                Log.w(TAG, "More than one measurement is open. Unable to decide where to store data! Using the one with the highest identifier!");
            }

            if (!measurementIdentifierQueryCursor.moveToFirst()) {
                throw new IllegalStateException("Unable to get measurement to store captured data to!");
            }

            final long measurementIdentifier = measurementIdentifierQueryCursor
                    .getLong(measurementIdentifierQueryCursor.getColumnIndex(MeasurementTable.COLUMN_FINISHED));

            ArrayList<ContentProviderOperation> batchOperations = new ArrayList<>();

            batchOperations.add(newGeoLocationInsertOperation(measurementIdentifier, data));
            batchOperations.addAll(newDataPointInsertOperation(data.getAccelerations(),
                    MeasuringPointsContentProvider.SAMPLE_POINTS_URI, new Mapper() {
                        @Override
                        public ContentValues map(Point3D dataPoint) {
                            ContentValues ret = new ContentValues();
                            ret.put(SamplePointTable.COLUMN_AX, dataPoint.getX());
                            ret.put(SamplePointTable.COLUMN_AY, dataPoint.getY());
                            ret.put(SamplePointTable.COLUMN_AZ, dataPoint.getZ());
                            ret.put(SamplePointTable.COLUMN_IS_SYNCED, 0);
                            ret.put(SamplePointTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
                            ret.put(SamplePointTable.COLUMN_TIME, dataPoint.getTimestamp());
                            return ret;
                        }
                    }));
            batchOperations.addAll(newDataPointInsertOperation(data.getRotations(),
                    MeasuringPointsContentProvider.ROTATION_POINTS_URI, new Mapper() {
                        @Override
                        public ContentValues map(Point3D dataPoint) {
                            ContentValues ret = new ContentValues();
                            ret.put(RotationPointTable.COLUMN_RX, dataPoint.getX());
                            ret.put(RotationPointTable.COLUMN_RY, dataPoint.getY());
                            ret.put(RotationPointTable.COLUMN_RZ, dataPoint.getZ());
                            ret.put(RotationPointTable.COLUMN_IS_SYNCED, 0);
                            ret.put(RotationPointTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
                            ret.put(RotationPointTable.COLUMN_TIME, dataPoint.getTimestamp());
                            return ret;
                        }
                    }));
            batchOperations.addAll(newDataPointInsertOperation(data.getDirections(),
                    MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI, new Mapper() {
                        @Override
                        public ContentValues map(Point3D dataPoint) {
                            ContentValues ret = new ContentValues();
                            ret.put(MagneticValuePointTable.COLUMN_MX, dataPoint.getX());
                            ret.put(MagneticValuePointTable.COLUMN_MY, dataPoint.getY());
                            ret.put(MagneticValuePointTable.COLUMN_MZ, dataPoint.getZ());
                            ret.put(MagneticValuePointTable.COLUMN_IS_SYNCED, 0);
                            ret.put(MagneticValuePointTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
                            ret.put(MagneticValuePointTable.COLUMN_TIME, dataPoint.getTimestamp());
                            return ret;
                        }
                    }));

            resolver.applyBatch(MeasuringPointsContentProvider.AUTHORITY, batchOperations);
        } catch (RemoteException e) {
            throw new IllegalStateException(e);
        } catch (OperationApplicationException e) {
            throw new IllegalStateException(e);
        } finally {
            if (measurementIdentifierQueryCursor != null) {
                measurementIdentifierQueryCursor.close();
            }
        }
    }

    /**
     * Creates a new operation to store the geo location from the provided data under the measurement identified by the
     * provided identifier.
     *
     * @param measurementIdentifier The identifier of the measurement to store the geo location under.
     * @param data The data containing the geo location to store.
     * @return A new persistence operation ready to be executed.
     */
    private ContentProviderOperation newGeoLocationInsertOperation(final long measurementIdentifier,
            final CapturedData data) {
        ContentValues values = new ContentValues();
        values.put(GpsPointsTable.COLUMN_ACCURACY, data.getGpsAccuracy());
        values.put(GpsPointsTable.COLUMN_GPS_TIME, data.getGpsTime());
        values.put(GpsPointsTable.COLUMN_IS_SYNCED, false);
        values.put(GpsPointsTable.COLUMN_LAT, data.getLat());
        values.put(GpsPointsTable.COLUMN_LON, data.getLon());
        values.put(GpsPointsTable.COLUMN_SPEED, data.getGpsSpeed());
        values.put(GpsPointsTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);

        return ContentProviderOperation.newInsert(MeasuringPointsContentProvider.GPS_POINTS_URI).withValues(values)
                .build();
    }

    /**
     * Creates a new operation to store a list of data points under the provided URI in the local persistent storage.
     *
     * @param dataPoints The data points to store.
     * @param uri The uri identifying the data points table to store them at.
     * @param mapper A mapper for mapping the data point values to the correct columns.
     * @return A <code>List</code> of operations - one per data point - ready to be executed.
     */
    private @NonNull List<ContentProviderOperation> newDataPointInsertOperation(final @NonNull List<Point3D> dataPoints,
            final @NonNull Uri uri, final @NonNull Mapper mapper) {
        List<ContentProviderOperation> ret = new ArrayList<>(dataPoints.size());

        for (Point3D dataPoint : dataPoints) {
            ContentValues values = mapper.map(dataPoint);

            ret.add(ContentProviderOperation.newInsert(uri).withValues(values).build());
        }

        return ret;
    }

    /**
     * @return All measurements currently in the local persistent data storage.
     */
    public @NonNull List<Measurement> loadMeasurements() {
        Cursor cursor = null;
        try {
            List<Measurement> ret = new ArrayList<>();

            cursor = resolver.query(MeasuringPointsContentProvider.MEASUREMENT_URI, null, null, null, null);
            if (cursor == null) {
                throw new IllegalStateException("Unable to access database to load measurements!");
            }

            while (cursor.moveToNext()) {
                long measurementIdentifier = cursor.getLong(cursor.getColumnIndex(BaseColumns._ID));

                ret.add(new Measurement(measurementIdentifier));
            }

            return ret;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Removes everything from the local persistent data storage.
     */
    public void clear() {
        resolver.delete(MeasuringPointsContentProvider.ROTATION_POINTS_URI, null, null);
        resolver.delete(MeasuringPointsContentProvider.SAMPLE_POINTS_URI, null, null);
        resolver.delete(MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI, null, null);
        resolver.delete(MeasuringPointsContentProvider.GPS_POINTS_URI, null, null);
        resolver.delete(MeasuringPointsContentProvider.MEASUREMENT_URI, null, null);
    }

    /**
     * Removes one {@link Measurement} from the local persistent data storage.
     *
     * @param measurement The measurement to remove.
     */
    public void delete(final @NonNull Measurement measurement) {
        String[] arrayWithMeasurementIdentifier = {Long.valueOf(measurement.getIdentifier()).toString()};
        resolver.delete(MeasuringPointsContentProvider.ROTATION_POINTS_URI,
                RotationPointTable.COLUMN_MEASUREMENT_FK + "=?", arrayWithMeasurementIdentifier);
        resolver.delete(MeasuringPointsContentProvider.SAMPLE_POINTS_URI, SamplePointTable.COLUMN_MEASUREMENT_FK + "=?",
                arrayWithMeasurementIdentifier);
        resolver.delete(MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI,
                MagneticValuePointTable.COLUMN_MEASUREMENT_FK + "=?", arrayWithMeasurementIdentifier);
        resolver.delete(MeasuringPointsContentProvider.GPS_POINTS_URI, GpsPointsTable.COLUMN_MEASUREMENT_FK + "=?",
                arrayWithMeasurementIdentifier);
        resolver.delete(MeasuringPointsContentProvider.MEASUREMENT_URI, BaseColumns._ID + "=?",
                arrayWithMeasurementIdentifier);
    }

    /**
     * A mapper for mapping 3D data points to <code>ContentValues</code> objects for storage in the local persistent
     * data storage. <code>ContentValues</code> objects are the expected input type of Android Content Providers.
     *
     * @author Klemens Muthmann
     * @version 1.0.0
     * @since 2.0.0
     */
    private interface Mapper {
        /**
         * Maps the provided data point to a <code>ContentValues</code> object.
         *
         * @param dataPoint The {@link Point3D} to map.
         * @return The mapping as a <code>ContentValues</code> object.
         */
        ContentValues map(final Point3D dataPoint);
    }
}
