package de.cyface.persistence;

import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

/**
 * A table for sample points (acceleration) from the android acceleration sensor.
 *
 * @author Klemens Muthmann
 * @version 1.2.3
 * @since 1.0.0
 */
public class AccelerationPointTable extends AbstractCyfaceMeasurementTable {

    /**
     * The path segment in the table URI identifying the accelerations table.
     */
    public final static String URI_PATH = "sample";

    /**
     * Creates a new completely initialized {@code AccelerationPointTable} using "sample_points" as table name.
     */
    public AccelerationPointTable() {
        super("sample_points");
    }

    /**
     * Column name for the column storing the acceleration value in X direction in m/s² using the device coordinate
     * system.
     */
    public static final String COLUMN_AX = "ax";
    /**
     * Column name for the column storing the acceleration value in Y direction in m/s² using the device coordinate
     * system.
     */
    public static final String COLUMN_AY = "ay";
    /**
     * Column name for the column storing the acceleration value in Z direction in m/s² using the device coordinate
     * system.
     */
    public static final String COLUMN_AZ = "az";
    /**
     * Column name for the column storing the timestamp this point was captured at in milliseconds since 01.01.1970
     * (UNIX timestamp format).
     */
    public static final String COLUMN_TIME = "time";
    /**
     * Column name for the foreign key to the measurement this point belongs to.
     */
    public static final String COLUMN_MEASUREMENT_FK = "measurement_fk";
    /**
     * Column name for the column storing either a '1' if this point has been synchronized with a Cyface server and '0'
     * otherwise.
     */
    public static final String COLUMN_IS_SYNCED = "is_synced";

    /**
     * An array containing all columns from this table in default order.
     */
    private static final String[] COLUMNS = {BaseColumns._ID, COLUMN_AX, COLUMN_AY, COLUMN_AZ, COLUMN_TIME,
            COLUMN_MEASUREMENT_FK, COLUMN_IS_SYNCED};

    @Override
    protected String getCreateStatement() {
        return "CREATE TABLE " + getName() + "(" + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + COLUMN_AX
                + " REAL NOT NULL, " + COLUMN_AY + " REAL NOT NULL, " + COLUMN_AZ + " REAL NOT NULL, " + COLUMN_TIME
                + " INTEGER NOT NULL, " + COLUMN_MEASUREMENT_FK + " INTEGER NOT NULL, " + COLUMN_IS_SYNCED
                + " INTEGER NOT NULL DEFAULT 0);";
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        switch (oldVersion) {
            case 3:
                // nothing to do here
                // no break, thus, the upgrade process continues with the next incremental upgrade step ->
        }
    }

    @Override
    protected String[] getDatabaseTableColumns() {
        return COLUMNS;
    }
}
