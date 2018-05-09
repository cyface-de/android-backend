package de.cyface.datacapturing.persistence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.DeadObjectException;
import android.os.RemoteException;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.util.Log;

import de.cyface.datacapturing.Measurement;
import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.model.CapturedData;
import de.cyface.datacapturing.model.GeoLocation;
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
 * @author Armin Schnabel
 * @version 3.1.0
 * @since 2.0.0
 */
public class MeasurementPersistence {

    /**
     * Tag used to identify messages on logcat.
     */
    private static final String TAG = "de.cyface.persistence";
    /**
     * Number of save operations to carry out in one batch. Increasing this value might increase performance but also
     * can lead to a {@link android.os.TransactionTooLargeException} on some smartphones. The current value is the one
     * where all tests are finally passing on the Pixel 2.
     */
    public static final int MAX_SIMULTANEOUS_OPERATIONS = 550;
    /**
     * <code>ContentResolver</code> that provides access to the {@link MeasuringPointsContentProvider}.
     */
    private final ContentResolver resolver;

    /**
     * Creates a new completely initialized <code>MeasurementPersistence</code>.
     *
     * @param resolver <code>ContentResolver</code> that provides access to the {@link MeasuringPointsContentProvider}.
     */
    public MeasurementPersistence(final @NonNull ContentResolver resolver) {
        this.resolver = resolver;
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

        if (resultUri == null) {
            throw new IllegalStateException("New measurement could not be created!");
        }

        return Long.valueOf(resultUri.getLastPathSegment());
    }

    /**
     * Close the currently active {@link Measurement}.
     *
     * @return The number of rows successfully updated.
     */
    public int closeRecentMeasurement() {
        // For brevity we are closing all open measurements. If we would like to make sure, that no error has occured we
        // would need to check that there is only one such open measurement before closing anything.
        ContentValues values = new ContentValues();
        values.put(MeasurementTable.COLUMN_FINISHED, 1);
        return resolver.update(MeasuringPointsContentProvider.MEASUREMENT_URI, values,
                MeasurementTable.COLUMN_FINISHED + "=?", new String[] {"0"});
    }

    /**
     * Saves the provided {@link CapturedData} to the local persistent storage of the device.
     *
     * @param data The data to store.
     */
    public void storeData(final @NonNull CapturedData data, final long measurementIdentifier)
            throws DataCapturingException {
        try {
            // final long measurementIdentifier = getIdentifierOfCurrentlyCapturedMeasurement();
            ContentProviderClient client = null;
            try {
                client = resolver.acquireContentProviderClient(MeasuringPointsContentProvider.AUTHORITY);
                if (client == null) {
                    throw new DataCapturingException(String.format("Unable to create client for content provider %s",
                            MeasuringPointsContentProvider.AUTHORITY));
                }

                ContentValues[] values = new ContentValues[data.getAccelerations().size()];
                for (int i = 0; i < data.getAccelerations().size(); i++) {
                    Point3D dataPoint = data.getAccelerations().get(i);
                    ContentValues ret = new ContentValues();
                    ret.put(SamplePointTable.COLUMN_AX, dataPoint.getX());
                    ret.put(SamplePointTable.COLUMN_AY, dataPoint.getY());
                    ret.put(SamplePointTable.COLUMN_AZ, dataPoint.getZ());
                    ret.put(SamplePointTable.COLUMN_IS_SYNCED, 0);
                    ret.put(SamplePointTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
                    ret.put(SamplePointTable.COLUMN_TIME, dataPoint.getTimestamp());
                    values[i] = ret;
                }
                for (int i = 0; i < values.length; i += MAX_SIMULTANEOUS_OPERATIONS) {
                    int startIndex = i;
                    int endIndex = Math.min(values.length, i + MAX_SIMULTANEOUS_OPERATIONS);
                    // BulkInsert is about 80 times faster than insertBatch
                    client.bulkInsert(MeasuringPointsContentProvider.SAMPLE_POINTS_URI,
                            Arrays.copyOfRange(values, startIndex, endIndex));
                }

                values = new ContentValues[data.getRotations().size()];
                for (int i = 0; i < data.getRotations().size(); i++) {
                    Point3D dataPoint = data.getRotations().get(i);
                    ContentValues ret = new ContentValues();
                    ret.put(RotationPointTable.COLUMN_RX, dataPoint.getX());
                    ret.put(RotationPointTable.COLUMN_RY, dataPoint.getY());
                    ret.put(RotationPointTable.COLUMN_RZ, dataPoint.getZ());
                    ret.put(RotationPointTable.COLUMN_IS_SYNCED, 0);
                    ret.put(RotationPointTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
                    ret.put(RotationPointTable.COLUMN_TIME, dataPoint.getTimestamp());
                    values[i] = ret;
                }
                for (int i = 0; i < values.length; i += MAX_SIMULTANEOUS_OPERATIONS) {
                    int startIndex = i;
                    int endIndex = Math.min(values.length, i + MAX_SIMULTANEOUS_OPERATIONS);
                    // BulkInsert is about 80 times faster than insertBatch
                    client.bulkInsert(MeasuringPointsContentProvider.ROTATION_POINTS_URI,
                            Arrays.copyOfRange(values, startIndex, endIndex));
                }

                values = new ContentValues[data.getDirections().size()];
                for (int i = 0; i < data.getDirections().size(); i++) {
                    Point3D dataPoint = data.getDirections().get(i);
                    ContentValues ret = new ContentValues();
                    ret.put(MagneticValuePointTable.COLUMN_MX, dataPoint.getX());
                    ret.put(MagneticValuePointTable.COLUMN_MY, dataPoint.getY());
                    ret.put(MagneticValuePointTable.COLUMN_MZ, dataPoint.getZ());
                    ret.put(MagneticValuePointTable.COLUMN_IS_SYNCED, 0);
                    ret.put(MagneticValuePointTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
                    ret.put(MagneticValuePointTable.COLUMN_TIME, dataPoint.getTimestamp());
                    values[i] = ret;
                }
                for (int i = 0; i < values.length; i += MAX_SIMULTANEOUS_OPERATIONS) {
                    int startIndex = i;
                    int endIndex = Math.min(values.length, i + MAX_SIMULTANEOUS_OPERATIONS);
                    // BulkInsert is about 80 times faster than insertBatch
                    client.bulkInsert(MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI,
                            Arrays.copyOfRange(values, startIndex, endIndex));
                }

            } finally {
                if (client != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        client.close();
                    } else {
                        client.release();
                    }
                }
            }
        } catch (DeadObjectException e) {
            Log.e(TAG, "Binder buffer full. Cannot save data.", e);
        } catch (RemoteException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Stores the provided geo location under the currently active captured measurement.
     *
     * @param location The geo location to store.
     * @param measurementIdentifier The identifier of the measurement to store the data to.
     */
    public void storeLocation(final @NonNull GeoLocation location, final long measurementIdentifier) {
        ContentValues values = new ContentValues();
        // Android gets the accuracy in meters but we save it in centimeters to reduce size during transmission
        values.put(GpsPointsTable.COLUMN_ACCURACY, Math.round(location.getAccuracy() * 100));
        values.put(GpsPointsTable.COLUMN_GPS_TIME, location.getTimestamp());
        values.put(GpsPointsTable.COLUMN_IS_SYNCED, false);
        values.put(GpsPointsTable.COLUMN_LAT, location.getLat());
        values.put(GpsPointsTable.COLUMN_LON, location.getLon());
        values.put(GpsPointsTable.COLUMN_SPEED, location.getSpeed());
        values.put(GpsPointsTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);

        resolver.insert(MeasuringPointsContentProvider.GPS_POINTS_URI, values);
    }

    /**
     * Provides information about whether there is a currently open measurement or not.
     *
     * @return <code>true</code> if a measurement is open; <code>false</code> otherwise.
     * @throws DataCapturingException If more than one measurement is open.
     */
    public boolean hasOpenMeasurement() throws DataCapturingException {
        Cursor openMeasurementQueryCursor = null;
        try {
            openMeasurementQueryCursor = resolver.query(MeasuringPointsContentProvider.MEASUREMENT_URI, null,
                    MeasurementTable.COLUMN_FINISHED + "=" + MeasuringPointsContentProvider.SQLITE_FALSE, null, null);

            if (openMeasurementQueryCursor.getCount() > 1) {
                throw new DataCapturingException("More than one measurement is open.");
            }

            return openMeasurementQueryCursor.getCount() == 1;
        } finally {
            if (openMeasurementQueryCursor != null) {
                openMeasurementQueryCursor.close();
            }
        }
    }

    /**
     * Provides the identifier of the measurement currently captured by the framework. This method should only be called
     * if capturing is active or throw an error otherwise.
     *
     * @return The system wide unique identifier of the active measurement.
     */
    public long getIdentifierOfCurrentlyCapturedMeasurement() {
        Cursor measurementIdentifierQueryCursor = null;
        try {
            measurementIdentifierQueryCursor = resolver.query(MeasuringPointsContentProvider.MEASUREMENT_URI,
                    new String[] {BaseColumns._ID, MeasurementTable.COLUMN_FINISHED},
                    MeasurementTable.COLUMN_FINISHED + "=" + MeasuringPointsContentProvider.SQLITE_FALSE, null,
                    BaseColumns._ID + " DESC");
            if (measurementIdentifierQueryCursor == null) {
                throw new IllegalStateException("Unable to query for measurement identifier!");
            }

            if (measurementIdentifierQueryCursor.getCount() > 1) {
                Log.w(TAG,
                        "More than one measurement is open. Unable to decide where to store data! Using the one with the highest identifier!");
            }

            if (!measurementIdentifierQueryCursor.moveToFirst()) {
                throw new IllegalStateException("Unable to get measurement to store captured data to!");
            }

            int indexOfMeasurementIdentifierColumn = measurementIdentifierQueryCursor.getColumnIndex(BaseColumns._ID);
            final long measurementIdentifier = measurementIdentifierQueryCursor
                    .getLong(indexOfMeasurementIdentifierColumn);
            return measurementIdentifier;
        } finally {
            if (measurementIdentifierQueryCursor != null) {
                measurementIdentifierQueryCursor.close();
            }
        }
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
     * Loads the track of <code>GeoLocation</code> objects for the provided measurement.
     *
     * @param measurement The measurement to load the track for.
     * @return The loaded track of <code>GeoLocation</code> objects ordered by time ascending.
     */
    public List<GeoLocation> loadTrack(final @NonNull Measurement measurement) {
        Cursor locationsCursor = null;
        try {
            locationsCursor = resolver.query(MeasuringPointsContentProvider.GPS_POINTS_URI, null,
                    GpsPointsTable.COLUMN_MEASUREMENT_FK + "=?",
                    new String[] {Long.valueOf(measurement.getIdentifier()).toString()},
                    GpsPointsTable.COLUMN_GPS_TIME + " ASC");

            if (locationsCursor == null) {
                return Collections.emptyList();
            }

            List<GeoLocation> ret = new ArrayList<>(locationsCursor.getCount());
            while (locationsCursor.moveToNext()) {
                double lat = locationsCursor.getDouble(locationsCursor.getColumnIndex(GpsPointsTable.COLUMN_LAT));
                double lon = locationsCursor.getDouble(locationsCursor.getColumnIndex(GpsPointsTable.COLUMN_LON));
                long timestamp = locationsCursor
                        .getLong(locationsCursor.getColumnIndex(GpsPointsTable.COLUMN_GPS_TIME));
                double speed = locationsCursor.getDouble(locationsCursor.getColumnIndex(GpsPointsTable.COLUMN_SPEED));
                float accuracy = locationsCursor
                        .getFloat(locationsCursor.getColumnIndex(GpsPointsTable.COLUMN_ACCURACY));

                ret.add(new GeoLocation(lat, lon, timestamp, speed, accuracy));
            }

            return ret;
        } finally {
            if (locationsCursor != null) {
                locationsCursor.close();
            }
        }
    }
}
