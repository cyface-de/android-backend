package de.cyface.persistence;

import static de.cyface.persistence.Constants.TAG;

import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.util.Log;
import androidx.annotation.NonNull;

/**
 * This class represents the table containing the measurement-independent identifiers stored on this device.
 *
 * @author Armin Schnabel
 * @version 2.0.1
 * @since 3.0.0
 */
public final class IdentifierTable extends AbstractCyfaceMeasurementTable {

    /**
     * The path segment in the table URI identifying the identifier table.
     */
    final static String URI_PATH = "identifier";
    /**
     * A String value which contains an identifier for this device.
     */
    final static String COLUMN_DEVICE_ID = "device_id";

    /**
     * An array containing all columns from this table in default order.
     */
    private final static String[] COLUMNS = {BaseColumns._ID, COLUMN_DEVICE_ID};

    /**
     * Creates a new completely initialized {@code IdentifierTable} using the name {@code URI_PATH}.
     */
    IdentifierTable() {
        super(URI_PATH);
    }

    @Override
    protected String getCreateStatement() {
        return "CREATE TABLE " + getName() + "(" + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_DEVICE_ID + " TEXT NOT NULL);";
    }

    /* Don't forget to update the DatabaseHelper's DATABASE_VERSION */
    @Override
    public void onUpgrade(@NonNull final SQLiteDatabase database, final int oldVersion, final int newVersion) {
        Log.d(TAG, "Upgrading " + getName() + " from version " + oldVersion + " to " + newVersion + " ...");
        // switch (oldVersion) {
        // case 10:
        // no break, thus, the upgrade process continues with the next incremental upgrade step
        // }
    }

    @Override
    protected String[] getDatabaseTableColumns() {
        return COLUMNS;
    }
}