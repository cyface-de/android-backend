/*
 * Created at 15:22:46 on 19.01.2015
 */
package de.cyface.persistence;

import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.util.Log;

/**
 * <p>
 * Table for storing GPS measuring points. The data in this table is intended for storage prior to processing it by
 * either transfer to a server or export to some external file or device.
 * </p>
 *
 * @author Klemens Muthmann
 * @version 1.2.0
 * @since 1.0.0
 */
public class GpsPointsTable extends AbstractCyfaceMeasurementTable {

    static final String TAG = "GpsPointsTable";
    /**
     * The path segment in the table URI identifying the geo locations table.
     */
    public final static String URI_PATH = "measuring";
    public static final String COLUMN_GPS_TIME = "gps_time";
    public static final String COLUMN_LAT = "lat";
    public static final String COLUMN_LON = "lon";
    public static final String COLUMN_SPEED = "speed";
    public static final String COLUMN_ACCURACY = "accuracy";
    public static final String COLUMN_MEASUREMENT_FK = "measurement_fk";
    public static final String COLUMN_IS_SYNCED = "is_synced";


    static final String[] COLUMNS = {BaseColumns._ID, COLUMN_GPS_TIME, COLUMN_LAT,
            COLUMN_LON, COLUMN_SPEED, COLUMN_ACCURACY, COLUMN_MEASUREMENT_FK, COLUMN_IS_SYNCED};

    protected GpsPointsTable() {
        super("gps_points");
    }

    @Override
    protected String getCreateStatement() {
        return "CREATE TABLE " + getName() + "(" + BaseColumns._ID
                + " INTEGER PRIMARY KEY AUTOINCREMENT, " + COLUMN_GPS_TIME + " INTEGER NOT NULL, "
                + COLUMN_LAT + " REAL NOT NULL, " + COLUMN_LON + " REAL NOT NULL, " + COLUMN_SPEED + " REAL NOT NULL, "
                + COLUMN_ACCURACY + " INTEGER NOT NULL, " + COLUMN_MEASUREMENT_FK + " INTEGER NOT NULL, "
                + COLUMN_IS_SYNCED + " INTEGER NOT NULL DEFAULT 0);";
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        switch(oldVersion) {
            case 3:
                // nothing to do here
                // no break, thus, the upgrade process continues with the next incremental upgrade step ->
            case 4:
                Log.w(TAG, "Upgrading " + getName() + " from version 4 to 5"); // For some reason this does not show up in log even though it's called
                database.execSQL("ALTER TABLE " + getName() + " ADD COLUMN " + COLUMN_ACCURACY + " INTEGER NOT NULL DEFAULT 0;");
        }
    }

    @Override
    protected String[] getDatabaseTableColumns() {
        return COLUMNS;
    }
}
