package de.cyface.datacapturing.persistence;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.BaseColumns;

import java.util.ArrayList;
import java.util.List;

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
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
public class MeasurementPersistence {

    private ContentResolver resolver;

    public long newMeasurement(Vehicle vehicle) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTable.COLUMN_VEHICLE, vehicle.getDatabaseIdentifier());
        values.put(MeasurementTable.COLUMN_FINISHED, false);
        Uri resultUri = resolver.insert(MeasuringPointsContentProvider.MEASUREMENT_URI, values);
        return Long.valueOf(resultUri.getLastPathSegment());
    }

    public void closeRecentMeasurement() {
        // For brevity we are closing all open measurements. If we would like to make sure, that no error has occured we
        // would need to check that there is only one such open measurement before closing anything.
        ContentValues values = new ContentValues();
        values.put(MeasurementTable.COLUMN_FINISHED, true);
        resolver.update(MeasuringPointsContentProvider.MEASUREMENT_URI, values, MeasurementTable.COLUMN_FINISHED + "=?",
                new String[] {"false"});
    }

    public void storeData(final CapturedData data) {
        Cursor measurementIdentifierQueryCursor = null;
        try {
            measurementIdentifierQueryCursor = resolver.query(MeasuringPointsContentProvider.MEASUREMENT_URI, new String[]{BaseColumns._ID}, MeasurementTable.COLUMN_FINISHED + "=0", null, null);
            if (measurementIdentifierQueryCursor.getCount() > 1) {
                throw new IllegalStateException("More than one measurement is open. Unable to decide where to store data.");
            }

            if (!measurementIdentifierQueryCursor.moveToFirst()) {
                throw new IllegalStateException("Unable to get measurement to store captured data to!");
            }

            final long measurementIdentifier = measurementIdentifierQueryCursor.getLong(measurementIdentifierQueryCursor.getColumnIndex(MeasurementTable.COLUMN_FINISHED));

            ArrayList<ContentProviderOperation> batchOperations = new ArrayList<>();

            batchOperations.add(newGeoLocationInsertOperation(measurementIdentifier,data));
            batchOperations.addAll(newDataPointInsertOperation(measurementIdentifier, data.getAccelerations(), MeasuringPointsContentProvider.SAMPLE_POINTS_URI, new Mapper() {
                @Override
                public ContentValues map(Point3D dataPoint) {
                    ContentValues ret = new ContentValues();
                    ret.put(SamplePointTable.COLUMN_AX,dataPoint.getX());
                    ret.put(SamplePointTable.COLUMN_AY,dataPoint.getY());
                    ret.put(SamplePointTable.COLUMN_AZ,dataPoint.getZ());
                    ret.put(SamplePointTable.COLUMN_IS_SYNCED,0);
                    ret.put(SamplePointTable.COLUMN_MEASUREMENT_FK,measurementIdentifier);
                    ret.put(SamplePointTable.COLUMN_TIME,dataPoint.getTimestamp());
                    return ret;
                }
            }));
            batchOperations.addAll(newDataPointInsertOperation(measurementIdentifier, data.getRotations(), MeasuringPointsContentProvider.ROTATION_POINTS_URI, new Mapper() {
                @Override
                public ContentValues map(Point3D dataPoint) {
                    ContentValues ret = new ContentValues();
                    ret.put(RotationPointTable.COLUMN_RX,dataPoint.getX());
                    ret.put(RotationPointTable.COLUMN_RY,dataPoint.getY());
                    ret.put(RotationPointTable.COLUMN_RZ,dataPoint.getZ());
                    ret.put(RotationPointTable.COLUMN_IS_SYNCED,0);
                    ret.put(RotationPointTable.COLUMN_MEASUREMENT_FK,measurementIdentifier);
                    ret.put(RotationPointTable.COLUMN_TIME,dataPoint.getTimestamp());
                    return ret;
                }
            }));
            batchOperations.addAll(newDataPointInsertOperation(measurementIdentifier, data.getDirections(), MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI, new Mapper() {
                @Override
                public ContentValues map(Point3D dataPoint) {
                    ContentValues ret = new ContentValues();
                    ret.put(MagneticValuePointTable.COLUMN_MX,dataPoint.getX());
                    ret.put(MagneticValuePointTable.COLUMN_MY,dataPoint.getY());
                    ret.put(MagneticValuePointTable.COLUMN_MZ,dataPoint.getZ());
                    ret.put(MagneticValuePointTable.COLUMN_IS_SYNCED,0);
                    ret.put(MagneticValuePointTable.COLUMN_MEASUREMENT_FK,measurementIdentifier);
                    ret.put(MagneticValuePointTable.COLUMN_TIME,dataPoint.getTimestamp());
                    return ret;
                }
            }));


                    resolver.applyBatch(MeasuringPointsContentProvider.AUTHORITY, batchOperations);
        } catch (RemoteException e) {
            throw new IllegalStateException(e);
        } catch (OperationApplicationException e) {
            throw new IllegalStateException(e);
        } finally {
            if(measurementIdentifierQueryCursor!=null) {
                measurementIdentifierQueryCursor.close();
            }
        }
    }

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

    private List<ContentProviderOperation> newDataPointInsertOperation(final long measurementIdentifier, final List<Point3D> dataPoints, final Uri uri, final Mapper mapper) {
        List<ContentProviderOperation> ret = new ArrayList<>(dataPoints.size());

        for(Point3D dataPoint: dataPoints) {
            ContentValues values = mapper.map(dataPoint);

            ret.add(ContentProviderOperation.newInsert(uri).withValues(values).build());
        }

        return ret;
    }

    private interface Mapper {
        ContentValues map(final Point3D dataPoint);
    }
}
