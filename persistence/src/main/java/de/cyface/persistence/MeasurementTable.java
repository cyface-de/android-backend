package de.cyface.persistence;

import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.Point3d;
import de.cyface.persistence.model.Vehicle;
import de.cyface.persistence.serialization.MeasurementSerializer;

/**
 * This class represents the table containing all the {@link Measurement}s currently stored on this device.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.3.0
 * @since 1.0.0
 */
public class MeasurementTable extends AbstractCyfaceMeasurementTable {

    /**
     * The path segment in the table URI identifying the {@link MeasurementTable}.
     */
    static final String URI_PATH = "measurements";
    /**
     * Column name for {@link MeasurementStatus#getDatabaseIdentifier()} of the {@link Measurement}.
     * Usually only one measurement should be in the {@link MeasurementStatus#OPEN} or {@link MeasurementStatus#PAUSED}
     * state, else there has been some error.
     */
    public static final String COLUMN_STATUS = "status";
    /**
     * Column name for the {@link Vehicle#getDatabaseIdentifier()} value of the {@link Vehicle} enumeration.
     */
    public static final String COLUMN_VEHICLE = "vehicle";
    /**
     * Column name for the number of acceleration {@link Point3d}s persisted for this {@link Measurement}.
     */
    public static final String COLUMN_ACCELERATIONS = "accelerations";
    /**
     * Column name for the number of rotation {@link Point3d}s persisted for this {@link Measurement}.
     */
    public static final String COLUMN_ROTATIONS = "rotations";
    /**
     * Column name for the number of direction {@link Point3d}s persisted for this {@link Measurement}.
     */
    public static final String COLUMN_DIRECTIONS = "directions";
    /**
     * Column name for the {@link MeasurementSerializer#PERSISTENCE_FILE_FORMAT_VERSION} for the data in the file
     * persistence layer of for this {@link Measurement}.
     */
    public static final String COLUMN_PERSISTENCE_FILE_FORMAT_VERSION = "file_format_version";
    /**
     * Column name for the distance of this {@link Measurement} based on its {@link GeoLocation}s in meters.
     */
    public static final String COLUMN_DISTANCE = "distance";
    /**
     * An array containing all columns from this table in default order.
     */
    private static final String[] COLUMNS = {BaseColumns._ID, COLUMN_STATUS, COLUMN_VEHICLE, COLUMN_ACCELERATIONS,
            COLUMN_ROTATIONS, COLUMN_DIRECTIONS, COLUMN_PERSISTENCE_FILE_FORMAT_VERSION, COLUMN_DISTANCE};

    /**
     * Creates a new completely initialized {@code MeasurementTable} using the name {@link #URI_PATH}.
     */
    MeasurementTable() {
        super(URI_PATH);
    }

    @Override
    protected String getCreateStatement() {
        return "CREATE TABLE " + getName() + " (" + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_STATUS + " TEXT NOT NULL, " + COLUMN_VEHICLE + " TEXT NOT NULL, " + COLUMN_ACCELERATIONS
                + " INTEGER NOT NULL, " + COLUMN_ROTATIONS + " INTEGER NOT NULL, " + COLUMN_DIRECTIONS
                + " INTEGER NOT NULL, " + COLUMN_PERSISTENCE_FILE_FORMAT_VERSION + " SHORT INTEGER NOT NULL, "
                + COLUMN_DISTANCE + " REAL NOT NULL);";
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

                // We use a transaction as this lead to an unresolvable error where the IdentifierTable
                // was not created in time for the first database query.

                database.execSQL("DELETE FROM measurement;");
                database.execSQL("DROP TABLE measurement;");
                onCreate(database);
                // continues with the next incremental upgrade until return ! -->
            case 10:
                database.execSQL("ALTER TABLE " + getName() + " ADD COLUMN " + COLUMN_DISTANCE + " REAL NOT NULL;");
                // continues with the next incremental upgrade until return ! -->
        }

    }

    @Override
    protected String[] getDatabaseTableColumns() {
        return COLUMNS;
    }
}
