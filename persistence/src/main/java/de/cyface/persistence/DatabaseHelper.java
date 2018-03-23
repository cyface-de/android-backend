/*
 * Created at 16:01:54 on 20.01.2015
 */
package de.cyface.persistence;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

/**
 * The <code>DatabaseHelper</code> class is the part of the content provider where the hard part takes place. It
 * distributes queries from the <code>MeasuringPointsContentProvider</code> to the correct tables.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 1.0.0
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    /**
     * The tag used to identify messages in Logcat.
     */
    private final static String TAG = DatabaseHelper.class.getName();
    /**
     * Name of the database used by the content provider to store data.
     */
    static final String DATABASE_NAME = "measures";
    /**
     * The path segment in the table URI identifying the geo locations table.
     */
    final static String GPS_POINT_URI_PATH = "measuring";
    /**
     * The path segment in the table URI identifying the measurements table.
     */
    final static String MEASUREMENT_URI_PATH = "measurement";
    /**
     * The path segment in the table URI identifying the accelerations table.
     */
    final static String SAMPLE_POINT_URI_PATH = "sample";
    /**
     * The path segment in the table URI identifying the rotations table.
     */
    final static String ROTATION_POINT_URI_PATH = "rotation";
    /**
     * The path segment in the table URI identifying the directions table.
     */
    final static String MAGNETIC_VALUE_POINT_URI_PATH = "magnetic_value";
    /**
     * Increase the DATABASE_VERSION if the database structure changes with a new update
     * but don't forget to adjust onCreate and onUpgrade accordingly for the new structure and incremental upgrade
     */
    private final static int DATABASE_VERSION = 8;
    /**
     * Type referring to the whole geo locations table.
     */
    private final static int GPS_POINTS = 1;
    /**
     * Type used for a single geo location.
     */
    private final static int GPS_POINT = 2;
    /**
     * Type referring to the whole measurements table.
     */
    private final static int MEASUREMENTS = 3;
    /**
     * Type used for a single measurement.
     */
    private final static int MEASUREMENT = 4;
    /**
     * Type referring to the whole accelerations table.
     */
    private final static int SAMPLE_POINTS = 5;
    /**
     * Type used for a single acceleration.
     */
    private final static int SAMPLE_POINT = 6;
    /**
     * Type referring to the whole rotations table.
     */
    private final static int ROTATION_POINTS = 7;
    /**
     * Type used for a single rotation.
     */
    private final static int ROTATION_POINT = 8;
    // private final static int GRAVITY_POINTS = 9;
    // private final static int GRAVITY_POINT = 10;
    /**
     * Type referring to the whole directions table.
     */
    private final static int MAGNETIC_VALUE_POINTS = 11;
    /**
     * Type used for a single direction.
     */
    private final static int MAGNETIC_VALUE_POINT = 12;

    /**
     * Matches a table URI to a type as in {@link #getType(Uri)}.
     */
    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        sUriMatcher.addURI(MeasuringPointsContentProvider.AUTHORITY, GPS_POINT_URI_PATH, GPS_POINTS);
        sUriMatcher.addURI(MeasuringPointsContentProvider.AUTHORITY, GPS_POINT_URI_PATH + "/#", GPS_POINT); // with "#"
                                                                                                            // it's a
                                                                                                            // single
                                                                                                            // entry !
        sUriMatcher.addURI(MeasuringPointsContentProvider.AUTHORITY, MEASUREMENT_URI_PATH, MEASUREMENTS);
        sUriMatcher.addURI(MeasuringPointsContentProvider.AUTHORITY, MEASUREMENT_URI_PATH + "/#", MEASUREMENT);
        sUriMatcher.addURI(MeasuringPointsContentProvider.AUTHORITY, SAMPLE_POINT_URI_PATH, SAMPLE_POINTS);
        sUriMatcher.addURI(MeasuringPointsContentProvider.AUTHORITY, SAMPLE_POINT_URI_PATH + "/#", SAMPLE_POINT);
        sUriMatcher.addURI(MeasuringPointsContentProvider.AUTHORITY, ROTATION_POINT_URI_PATH, ROTATION_POINTS);
        sUriMatcher.addURI(MeasuringPointsContentProvider.AUTHORITY, ROTATION_POINT_URI_PATH + "/#", ROTATION_POINT);
        sUriMatcher.addURI(MeasuringPointsContentProvider.AUTHORITY, MAGNETIC_VALUE_POINT_URI_PATH,
                MAGNETIC_VALUE_POINTS);
        sUriMatcher.addURI(MeasuringPointsContentProvider.AUTHORITY, MAGNETIC_VALUE_POINT_URI_PATH + "/#",
                MAGNETIC_VALUE_POINT);
    }

    /**
     * Mapping from a table type to a table containing that type.
     * 
     * @see #getType(Uri)
     */
    private final Map<Integer, CyfaceMeasurementTable> tables;

    /**
     * Creates a new compltely intialized <code>DatabaseHelper</code>.
     *
     * @param context The Android context to use to access the Android System via
     */
    DatabaseHelper(final @NonNull Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);

        // Current database structure
        tables = new HashMap<>(10);
        final MeasurementTable measurementTable = new MeasurementTable();
        final GpsPointsTable gpsPointsTable = new GpsPointsTable();
        final SamplePointTable samplePointTable = new SamplePointTable();
        final RotationPointTable rotationPointTable = new RotationPointTable();
        final MagneticValuePointTable magneticValuePointTable = new MagneticValuePointTable();
        tables.put(MEASUREMENT, measurementTable);
        tables.put(MEASUREMENTS, measurementTable);
        tables.put(GPS_POINT, gpsPointsTable);
        tables.put(GPS_POINTS, gpsPointsTable);
        tables.put(SAMPLE_POINT, samplePointTable);
        tables.put(SAMPLE_POINTS, samplePointTable);
        tables.put(ROTATION_POINT, rotationPointTable);
        tables.put(ROTATION_POINTS, rotationPointTable);
        tables.put(MAGNETIC_VALUE_POINT, magneticValuePointTable);
        tables.put(MAGNETIC_VALUE_POINTS, magneticValuePointTable);
    }

    /**
     * The onCreate method is called when the app is freshly installed (i.e. there is no data yet on the phone)
     * Update this (in DatabaseHelper()) if the database structure changes
     *
     * @param db the database in which the data shall be stored
     */
    @Override
    public void onCreate(final SQLiteDatabase db) {
        Set<CyfaceMeasurementTable> deduplicatedTables = new HashSet<>(tables.values());
        for (CyfaceMeasurementTable table : deduplicatedTables) {
            table.onCreate(db);
        }
    }

    /**
     * The onUpgrade method is called when the app is upgraded and the DATABASE_VERSION changed.
     * The incremental database upgrades are executed to reach the current version.
     *
     * @param db the database which shall be upgraded
     * @param oldVersion the database version the app was in before the upgrade
     * @param newVersion the database version of the new, upgraded app which shall be reached
     */
    @Override
    public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
        Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);

        // Incremental upgrades for existing tables
        // tables contains each table 2 times. We do need to call onUpgrade only once per table
        Set<CyfaceMeasurementTable> deduplicatedTables = new HashSet<>(tables.values());
        for (CyfaceMeasurementTable table : deduplicatedTables) {
            table.onUpgrade(db, oldVersion, newVersion);
        }

        // Incremental upgrades for the tables which don't exist anymore and, thus, don't have an own class file anymore
        switch (oldVersion) {
            case 3:
                Log.w(TAG, "Upgrading gravity_points from version 3 to 4: dropping table");
                db.execSQL("DELETE FROM gravity_points; DROP TABLE gravity_points; ");
                // no break, thus, the upgrade process continues with the next incremental upgrade step ->
                /*
                 * case 4:
                 * db.execSQL(SQL_QUERY_HERE_FOR_UPGRADES_FROM_4_to_5);
                 */
        }
    }

    /**
     * Deletes a row or multiple rows (depending on the format of the provided URI) from the database. If you delete a
     * measurement all corresponding data is cascadingly deleted as well.
     *
     * @param uri The URI specifying the table to delete from. If this ends with a single numeric identifier that row is
     *            deleted otherwise multiple rows might be deleted depending on the <code>selection</code> and
     *            <code>selectionArgs</code>.
     * @param selection The part of an SQL where statement behind the where. You can use '?' as a placeholder to secure
     *            yourself from SQL injections.
     * @param selectionArgs The arguments to place inside the '?' placeholder from <code>selection</code>.
     * @return The number of rows deleted.
     */
    int deleteRow(final Uri uri, final String selection, final String[] selectionArgs) {
        int uriType = sUriMatcher.match(uri);
        SQLiteDatabase database = getWritableDatabase();

        CyfaceMeasurementTable table = tables.get(uriType);
        String rowIdentifier = uri.getLastPathSegment();
        int ret = 0;
        database.beginTransaction();
        try {
            switch (uriType) {
                case MEASUREMENT:
                    // Measurement requires to also delete all dependend entries and then call table.deleteRow
                    // All other database entries just call table.deleteRow directly.

                    ret += deleteDataForMeasurement(database, Long.parseLong(rowIdentifier));
                    // continues here until return ! -->
                case GPS_POINT:
                case SAMPLE_POINT:
                case MAGNETIC_VALUE_POINT:
                case ROTATION_POINT:
                    // Add the id specified by the URI to implement expected behaviour of a content resolver, where the
                    // last element
                    // of the path is the identifier of the element requested. This is only necessary for single row
                    // deletions.
                    String adaptedSelection = BaseColumns._ID + "=" + rowIdentifier
                            + (selection == null ? "" : " AND " + selection);
                    ret += table.deleteRow(getWritableDatabase(), adaptedSelection, selectionArgs);
                    database.setTransactionSuccessful();
                    return ret;
                // Batch deletes:
                case MEASUREMENTS:
                    CyfaceMeasurementTable gpsPointsTable = tables.get(GPS_POINTS);
                    CyfaceMeasurementTable rotationPointsTable = tables.get(ROTATION_POINTS);
                    CyfaceMeasurementTable magneticValuePointsTable = tables.get(MAGNETIC_VALUE_POINTS);
                    CyfaceMeasurementTable accelerationPointsTable = tables.get(SAMPLE_POINTS);
                    /*
                     * TODO: The first part of the following "if" is only required in a very special scenario and should
                     * not be part of this call.
                     */
                    if ((BaseColumns._ID + "<=?").equals(selection)) {
                        ret += gpsPointsTable.deleteRow(database, GpsPointsTable.COLUMN_MEASUREMENT_FK + "<=?",
                                selectionArgs);
                        ret += rotationPointsTable.deleteRow(database, RotationPointTable.COLUMN_MEASUREMENT_FK + "<=?",
                                selectionArgs);
                        ret += magneticValuePointsTable.deleteRow(database,
                                MagneticValuePointTable.COLUMN_MEASUREMENT_FK + "<=?", selectionArgs);
                        ret += accelerationPointsTable.deleteRow(database,
                                SamplePointTable.COLUMN_MEASUREMENT_FK + "<=?", selectionArgs);
                    } else {
                        Cursor selectedMeasurementsCursor = null;
                        try {
                            selectedMeasurementsCursor = query(uri, new String[] {BaseColumns._ID}, selection,
                                    selectionArgs, null);
                            while (selectedMeasurementsCursor.moveToNext()) {
                                ret += deleteDataForMeasurement(database, selectedMeasurementsCursor
                                        .getLong(selectedMeasurementsCursor.getColumnIndex(BaseColumns._ID)));
                            }
                        } finally {
                            if (selectedMeasurementsCursor != null) {
                                selectedMeasurementsCursor.close();
                            }
                        }
                    }
                    database.setTransactionSuccessful();
                    // continues here until return ! -->
                case GPS_POINTS:
                case SAMPLE_POINTS:
                case MAGNETIC_VALUE_POINTS:
                case ROTATION_POINTS:
                    ret += table.deleteRow(getWritableDatabase(), selection, selectionArgs);
                    return ret;
                default:
                    Log.e(TAG, "Unable to delete data from table corresponding to URI " + uri
                            + ". There seems to be no such table.");
                    return 0;
            }
        } finally {
            database.endTransaction();
        }
    }

    /**
     * Cascadingly deletes all data for a single measurement from the database.
     *
     * @param database The database object to delete from.
     * @param measurementIdentifier The device wide unique identifier of the measurement to delete.
     * @return The number of rows deleted.
     */
    private int deleteDataForMeasurement(final @NonNull SQLiteDatabase database, final long measurementIdentifier) {
        CyfaceMeasurementTable geoLocationsTable = tables.get(GPS_POINTS);
        CyfaceMeasurementTable rotationsTable = tables.get(ROTATION_POINTS);
        CyfaceMeasurementTable directionsTable = tables.get(MAGNETIC_VALUE_POINTS);
        CyfaceMeasurementTable accelerationsTable = tables.get(SAMPLE_POINTS);
        String[] identifierAsArgs = new String[] {Long.valueOf(measurementIdentifier).toString()};
        int ret = 0;

        ret += geoLocationsTable.deleteRow(database, GpsPointsTable.COLUMN_MEASUREMENT_FK + "=?", identifierAsArgs);
        ret += rotationsTable.deleteRow(database, RotationPointTable.COLUMN_MEASUREMENT_FK + "=?", identifierAsArgs);
        ret += directionsTable.deleteRow(database, MagneticValuePointTable.COLUMN_MEASUREMENT_FK + "=?",
                identifierAsArgs);
        ret += accelerationsTable.deleteRow(database, SamplePointTable.COLUMN_MEASUREMENT_FK + "=?", identifierAsArgs);
        return ret;
    }

    /**
     * Inserts a single row into a table.
     *
     * @param uri The table to insert the row into.
     * @param values The values to insert in the new table row.
     * @return The identifier of the new row.
     */
    long insertRow(Uri uri, ContentValues values) {
        int uriType = sUriMatcher.match(uri);
        CyfaceMeasurementTable table = tables.get(uriType);
        return table.insertRow(getWritableDatabase(), values);
    }

    /**
     * Inserts a list of <code>ContentValues</code> as new rows into a table.
     *
     * @param uri The table to insert the new rows into.
     * @param values The values to insert.
     * @return An array of identifiers for the newly created table rows.
     */
    long[] bulkInsert(Uri uri, List<ContentValues> values) {
        int uriType = sUriMatcher.match(uri);
        CyfaceMeasurementTable table = tables.get(uriType);
        return table.insertBatch(getWritableDatabase(), values);
    }

    /**
     * Sends a query to the database and provides the result of that query.
     *
     * @param uri The table to send the query to.
     * @param projection The projection is the part behind the SQL select statement without the <code>SELECT</code>.
     * @param selection A selection query used to select a set of rows to update. This is the part behind the SQL where
     *            statement without the 'WHERE'. You may use '?' placeholders to avoid SQL injection attacks.
     * @param selectionArgs The arguments to insert for the placeholders used in <code>selection</code>. The amount of
     *            '?' and arguments needs to match.
     * @param sortOrder This is either <code>ASC</code> for ascending or <code>DESC</code> for descending.
     * @return A <code>Cursor</code> over the resulting rows. Do not forget to close this cursor after you finished
     *         reading from it. Preferrably use <code>finally</code> to close the cursor to avoid memory leaks.
     */
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        int uriType = sUriMatcher.match(uri);

        CyfaceMeasurementTable table = tables.get(uriType);
        switch (uriType) {
            case GPS_POINTS:
            case MEASUREMENTS:
            case SAMPLE_POINTS:
            case MAGNETIC_VALUE_POINTS:
            case ROTATION_POINTS:
                return table.query(getReadableDatabase(), projection, selection, selectionArgs, sortOrder);
            case GPS_POINT:
            case MEASUREMENT:
            case SAMPLE_POINT:
            case MAGNETIC_VALUE_POINT:
            case ROTATION_POINT:
                String adaptedSelection = BaseColumns._ID + "=" + uri.getLastPathSegment()
                        + (selection == null ? "" : " AND " + selection);
                return table.query(getReadableDatabase(), projection, adaptedSelection, selectionArgs, sortOrder);
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }

    /**
     * Updates a row or rows in the database with new values.
     *
     * @param uri The table to update rows in.
     * @param values The new values to insert.
     * @param selection A selection query used to select a set of rows to update. This is the part behind the SQL where
     *            statement without the 'WHERE'. You may use '?' placeholders to avoid SQL injection attacks.
     * @param selectionArgs The arguments to insert for the placeholders used in <code>selection</code>. The amount of
     *            '?' and arguments needs to match.
     * @return The number of updated rows.
     */
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int uriType = sUriMatcher.match(uri);
        CyfaceMeasurementTable table = tables.get(uriType);
        switch (uriType) {
            case GPS_POINTS:
            case MEASUREMENTS:
            case SAMPLE_POINTS:
            case MAGNETIC_VALUE_POINTS:
            case ROTATION_POINTS:
                return table.update(getWritableDatabase(), values, selection, selectionArgs);
            case GPS_POINT:
            case MEASUREMENT:
            case SAMPLE_POINT:
            case MAGNETIC_VALUE_POINT:
            case ROTATION_POINT:
                String id = uri.getLastPathSegment();
                String adaptedSelection = BaseColumns._ID + "=" + id
                        + (TextUtils.isEmpty(selection) ? "" : " AND " + selection);
                return table.update(getWritableDatabase(), values, adaptedSelection, selectionArgs);
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }

    /**
     * Provides the table identifying type part of the content provider URI. This is either the URI itself or the URI
     * without the last path segment if that is a row identifier.
     *
     * @param uri The URI to provide the type for.
     * @return The typed URI.
     */
    String getType(final Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case MEASUREMENTS:
            case MEASUREMENT:
                return MeasuringPointsContentProvider.AUTHORITY + "/" + MEASUREMENT_URI_PATH;
            case GPS_POINTS:
            case GPS_POINT:
                return MeasuringPointsContentProvider.AUTHORITY + "/" + GPS_POINT_URI_PATH;
            case SAMPLE_POINTS:
            case SAMPLE_POINT:
                return MeasuringPointsContentProvider.AUTHORITY + "/" + SAMPLE_POINT_URI_PATH;
            case MAGNETIC_VALUE_POINTS:
            case MAGNETIC_VALUE_POINT:
                return MeasuringPointsContentProvider.AUTHORITY + "/" + MAGNETIC_VALUE_POINT_URI_PATH;
            case ROTATION_POINTS:
            case ROTATION_POINT:
                return MeasuringPointsContentProvider.AUTHORITY + "/" + ROTATION_POINT_URI_PATH;
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
    }
}
