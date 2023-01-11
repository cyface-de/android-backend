/*
 * Copyright 2017 Cyface GmbH
 *
 * This file is part of the Cyface SDK for Android.
 *
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.persistence;

import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.GeoLocationV6;
import de.cyface.persistence.model.Measurement;

/**
 * Table for storing {@link GeoLocation} measuring points. The data in this table is intended for storage prior to
 * processing it by either transfer to a server or export to some external file or device.
 *
 * FIXME: Implement this when the structure is clear.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.4.4
 * @since 1.0.0
 */
public class GeoLocationsTableV6 extends AbstractCyfaceMeasurementTable {

    /**
     * The path segment in the table URI identifying the {@link GeoLocationsTableV6}.
     */
    final static String URI_PATH = "locations";
    /**
     * Column name for the column storing the {@link GeoLocationV6} timestamp.
     */
    public static final String COLUMN_GEOLOCATION_TIME = "gps_time";
    /**
     * Column name for the column storing the {@link GeoLocationV6} latitude.
     */
    public static final String COLUMN_LAT = "lat";
    /**
     * Column name for the column storing the {@link GeoLocationV6} longitude.
     */
    public static final String COLUMN_LON = "lon";
    /**
     * Column name for the column storing the {@link GeoLocationV6} altitude.
     */
    public static final String COLUMN_ALTITUDE = "altitude";
    /**
     * Column name for the column storing the {@link GeoLocationV6} speed in meters per second.
     */
    public static final String COLUMN_SPEED = "speed";
    /**
     * Column name for the column storing the {@link GeoLocationV6} accuracy in centimeters.
     */
    public static final String COLUMN_ACCURACY = "accuracy";
    /**
     * Column name for the column storing the {@link GeoLocationV6} vertical accuracy in centimeters.
     */
    public static final String COLUMN_VERTICAL_ACCURACY = "vertical_accuracy";
    /**
     * Column name for the column storing the foreign key referencing the {@link Measurement} for this
     * {@link GeoLocationV6}.
     */
    public static final String COLUMN_MEASUREMENT_FK = "measurement_fk";
    /**
     * An array containing all the column names used by a geo location table.
     */
    private static final String[] COLUMNS = {BaseColumns._ID, COLUMN_GEOLOCATION_TIME, COLUMN_LAT, COLUMN_LON,
            COLUMN_ALTITUDE,
            COLUMN_SPEED, COLUMN_ACCURACY, COLUMN_VERTICAL_ACCURACY, COLUMN_MEASUREMENT_FK};

    /**
     * Provides a completely initialized object as a representation of a table containing geo locations in the database.
     */
    protected GeoLocationsTableV6() {
        super(URI_PATH);
    }

    @Override
    protected String getCreateStatement() {
        return "CREATE TABLE " + getName() + " (" + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_GEOLOCATION_TIME + " INTEGER NOT NULL, " + COLUMN_LAT + " REAL NOT NULL, " + COLUMN_LON
                + " REAL NOT NULL, " + COLUMN_ALTITUDE + " REAL NOT NULL, " + COLUMN_SPEED + " REAL NOT NULL, "
                + COLUMN_ACCURACY + " INTEGER NOT NULL, " + COLUMN_VERTICAL_ACCURACY + " INTEGER NOT NULL, "
                + COLUMN_MEASUREMENT_FK + " INTEGER NOT NULL);";
    }

    /**
     * Don't forget to update the {@link DatabaseHelperV6}'s {@code DATABASE_VERSION} if you upgrade this table.
     * <p>
     * The Upgrade is automatically executed in a transaction, do not wrap the code in another transaction!
     * <p>
     * This upgrades are called incrementally by {@link DatabaseHelperV6#onUpgrade(SQLiteDatabase, int, int)}.
     * <p>
     * Remaining documentation: {@link CyfaceMeasurementTable#onUpgrade}
     */
    @Override
    public void onUpgrade(final SQLiteDatabase database, final int fromVersion, final int toVersion) {

        // switch (fromVersion) {} - no upgrades, yet
    }

    @Override
    protected String[] getDatabaseTableColumns() {
        return COLUMNS;
    }
}
