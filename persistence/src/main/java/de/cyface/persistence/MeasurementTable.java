/*
 * Created on 23.01.15.
 */
package de.cyface.persistence;

import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.util.Log;

/**
 * This class represents the table containing all the measurements currently stored on this device.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
public class MeasurementTable extends AbstractCyfaceMeasurementTable {

    /**
     * Logging tag for Android logging.
     */
    static final String TAG = "MeasurementTable";
    /**
     * A boolean value which is either <code>true</code> (or 1 in SQLLite) if this measurement has been completed or
     * <code>false</code> (or 0 in SQLLite) otherwise. Usually only one measurement should not be finished; else there
     * has been some error.
     */
    public static final String COLUMN_FINISHED = "finished";
    /**
     * A string which contains the .name() value of the vehicle enumeration
     */
    public static final String COLUMN_VEHICLE = "vehicle";

    /**
     * An array containing all columns from this table in default order.
     */
    private static final String[] COLUMNS = {BaseColumns._ID, COLUMN_FINISHED, COLUMN_VEHICLE};

    /**
     * Creates a new completely initialized {@code MeasurementTable} using the name "measurement".
     */
    MeasurementTable() {
        super("measurement");
    }

    @Override
    protected String getCreateStatement() {
        return "CREATE TABLE " + getName() + "(" + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_FINISHED + " INTEGER NOT NULL DEFAULT 1, " + COLUMN_VEHICLE + " TEXT);";
    }

    /* Don't forget to update the DatabaseHelper's DATABASE_VERSION */
    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        switch (oldVersion) {
            case 3:
                // nothing to do here
                // no break, thus, the upgrade process continues with the next incremental upgrade step ->
            case 5:
                Log.w(TAG, "Upgrading " + getName() + " from version 5 to 6"); // For some reason this does not show up
                                                                               // in log even though it's called
                database.execSQL("ALTER TABLE " + getName() + " ADD COLUMN " + COLUMN_VEHICLE + " TEXT;");
            case 7:
                database.beginTransaction();
                database.execSQL("CREATE TABLE " + getName() + "_backup (" + BaseColumns._ID
                        + " INTEGER PRIMARY KEY AUTOINCREMENT, " + COLUMN_FINISHED + " INTEGER NOT NULL DEFAULT 1,"
                        + COLUMN_VEHICLE + " TEXT);");
                database.execSQL("INSERT INTO " + getName() + "_backup (" + BaseColumns._ID + ", " + COLUMN_VEHICLE
                        + ")" + " SELECT " + BaseColumns._ID + ", " + COLUMN_VEHICLE + " FROM " + getName() + ";");
                database.execSQL("DROP TABLE " + getName() + ";");
                database.execSQL("ALTER TABLE " + getName() + "_backup RENAME TO " + getName() + ";");
                database.endTransaction();
        }
    }

    @Override
    protected String[] getDatabaseTableColumns() {
        return COLUMNS;
    }
}
