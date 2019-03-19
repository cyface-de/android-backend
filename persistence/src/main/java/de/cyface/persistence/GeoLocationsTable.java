/*
 * Copyright 2017 Cyface GmbH
 * This file is part of the Cyface SDK for Android.
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.persistence;

import static de.cyface.persistence.Constants.TAG;

import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.util.Log;

import androidx.annotation.NonNull;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;

/**
 * Table for storing {@link GeoLocation} measuring points. The data in this table is intended for storage prior to
 * processing it by either transfer to a server or export to some external file or device.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.4.1
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
    public static final String COLUMN_GEOLOCATION_TIME = "gps_time";
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
    private static final String[] COLUMNS = {BaseColumns._ID, COLUMN_GEOLOCATION_TIME, COLUMN_LAT, COLUMN_LON,
            COLUMN_SPEED, COLUMN_ACCURACY, COLUMN_MEASUREMENT_FK};

    /**
     * Provides a completely initialized object as a representation of a table containing geo locations in the database.
     */
    protected GeoLocationsTable() {
        super(URI_PATH);
    }

    @Override
    protected String getCreateStatement() {
        return "CREATE TABLE " + getName() + " (" + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_GEOLOCATION_TIME + " INTEGER NOT NULL, " + COLUMN_LAT + " REAL NOT NULL, " + COLUMN_LON
                + " REAL NOT NULL, " + COLUMN_SPEED + " REAL NOT NULL, " + COLUMN_ACCURACY + " INTEGER NOT NULL, "
                + COLUMN_MEASUREMENT_FK + " INTEGER NOT NULL);";
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
                Log.d(TAG, "Upgrading geoLocation table from V8");
                migrateDatabaseFromV8(database);

                break; // onUpgrade is called incrementally by DatabaseHelper
        }

    }

    /**
     * Renames table, updates the table structure and copies the data.
     *
     * @param database The {@code SQLiteDatabase} to upgrade
     */
    private void migrateDatabaseFromV8(@NonNull final SQLiteDatabase database) {
        // To drop columns we need to copy the table. We anyway renamed the table to locations.
        database.execSQL("ALTER TABLE gps_points RENAME TO _locations_old;");

        // To drop columns "is_synced" we need to create a new table
        database.execSQL("CREATE TABLE locations (_id INTEGER PRIMARY KEY AUTOINCREMENT, gps_time INTEGER NOT NULL, "
                + "lat REAL NOT NULL, lon REAL NOT NULL, speed REAL NOT NULL, accuracy INTEGER NOT NULL, "
                + "measurement_fk INTEGER NOT NULL);");
        // and insert the old data accordingly. This is anyway cleaner (no defaults)
        // We ignore the value as we upload to a new API.
        database.execSQL("INSERT INTO locations " + "(_id,gps_time,lat,lon,speed,accuracy,measurement_fk) "
                + "SELECT _id,gps_time,lat,lon,speed,accuracy,measurement_fk " + "FROM _locations_old");

        // Remove temp table
        database.execSQL("DROP TABLE _locations_old;");
    }

    @Override
    protected String[] getDatabaseTableColumns() {
        return COLUMNS;
    }
}
