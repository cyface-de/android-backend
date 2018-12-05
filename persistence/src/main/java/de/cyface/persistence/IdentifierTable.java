package de.cyface.persistence;

import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.util.Log;

import static de.cyface.persistence.Constants.TAG;

/**
 * This class represents the table containing the measurement-independent identifiers stored on this device.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.0.0
 */
public class IdentifierTable extends AbstractCyfaceTable {

    /**
     * The path segment in the table URI identifying the identifier table.
     */
    public final static String URI_PATH = "identifiers";
    /**
     * A String value which contains an identifier for this device.
     */
    public static final String COLUMN_DEVICE_ID = "device_id";
    /**
     * An int value which contains the identifier to be used for the next measurement;
     * If this value is reset than the {@code COLUMN_DEVICE_ID} must be reset, too to avoid duplicate measurement
     * identifiers on the server. We currently start counting from 1 upwards.
     */
    public static final String COLUMN_NEXT_MEASUREMENT_ID = "next_measurement_id";

    /**
     * An array containing all columns from this table in default order.
     */
    private static final String[] COLUMNS = {BaseColumns._ID, COLUMN_DEVICE_ID, COLUMN_NEXT_MEASUREMENT_ID};

    /**
     * Creates a new completely initialized {@code IdentifierTable} using the name {@code URI_PATH}.
     */
    IdentifierTable() {
        super(URI_PATH);
    }

    @Override
    protected String getCreateStatement() {
        return "CREATE TABLE " + getName() + "(" + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_DEVICE_ID + " TEXT NOT NULL, " + COLUMN_NEXT_MEASUREMENT_ID + " INTEGER NOT NULL DEFAULT -1);";
    }

    /* Don't forget to update the DatabaseHelper's DATABASE_VERSION */
    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        Log.d(TAG, "Upgrading " + getName() + " from version " + oldVersion + " to " + newVersion + " ...");
        switch (oldVersion) {
            // no break, thus, the upgrade process continues with the next incremental upgrade step
            case 8:
                // For some reason this does not show up in log even though it's called
                Log.w(TAG, "Upgrading " + getName() + " from version 8 to 9");
                onCreate(database);
        }
    }

    @Override
    protected String[] getDatabaseTableColumns() {
        return COLUMNS;
    }
}
