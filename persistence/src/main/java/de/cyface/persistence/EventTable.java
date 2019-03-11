package de.cyface.persistence;

import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

import de.cyface.persistence.model.Event;
import de.cyface.persistence.model.Measurement;

/**
 * Table for storing {@link Event}s.
 *
 * @author Armin Schnabel
 * @version 1.0.0
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
    protected EventTable() {
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
     *
     * Remaining documentation: {@link AbstractCyfaceMeasurementTable#onUpgrade}
     */
    @Override
    public void onUpgrade(final SQLiteDatabase database, final int oldVersion, final int newVersion) {

        // noinspection SwitchStatementWithTooFewBranches - because others will follow and it's an easier read
        switch (oldVersion) {
            case 11:
                onCreate(database);
                // continues with the next incremental upgrade until return ! -->
        }

    }

    @Override
    protected String[] getDatabaseTableColumns() {
        return COLUMNS;
    }
}
