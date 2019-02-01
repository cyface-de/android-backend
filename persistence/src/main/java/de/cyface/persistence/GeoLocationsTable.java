package de.cyface.persistence;

import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;

/**
 * Table for storing {@link GeoLocation} measuring points. The data in this table is intended for storage prior to
 * processing it by either transfer to a server or export to some external file or device.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.2.0
 * @since 1.0.0
 */
public class GeoLocationsTable extends AbstractCyfaceMeasurementTable {

    /**
     * The path segment in the table URI identifying the {@link GeoLocationsTable}.
     */
    final static String URI_PATH = "locations";
    /**
     * Column name for the column storing the {@link GeoLocation} timestamp.
     */
    public static final String COLUMN_GPS_TIME = "gps_time";
    /**
     * Column name for the column storing the {@link GeoLocation} latitude.
     */
    public static final String COLUMN_LAT = "lat";
    /**
     * Column name for the column storing the {@link GeoLocation} longitude.
     */
    public static final String COLUMN_LON = "lon";
    /**
     * Column name for the column storing the {@link GeoLocation} speed in meters per second.
     */
    public static final String COLUMN_SPEED = "speed";
    /**
     * Column name for the column storing the {@link GeoLocation} accuracy.
     */
    public static final String COLUMN_ACCURACY = "accuracy";
    /**
     * Column name for the column storing the foreign key referencing the {@link Measurement} for this
     * {@link GeoLocation}.
     */
    public static final String COLUMN_MEASUREMENT_FK = "measurement_fk";

    /**
     * An array containing all the column names used by a geo location table.
     */
    private static final String[] COLUMNS = {BaseColumns._ID, COLUMN_GPS_TIME, COLUMN_LAT, COLUMN_LON, COLUMN_SPEED,
            COLUMN_ACCURACY, COLUMN_MEASUREMENT_FK};

    /**
     * Provides a completely initialized object as a representation of a table containing geo locations in the database.
     */
    protected GeoLocationsTable() {
        super(URI_PATH);
    }

    @Override
    protected String getCreateStatement() {
        return "CREATE TABLE " + getName() + "(" + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_GPS_TIME + " INTEGER NOT NULL, " + COLUMN_LAT + " REAL NOT NULL, " + COLUMN_LON
                + " REAL NOT NULL, " + COLUMN_SPEED + " REAL NOT NULL, " + COLUMN_ACCURACY + " INTEGER NOT NULL, "
                + COLUMN_MEASUREMENT_FK + " INTEGER NOT NULL);";
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
            case 8:
                // This upgrade from 8 to 10 is executed for all SDK versions below 3 (which is v 10).
                // We don't support an soft-upgrade there but reset the database
                database.beginTransaction();
                database.execSQL("DELETE FROM gps_points;");
                database.execSQL("DROP TABLE gps_points;");
                database.execSQL(getCreateStatement());
                database.endTransaction();
                // continues with the next incremental upgrade until return ! -->
        }

    }

    @Override
    protected String[] getDatabaseTableColumns() {
        return COLUMNS;
    }
}
