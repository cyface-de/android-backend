package de.cyface.persistence;

import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

import de.cyface.persistence.model.Event;
import de.cyface.persistence.model.Measurement;

/**
 * Table for storing {@link Event}s.
 *
 * @author Armin Schnabel
 * @version 1.0.1
 * @since 4.0.0
 */
public class EventTable extends AbstractCyfaceMeasurementTable {

    /**
     * The path segment in the table URI identifying the {@link EventTable}.
     */
    final static String URI_PATH = "events";
    /**
     * Column name for the column storing the {@link Event} timestamp.
     */
    public static final String COLUMN_TIMESTAMP = "timestamp";
    /**
     * Column name for the column storing the {@link Event.EventType#getDatabaseIdentifier()}.
     */
    public static final String COLUMN_TYPE = "type";
    /**
     * Column name for the column storing the foreign key referencing the {@link Measurement} for this
     * {@link Event} if the {@code Event} is linked to a {@code Measurement}.
     */
    public static final String COLUMN_MEASUREMENT_FK = "measurement_fk";
    /**
     * An array containing all the column names used by a {@link EventTable}.
     */
    private static final String[] COLUMNS = {BaseColumns._ID, COLUMN_TIMESTAMP, COLUMN_TYPE, COLUMN_MEASUREMENT_FK};

    /**
     * Provides a completely initialized object as a representation of a table containing {@link Event}s in the
     * database.
     */
    EventTable() {
        super(URI_PATH);
    }

    @Override
    protected String getCreateStatement() {
        // The COLUMN_MEASUREMENT_FK may be null if the Event is not linked to a Measurement.
        return "CREATE TABLE " + getName() + " (" + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_TIMESTAMP + " INTEGER NOT NULL, " + COLUMN_TYPE + " TEXT NOT NULL, " + COLUMN_MEASUREMENT_FK
                + " INTEGER);";
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
    public void onUpgrade(final SQLiteDatabase database, final int fromVersion, final int toVersion) {

        switch (fromVersion) {

            case 11:
                // This table was added in version 12
                onCreate(database);
                break; // onUpgrade is called incrementally by DatabaseHelper
        }

    }

    @Override
    protected String[] getDatabaseTableColumns() {
        return COLUMNS;
    }
}
