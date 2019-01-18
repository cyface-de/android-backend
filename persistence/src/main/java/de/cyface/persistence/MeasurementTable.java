package de.cyface.persistence;

import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
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
 * @version 2.0.0
 * @since 1.0.0
 */
public class MeasurementTable extends AbstractCyfaceMeasurementTable {

    /**
     * The path segment in the table URI identifying the measurements table.
     */
    public static final String URI_PATH = "measurement";
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
     * An array containing all columns from this table in default order.
     */
    private static final String[] COLUMNS = {BaseColumns._ID, COLUMN_STATUS, COLUMN_VEHICLE, COLUMN_ACCELERATIONS,
            COLUMN_ROTATIONS, COLUMN_DIRECTIONS, COLUMN_PERSISTENCE_FILE_FORMAT_VERSION};

    /**
     * Creates a new completely initialized {@code MeasurementTable} using the name {@link #URI_PATH}.
     */
    MeasurementTable() {
        super(URI_PATH);
    }

    @Override
    protected String getCreateStatement() {
        return "CREATE TABLE " + getName() + "(" + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_STATUS + " TEXT NOT NULL, " + COLUMN_VEHICLE + " TEXT NOT NULL, " + COLUMN_ACCELERATIONS
                + " INTEGER NOT NULL DEFAULT, " + COLUMN_ROTATIONS + " INTEGER NOT NULL, " + COLUMN_DIRECTIONS
                + " INTEGER NOT NULL, " + COLUMN_PERSISTENCE_FILE_FORMAT_VERSION + " SHORT INTEGER NOT NULL);";
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
            case 9:
                // The upgrade from 8 to 9 is executed for all SDK versions below 3 (which is version 10).
                // We don't support an upgrade for data captured before version 3 so all old data is deleted.
                database.beginTransaction();
                database.execSQL("DELETE FROM " + getName() + ";");
                database.endTransaction();
                // continues with the next incremental upgrade until return ! -->
        }
    }

    @Override
    protected String[] getDatabaseTableColumns() {
        return COLUMNS;
    }
}
