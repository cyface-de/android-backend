package de.cyface.persistence;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.provider.BaseColumns;
import android.util.Log;

import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.Point3d;
import de.cyface.persistence.model.Vehicle;
import de.cyface.persistence.serialization.MeasurementSerializer;

import static de.cyface.persistence.Constants.TAG;

/**
 * This class represents the table containing all the {@link Measurement}s currently stored on this device.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.4.2
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
                + " INTEGER NOT NULL, " + COLUMN_PERSISTENCE_FILE_FORMAT_VERSION + " INTEGER NOT NULL, "
                + COLUMN_DISTANCE + " REAL NOT NULL);";
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

            case 8:
                // To drop columns we need to copy the table. We anyway renamed the table to measurement*s*.
                database.execSQL("ALTER TABLE measurement RENAME TO _measurements_old;");

                // Due to a bug in the code of V8 MeasurementTable we may need to create the sync column
                try {
                    database.execSQL("ALTER TABLE _measurements_old ADD COLUMN synced INTEGER NOT NULL DEFAULT 0");
                } catch (final SQLiteException ex) {
                    Log.w(TAG, "Altering measurements: " + ex.getMessage());
                }

                // Columns "accelerations", "rotations", and "directions" were added
                // We don't support a data preserving upgrade for sensor data stored in the database
                // Thus, the data is deleted in DatabaseHelper#onUpgrade and the counters are set to 0.
                database.execSQL("ALTER TABLE _measurements_old ADD COLUMN accelerations INTEGER NOT NULL DEFAULT 0");
                database.execSQL("ALTER TABLE _measurements_old ADD COLUMN rotations INTEGER NOT NULL DEFAULT 0");
                database.execSQL("ALTER TABLE _measurements_old ADD COLUMN directions INTEGER NOT NULL DEFAULT 0");
                // For the same reason we can just set the file_format_version to 1 (first supported version)
                database.execSQL(
                        "ALTER TABLE _measurements_old ADD COLUMN file_format_version INTEGER NOT NULL DEFAULT 1");

                // Distance column was added. Calculate the distance for existing entries.
                database.execSQL("ALTER TABLE _measurements_old ADD COLUMN distance REAL NOT NULL DEFAULT 0.0;");
                // FIXME: calculate distance for old entries!

                // Columns "finished" and "synced" are now in the "status" column
                // To migrate old measurements we need to set a default which is then adjusted
                database.execSQL("ALTER TABLE _measurements_old ADD COLUMN status TEXT NOT NULL DEFAULT 'MIGRATION'");
                database.execSQL("UPDATE _measurements_old SET status = 'OPEN' WHERE finished = 0 AND synced = 0");
                database.execSQL("UPDATE _measurements_old SET status = 'FINISHED' WHERE finished = 1 AND synced = 0");
                database.execSQL("UPDATE _measurements_old SET status = 'SYNCED' WHERE finished = 1 AND synced = 1");

                // To drop columns "finished" and "synced" we need to create a new table
                database.execSQL("CREATE TABLE measurements (_id INTEGER PRIMARY KEY AUTOINCREMENT, "+
                        "status TEXT NOT NULL, vehicle TEXT NOT NULL, accelerations INTEGER NOT NULL, " +
                        "rotations INTEGER NOT NULL, directions INTEGER NOT NULL, file_format_version INTEGER NOT NULL, "
                        + "distance REAL NOT NULL);");
                // and insert the old data accordingly. This is anyway cleaner (no defaults)
                database.execSQL("INSERT INTO measurements "+
                        "(_id,status,vehicle,accelerations,rotations,directions,file_format_version,distance) "+
                        "SELECT _id,status,vehicle,accelerations,rotations,directions,file_format_version,distance "+
                        "FROM _measurements_old");

                break; // onUpgrade is called incrementally by DatabaseHelper
        }

    }

    @Override
    protected String[] getDatabaseTableColumns() {
        return COLUMNS;
    }
}
