/*
 * Created on 23.01.15.
 */
package de.cyface.persistence;

import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.util.Log;

/**
 * <p>
 * This class represents the table containing all the measurements currently stored on this device.
 * </p>
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
public class MeasurementTable extends AbstractCyfaceMeasurementTable {

    /**
     * <p>
     * Logging tag for Android logging.
     * </p>
     */
    static final String TAG = "MeasurementTable";
    /**
     * <p>
     * This column is not used for anything, just to be able to create a measurement in the db without defining any values,
     * see: http://stackoverflow.com/a/2663620
     * </p>
     */
    public static final String COLUMN_WORKAROUND = "workaround";
    /**
     * <p>A string which contains the .name() value of the vehicle enumeration</p>
     */
    public static final String COLUMN_VEHICLE = "vehicle";

    /**
     * <p>
     * An array containing all columns from this table in default order.
     * </p>
     */
    private static final String[] COLUMNS = {BaseColumns._ID, COLUMN_WORKAROUND, COLUMN_VEHICLE};

    /**
     * <p>
     * Creates a new completely initialized {@code MeasurementTable} using the name "measurement".
     * </p>
     */
    MeasurementTable() {
        super("measurement");
    }

    @Override
    protected String getCreateStatement() {
        return "CREATE TABLE " + getName() + "(" + BaseColumns._ID
                + " INTEGER PRIMARY KEY AUTOINCREMENT, " + COLUMN_WORKAROUND + " REAL not null, " +
                COLUMN_VEHICLE + " TEXT);";
    }

    /* Don't forget to update the DatabaseHelper's DATABASE_VERSION */
    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        switch(oldVersion) {
            case 3:
                // nothing to do here
                // no break, thus, the upgrade process continues with the next incremental upgrade step ->
            case 5:
                Log.w(TAG, "Upgrading " + getName() + " from version 5 to 6"); // For some reason this does not show up in log even though it's called
                database.execSQL("ALTER TABLE " + getName() + " ADD COLUMN " + COLUMN_VEHICLE+ " TEXT;");
            /*case 6:
                /*database.beginTransaction();
                database.execSQL("CREATE TABLE " + getName() + "_backup (" + BaseColumns._ID
                        + " INTEGER PRIMARY KEY AUTOINCREMENT, " + COLUMN_VEHICLE + " TEXT);");
                database.execSQL("INSERT INTO " + getName() + "_backup (" + BaseColumns._ID + ", "+ COLUMN_VEHICLE + ")"
                        + " SELECT " + BaseColumns._ID + ", "+ COLUMN_VEHICLE + " FROM " + getName() + ";");
                database.execSQL("DROP TABLE " + getName() + ";");
                database.execSQL("ALTER TABLE " + getName() + "_backup RENAME TO " + getName() + ";");
                database.endTransaction();*/
        }
    }

    @Override
    protected String[] getDatabaseTableColumns() {
        return COLUMNS;
    }
}
