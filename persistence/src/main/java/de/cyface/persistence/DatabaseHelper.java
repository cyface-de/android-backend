/*
 * Created at 16:01:54 on 20.01.2015
 */
package de.cyface.persistence;

import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;

import java.util.*;

public class DatabaseHelper extends SQLiteOpenHelper {

    private final static String TAG = DatabaseHelper.class.getName();
    static final String DATABASE_NAME = "measures";
    final static String GPS_POINT_URI_PATH = "measuring";
    final static String MEASUREMENT_URI_PATH = "measurement";
    final static String SAMPLE_POINT_URI_PATH = "sample";
    final static String ROTATION_POINT_URI_PATH = "rotation";
    final static String MAGNETIC_VALUE_POINT_URI_PATH = "magnetic_value";
    private final static int DATABASE_VERSION = 6;
    private final static int GPS_POINTS = 1;
    private final static int GPS_POINT = 2;
    private final static int MEASUREMENTS = 3;
    private final static int MEASUREMENT = 4;
    private final static int SAMPLE_POINTS = 5;
    private final static int SAMPLE_POINT = 6;
    private final static int ROTATION_POINTS = 7;
    private final static int ROTATION_POINT = 8;
    //private final static int GRAVITY_POINTS = 9;
    //private final static int GRAVITY_POINT = 10;
    private final static int MAGNETIC_VALUE_POINTS = 11;
    private final static int MAGNETIC_VALUE_POINT = 12;

    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        sUriMatcher.addURI(MeasuringPointsContentProvider.AUTHORITY, GPS_POINT_URI_PATH, GPS_POINTS);
        sUriMatcher.addURI(MeasuringPointsContentProvider.AUTHORITY, GPS_POINT_URI_PATH + "/#", GPS_POINT); // with "#" it's a single entry !
        sUriMatcher.addURI(MeasuringPointsContentProvider.AUTHORITY, MEASUREMENT_URI_PATH, MEASUREMENTS);
        sUriMatcher.addURI(MeasuringPointsContentProvider.AUTHORITY, MEASUREMENT_URI_PATH + "/#", MEASUREMENT);
        sUriMatcher.addURI(MeasuringPointsContentProvider.AUTHORITY, SAMPLE_POINT_URI_PATH, SAMPLE_POINTS);
        sUriMatcher.addURI(MeasuringPointsContentProvider.AUTHORITY, SAMPLE_POINT_URI_PATH + "/#", SAMPLE_POINT);
        sUriMatcher.addURI(MeasuringPointsContentProvider.AUTHORITY, ROTATION_POINT_URI_PATH, ROTATION_POINTS);
        sUriMatcher.addURI(MeasuringPointsContentProvider.AUTHORITY, ROTATION_POINT_URI_PATH + "/#", ROTATION_POINT);
        sUriMatcher.addURI(MeasuringPointsContentProvider.AUTHORITY, MAGNETIC_VALUE_POINT_URI_PATH, MAGNETIC_VALUE_POINTS);
        sUriMatcher.addURI(MeasuringPointsContentProvider.AUTHORITY, MAGNETIC_VALUE_POINT_URI_PATH + "/#", MAGNETIC_VALUE_POINT);
    }

    private final Map<Integer, CyfaceMeasurementTable> tables;

    /**
     * Increase the DATABASE_VERSION if the database structure changes with a new update
     *  but don't forget to adjust onCreate and onUpgrade accordingly for the new structure and incremental upgrade
     * @param context
     */
    DatabaseHelper(final Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);

        // Current database structure
        tables = new HashMap<>();
        final MeasurementTable measurementTable = new MeasurementTable();
        final GpsPointsTable gpsPointsTable = new GpsPointsTable();
        final SamplePointTable samplePointTable = new SamplePointTable();
        final RotationPointTable rotationPointTable = new RotationPointTable();
        final MagneticValuePointTable magneticValuePointTable= new MagneticValuePointTable();
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
     * <p>
     * The onCreate method is called when the app is freshly installed (i.e. there is no data yet on the phone)
     *  Update this (in DatabaseHelper()) if the database structure changes
     *  </p>
     * @param db the database in which the data shall be stored
     */
    @Override
    public void onCreate(final SQLiteDatabase db) {
        Set<CyfaceMeasurementTable> uniqueTableList = new HashSet<>(tables.values());
        for (CyfaceMeasurementTable table : uniqueTableList) {
            table.onCreate(db);
        }
    }

    /**
     * <p>
     * The onUpgrade method is called when the app is upgraded and the DATABASE_VERSION changed.
     *  The incremental database upgrades are executed to reach the current version.
     *  </p>
     * @param db the database which shall be upgraded
     * @param oldVersion the database version the app was in before the upgrade
     * @param newVersion the database version of the new, upgraded app which shall be reached
     */
    @Override
    public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
        Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);

        // Incremental upgrades for existing tables
        Set<CyfaceMeasurementTable> uniqueTableList = new HashSet<>(tables.values());
        for (CyfaceMeasurementTable table : uniqueTableList) {
            table.onUpgrade(db, oldVersion, newVersion);
        }

        // Incremental upgrades for the tables which don't exist anymore and, thus, don't have an own class file anymore
        switch(oldVersion) {
            case 3:
                Log.w(TAG, "Upgrading gravity_points from version 3 to 4: dropping table");
                db.execSQL("DELETE FROM gravity_points; DROP gravity_points; ");
                // no break, thus, the upgrade process continues with the next incremental upgrade step ->
            /*case 4:
                db.execSQL(SQL_QUERY_HERE_FOR_UPGRADES_FROM_4_to_5);*/
        }
    }

    public int deleteRow(final Uri uri, final String selection, final String[] selectionArgs) {
        int uriType = sUriMatcher.match(uri);
        SQLiteDatabase database = getWritableDatabase();

        CyfaceMeasurementTable table = tables.get(uriType);
        String rowIdentifier = uri.getLastPathSegment();
        String adaptedSelection =
                BaseColumns._ID + "=" + rowIdentifier + (selection == null ? "" : " AND " + selection);
        switch (uriType) {
            case MEASUREMENT:
                CyfaceMeasurementTable gpsPointsTable = tables.get(GPS_POINTS);
                CyfaceMeasurementTable rotationPointsTable = tables.get(ROTATION_POINTS);
                CyfaceMeasurementTable magneticValuePointsTable = tables.get(MAGNETIC_VALUE_POINTS);
                CyfaceMeasurementTable accelerationPointsTable = tables.get(SAMPLE_POINTS);

                getWritableDatabase().beginTransaction();
                gpsPointsTable.deleteRow(database, GpsPointsTable.COLUMN_MEASUREMENT_FK + "=?", selectionArgs);
                rotationPointsTable.deleteRow(database, RotationPointTable.COLUMN_MEASUREMENT_FK + "=?", selectionArgs);
                magneticValuePointsTable.deleteRow(database, MagneticValuePointTable.COLUMN_MEASUREMENT_FK + "=?", selectionArgs);
                accelerationPointsTable.deleteRow(database, SamplePointTable.COLUMN_MEASUREMENT_FK + "=?", selectionArgs);
                getWritableDatabase().setTransactionSuccessful();
                getWritableDatabase().endTransaction();
                // continues here until return ! -->
            case GPS_POINT:
            case SAMPLE_POINT:
            case MAGNETIC_VALUE_POINT:
            case ROTATION_POINT:
                return table.deleteRow(getWritableDatabase(), adaptedSelection, selectionArgs);
            // Batch deletes:
            case MEASUREMENTS:
                if (selection != null && selection.equals(BaseColumns._ID+ "<=?")) {
                    gpsPointsTable = tables.get(GPS_POINTS);
                    rotationPointsTable = tables.get(ROTATION_POINTS);
                    magneticValuePointsTable = tables.get(MAGNETIC_VALUE_POINTS);
                    accelerationPointsTable = tables.get(SAMPLE_POINTS);

                    getWritableDatabase().beginTransaction();
                    gpsPointsTable.deleteRow(database, GpsPointsTable.COLUMN_MEASUREMENT_FK + "<=?", selectionArgs);
                    rotationPointsTable.deleteRow(database, RotationPointTable.COLUMN_MEASUREMENT_FK + "<=?", selectionArgs);
                    magneticValuePointsTable.deleteRow(database, MagneticValuePointTable.COLUMN_MEASUREMENT_FK + "<=?", selectionArgs);
                    accelerationPointsTable.deleteRow(database, SamplePointTable.COLUMN_MEASUREMENT_FK + "<=?", selectionArgs);
                    getWritableDatabase().setTransactionSuccessful();
                    getWritableDatabase().endTransaction();
                } else {
                    Log.e(TAG, "The batch deletion of measurements is not yet implemented !");
                    return 0;
                }
                // continues here until return ! -->
            case GPS_POINTS:
            case SAMPLE_POINTS:
            case MAGNETIC_VALUE_POINTS:
            case ROTATION_POINTS:
                return table.deleteRow(getWritableDatabase(), selection, selectionArgs);
            default:
                Log.e(TAG, "Unable to delete data from table corresponding to URI " + uri);
                return 0;
        }
    }

    public long insertRow(Uri uri, ContentValues values) {
        int uriType = sUriMatcher.match(uri);
        CyfaceMeasurementTable table = tables.get(uriType);
        return table.insertRow(getWritableDatabase(), values);
    }

    public long[] bulkInsert(Uri uri, List<ContentValues> values) {
        int uriType = sUriMatcher.match(uri);
        CyfaceMeasurementTable table = tables.get(uriType);
        return table.insertBatch(getWritableDatabase(), values);
    }

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
                String adaptedSelection = BaseColumns._ID + "=" + uri.getLastPathSegment() + (selection == null ?
                        "" : " AND " + selection);
                return table.query(getReadableDatabase(), projection, adaptedSelection, selectionArgs, sortOrder);
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }

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
                String adaptedSelection =
                        BaseColumns._ID + "=" + id + (TextUtils.isEmpty(selection) ? "" : " AND " + selection);
                return table.update(getWritableDatabase(), values, adaptedSelection, selectionArgs);
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }

    public String getType(final Uri uri) {
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