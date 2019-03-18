package de.cyface.persistence;

import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

import androidx.annotation.NonNull;

/**
 * This class represents the table containing the measurement-independent identifiers stored on this device.
 *
 * @author Armin Schnabel
 * @version 2.2.0
 * @since 3.0.0
 */
public final class IdentifierTable extends AbstractCyfaceMeasurementTable {

    /**
     * The path segment in the table URI identifying the identifier table.
     */
    final static String URI_PATH = "identifiers";
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
        return "CREATE TABLE " + getName() + " (" + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_DEVICE_ID + " TEXT NOT NULL);";
    }

    /**
     * Don't forget to update the {@link DatabaseHelper}'s {@code DATABASE_VERSION} if you upgrade this table.
     * <p>
     * The Upgrade is automatically executed in a transaction, do not wrap the code in another transaction!
     * <p>
     * This upgrades are called incrementally by {@link DatabaseHelper#onUpgrade(SQLiteDatabase, int, int)}.
     * <p>
     * Remaining documentation: {@link CyfaceMeasurementTable#onUpgrade}
     */
    @Override
    public void onUpgrade(@NonNull final SQLiteDatabase database, final int fromVersion, final int toVersion) {

        // noinspection SwitchStatementWithTooFewBranches - because others will follow and it's an easier read
        switch (fromVersion) {

            case 9:
                // This table was added in version 10
                onCreate(database);
                break; // onUpgrade is called incrementally by DatabaseHelper
        }
    }

    @Override
    protected String[] getDatabaseTableColumns() {
        return COLUMNS;
    }
}