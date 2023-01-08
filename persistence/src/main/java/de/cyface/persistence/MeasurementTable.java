/*
 * Copyright 2017-2023 Cyface GmbH
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

import static de.cyface.persistence.Constants.TAG;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.util.Log;

import androidx.annotation.NonNull;

import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.Modality;
import de.cyface.persistence.serialization.MeasurementSerializer;
import de.cyface.utils.Validate;

/**
 * This class represents the table containing all the {@link Measurement}s currently stored on this device.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 4.0.0
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
     * Column name for the {@link Modality#getDatabaseIdentifier()} value of the {@link Modality} enumeration.
     */
    public static final String COLUMN_MODALITY = "modality";
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
     * Column name for the sum of all valid speed samples captured for this {@link Measurement} based on its
     * {@link GeoLocation}s in meters per second.
     */
    public static final String COLUMN_SPEED_SUM = "speed_sum";
    /**
     * Column name for the number of valid speed samples captured for this {@link Measurement} based on its
     * {@link GeoLocation}s.
     */
    public static final String COLUMN_SPEED_COUNTER = "speed_counter";
    /**
     * Column name for the Unix timestamp in milliseconds of this {@link Measurement}.
     */
    public static final String COLUMN_TIMESTAMP = "timestamp";
    /**
     * An array containing all columns from this table in default order.
     */
    private static final String[] COLUMNS = {BaseColumns._ID, COLUMN_STATUS, COLUMN_MODALITY,
            COLUMN_PERSISTENCE_FILE_FORMAT_VERSION, COLUMN_DISTANCE, COLUMN_SPEED_SUM, COLUMN_SPEED_COUNTER,
            COLUMN_TIMESTAMP};

    /**
     * Creates a new completely initialized {@code MeasurementTable} using the name {@link #URI_PATH}.
     */
    MeasurementTable() {
        super(URI_PATH);
    }

    @Override
    protected String getCreateStatement() {
        return "CREATE TABLE " + getName() + " (" + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_STATUS + " TEXT NOT NULL, " + COLUMN_MODALITY + " TEXT NOT NULL, "
                + COLUMN_PERSISTENCE_FILE_FORMAT_VERSION + " INTEGER NOT NULL, " + COLUMN_DISTANCE + " REAL NOT NULL, "
                + COLUMN_SPEED_SUM + " REAL NOT NULL, " + COLUMN_SPEED_COUNTER + " INTEGER NOT NULL, "
                + COLUMN_TIMESTAMP + " INTEGER NOT NULL);";
    }

    /**
     * Don't forget to update the {@link DatabaseHelper}'s {@code DATABASE_VERSION} if you upgrade this table.
     * <p>
     * The Upgrade is automatically executed in a transaction, do not wrap the code in another transaction!
     * <p>
     * This upgrade is called incrementally by {@link DatabaseHelper#onUpgrade(SQLiteDatabase, int, int)}.
     * <p>
     * Remaining documentation: {@link CyfaceMeasurementTable#onUpgrade}
     */
    @Override
    public void onUpgrade(final SQLiteDatabase database, final int fromVersion, final int toVersion) {

        switch (fromVersion) {

            case 8:
                Log.d(TAG, "Upgrading measurement table from V8");
                migrateDatabaseFromV8(database);

                break; // onUpgrade is called incrementally by DatabaseHelper

            case 9:
                // Version 9 was never released so we use this to make sure the MeasurementTable and GeoLocationsTable
                // already migrated from V8. Now we calculate the correct distance for the migrated data:
                Log.d(TAG, "Calculating distances for migrated V8 measurements");

                updateDistanceForV8Measurements(database);

                break; // onUpgrade is called incrementally by DatabaseHelper

            case 12:
                Log.d(TAG, "Removing sensor point counter columns from measurement table");

                migrateDatabaseFromV12(database);

                break; // onUpgrade is called incrementally by DatabaseHelper

            case 13:
                Log.d(TAG, "Upgrading measurement table from V13");
                migrateDatabaseFromV13(database);

                // < V14 measurements without GeoLocations will receive an 0L timestamp
                Log.d(TAG, "Calculating timestamp for migrated V13 measurements");
                updateMeasurementTimestampForV13Measurements(database);

                break; // onUpgrade is called incrementally by DatabaseHelper

            case 15:
                // This column was added in version 16
                Log.d(TAG, "Upgrading measurement table from V15");
                migrateDatabaseFromV15(database);

                break; // onUpgrade is called incrementally by DatabaseHelper

            case 16:
                // These columns were added in version 17
                Log.d(TAG, "Upgrading measurement table from V16");
                migrateDatabaseFromV16(database);

                break;
        }

    }

    /**
     * Adds columns related to average speed to table and calculates the values for existing data.
     * <p>
     * Like for newly captured measurements, the average speed is only based on locations not filtered
     * by the {@link DefaultLocationCleaningStrategy}.
     *
     * @param database The {@code SQLiteDatabase} to upgrade
     */
    private void migrateDatabaseFromV16(@NonNull final SQLiteDatabase database) {

        database.execSQL("ALTER TABLE measurements ADD COLUMN speed_sum REAL NOT NULL DEFAULT 0.0");
        database.execSQL("ALTER TABLE measurements ADD COLUMN speed_counter INTEGER NOT NULL DEFAULT 0");

        Cursor measurementCursor = null;
        Cursor geoLocationCursor = null;
        LocationCleaningStrategy locationCleaningStrategy = new DefaultLocationCleaningStrategy();
        try {
            measurementCursor = database.query("measurements", new String[] {"_id"}, null, null, null, null, null,
                    null);
            if (measurementCursor.getCount() == 0) {
                Log.v(TAG, "No measurements for migration found");
                return;
            }

            // Check all measurements
            while (measurementCursor.moveToNext()) {
                final int identifierColumnIndex = measurementCursor.getColumnIndex("_id");
                final long measurementId = measurementCursor.getLong(identifierColumnIndex);

                geoLocationCursor = database.query("locations",
                        new String[] {"lat", "lon", "gps_time", "speed", "accuracy"}, "measurement_fk = ?",
                        new String[] {String.valueOf(measurementId)}, null, null, "gps_time ASC", null);
                if (geoLocationCursor.getCount() < 1) {
                    Log.v(TAG, "Not enough geoLocations to update average speed in measurement entry:" + measurementId);
                    continue;
                }

                // Calculate average speed for selected measurement
                double speedSum = 0.0;
                int speedCounter = 0;
                while (geoLocationCursor.moveToNext()) {
                    final int latColumnIndex = geoLocationCursor.getColumnIndex("lat");
                    final int lonColumnIndex = geoLocationCursor.getColumnIndex("lon");
                    final int timeColumnIndex = geoLocationCursor.getColumnIndex("gps_time");
                    final int speedColumnIndex = geoLocationCursor.getColumnIndex("speed");
                    final int accuracyColumnIndex = geoLocationCursor.getColumnIndex("accuracy");
                    final double lat = geoLocationCursor.getDouble(latColumnIndex);
                    final double lon = geoLocationCursor.getDouble(lonColumnIndex);
                    final long time = geoLocationCursor.getLong(timeColumnIndex);
                    final double speed = geoLocationCursor.getDouble(speedColumnIndex);
                    final float accuracy = geoLocationCursor.getFloat(accuracyColumnIndex);
                    final GeoLocation geoLocation = new GeoLocation(lat, lon, time, speed, accuracy);

                    if (locationCleaningStrategy.isClean(geoLocation)) {
                        speedSum += speed;
                        speedCounter += 1;
                    }
                }

                if (speedCounter > 0) {
                    final double averageSpeed = speedSum / (double) speedCounter;
                    Log.v(TAG, String.format("Updating average speed for measurement %d to %f (%f/%d)", measurementId,
                            averageSpeed, speedSum, speedCounter));
                    database.execSQL("UPDATE measurements SET speed_sum = " + speedSum + ", speed_counter = " + speedCounter
                            + " WHERE _id = " + measurementId);
                }
            }

        } finally {
            if (measurementCursor != null) {
                measurementCursor.close();
            }
            if (geoLocationCursor != null) {
                geoLocationCursor.close();
            }
        }
    }

    /**
     * Adds timestamp columns to table.
     *
     * @param database The {@code SQLiteDatabase} to upgrade
     */
    private void migrateDatabaseFromV15(@NonNull final SQLiteDatabase database) {
        // To rename columns we need to copy the table.
        database.execSQL("ALTER TABLE measurements RENAME TO _measurements_old;");

        // Create the new table schema with the renamed column
        database.execSQL("CREATE TABLE measurements (_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "status TEXT NOT NULL, modality TEXT NOT NULL, file_format_version INTEGER NOT NULL, "
                + "distance REAL NOT NULL, timestamp INTEGER NOT NULL);");

        // Insert the old data into the new column
        database.execSQL("INSERT INTO measurements "
                + "(_id,status,modality,file_format_version,distance,timestamp) "
                + "SELECT _id,status,vehicle,file_format_version,distance,timestamp "
                + "FROM _measurements_old");

        // Remove temp table
        database.execSQL("DROP TABLE _measurements_old;");
    }

    /**
     * Adds timestamp columns to table.
     *
     * @param database The {@code SQLiteDatabase} to upgrade
     */
    private void migrateDatabaseFromV13(@NonNull final SQLiteDatabase database) {

        database.execSQL("ALTER TABLE measurements ADD COLUMN timestamp INTEGER NOT NULL DEFAULT 0");
    }

    /**
     * Calculates and updates the timestamp for {@link Measurement}s migrated from V13.
     *
     * @param database The {@code SQLiteDatabase} to upgrade
     */
    private void updateMeasurementTimestampForV13Measurements(@NonNull final SQLiteDatabase database) {
        Cursor measurementCursor = null;
        Cursor geoLocationCursor = null;
        try {
            measurementCursor = database.query("measurements", new String[] {"_id"}, null, null, null, null, null,
                    null);
            if (measurementCursor.getCount() == 0) {
                Log.v(TAG, "No measurements for migration found");
                return;
            }

            // Check all measurements
            while (measurementCursor.moveToNext()) {
                final int identifierColumnIndex = measurementCursor.getColumnIndex("_id");
                final long measurementId = measurementCursor.getLong(identifierColumnIndex);

                geoLocationCursor = database.query("locations",
                        new String[] {"gps_time"}, "measurement_fk = ?",
                        new String[] {String.valueOf(measurementId)}, null, null, "gps_time ASC", "1");

                long timestamp = 0L; // Default value for measurements without GeoLocations
                if (geoLocationCursor.moveToNext()) {
                    final int timeColumnIndex = geoLocationCursor.getColumnIndex("gps_time");
                    timestamp = geoLocationCursor.getLong(timeColumnIndex);
                }
                Validate.isTrue(timestamp >= 0L);

                Log.v(TAG, "Updating timestamp for measurement " + measurementId + " to " + timestamp);
                database.execSQL("UPDATE measurements SET timestamp = " + timestamp + " WHERE _id = " + measurementId);
            }

        } finally {
            if (measurementCursor != null) {
                measurementCursor.close();
            }
            if (geoLocationCursor != null) {
                geoLocationCursor.close();
            }
        }
    }

    /**
     * Removes sensor point counter columns from table.
     *
     * @param database The {@code SQLiteDatabase} to upgrade
     */
    private void migrateDatabaseFromV12(@NonNull final SQLiteDatabase database) {

        // To drop columns we need to copy the table.
        database.execSQL("ALTER TABLE measurements RENAME TO _measurements_old;");

        // To drop columns "accelerations", "rotations" and "directions" we need to create a new table
        database.execSQL("CREATE TABLE measurements (_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "status TEXT NOT NULL, vehicle TEXT NOT NULL, file_format_version INTEGER NOT NULL, "
                + "distance REAL NOT NULL);");
        // and insert the old data accordingly
        database.execSQL("INSERT INTO measurements " + "(_id,status,vehicle,file_format_version,distance) "
                + "SELECT _id,status,vehicle,file_format_version,distance " + "FROM _measurements_old");

        // Remove temp table
        database.execSQL("DROP TABLE _measurements_old;");
    }

    /**
     * Calculates and updates the distance for {@link Measurement}s migrated from V8 which have 0.0 as distance.
     *
     * @param database The {@code SQLiteDatabase} to upgrade
     */
    private void updateDistanceForV8Measurements(@NonNull final SQLiteDatabase database) {
        Cursor measurementCursor = null;
        Cursor geoLocationCursor = null;
        try {
            measurementCursor = database.query("measurements", new String[] {"_id"}, null, null, null, null, null,
                    null);
            if (measurementCursor.getCount() == 0) {
                Log.v(TAG, "No measurements for migration found");
                return;
            }

            // Check all measurements
            while (measurementCursor.moveToNext()) {
                final int identifierColumnIndex = measurementCursor.getColumnIndex("_id");
                final long measurementId = measurementCursor.getLong(identifierColumnIndex);

                geoLocationCursor = database.query("locations",
                        new String[] {"lat", "lon", "gps_time", "speed", "accuracy"}, "measurement_fk = ?",
                        new String[] {String.valueOf(measurementId)}, null, null, "gps_time ASC", null);
                if (geoLocationCursor.getCount() < 2) {
                    Log.v(TAG, "Not enough geoLocations to update distance in measurement entry:" + measurementId);
                    continue;
                }

                // Calculate distance for selected measurement
                final DistanceCalculationStrategy distanceCalculationStrategy = new DefaultDistanceCalculationStrategy();
                double distance = 0.0;
                GeoLocation previousLocation = null;
                while (geoLocationCursor.moveToNext()) {
                    final int latColumnIndex = geoLocationCursor.getColumnIndex("lat");
                    final int lonColumnIndex = geoLocationCursor.getColumnIndex("lon");
                    final int timeColumnIndex = geoLocationCursor.getColumnIndex("gps_time");
                    final int speedColumnIndex = geoLocationCursor.getColumnIndex("speed");
                    final int accuracyColumnIndex = geoLocationCursor.getColumnIndex("accuracy");
                    final double lat = geoLocationCursor.getFloat(latColumnIndex);
                    final double lon = geoLocationCursor.getFloat(lonColumnIndex);
                    final long time = geoLocationCursor.getLong(timeColumnIndex);
                    final float speed = geoLocationCursor.getFloat(speedColumnIndex);
                    final int accuracy = geoLocationCursor.getInt(accuracyColumnIndex);
                    final GeoLocation geoLocation = new GeoLocation(lat, lon, time, speed, accuracy);

                    // We cannot calculate a distance from just one geoLocation:
                    if (previousLocation == null) {
                        previousLocation = geoLocation;
                        continue;
                    }

                    // Calculate distance between last two locations
                    final double newDistance = distanceCalculationStrategy.calculateDistance(previousLocation,
                            geoLocation);
                    Validate.isTrue(newDistance >= 0);

                    distance += newDistance;
                    previousLocation = geoLocation;
                }

                Log.v(TAG, "Updating distance for measurement " + measurementId + " to " + distance);
                database.execSQL("UPDATE measurements SET distance = " + distance + " WHERE _id = " + measurementId);
            }

        } finally {
            if (measurementCursor != null) {
                measurementCursor.close();
            }
            if (geoLocationCursor != null) {
                geoLocationCursor.close();
            }
        }
    }

    /**
     * Renames table, updates the table structure and copies the data.
     * <p>
     * The distance column is set to 0.0 as it's calculated in the next migration to ensure both
     * {@link GeoLocationsTable} and {@link MeasurementTable} is already upgraded.
     *
     * @param database The {@code SQLiteDatabase} to upgrade
     */
    private void migrateDatabaseFromV8(@NonNull final SQLiteDatabase database) {
        // To drop columns we need to copy the table. We anyway renamed the table to measurement*s*.
        database.execSQL("ALTER TABLE measurement RENAME TO _measurements_old;");

        // Due to a bug in the code of V8 MeasurementTable we may need to create the sync column
        /*
         * This should never be the case for STAD-2019
         * try {
         * database.execSQL("ALTER TABLE _measurements_old ADD COLUMN synced INTEGER NOT NULL DEFAULT 0");
         * } catch (final SQLiteException ex) {
         * Log.w(TAG, "Altering measurements: " + ex.getMessage());
         * }
         */

        // Columns "accelerations", "rotations", and "directions" were added
        // We don't support a data preserving upgrade for sensor data stored in the database
        // Thus, the data is deleted in DatabaseHelper#onUpgrade and the counters are set to 0.
        database.execSQL("ALTER TABLE _measurements_old ADD COLUMN accelerations INTEGER NOT NULL DEFAULT 0");
        database.execSQL("ALTER TABLE _measurements_old ADD COLUMN rotations INTEGER NOT NULL DEFAULT 0");
        database.execSQL("ALTER TABLE _measurements_old ADD COLUMN directions INTEGER NOT NULL DEFAULT 0");
        // For the same reason we can just set the file_format_version to 1 (first supported version)
        database.execSQL("ALTER TABLE _measurements_old ADD COLUMN file_format_version INTEGER NOT NULL DEFAULT 1");

        // Distance column was added. We calculate the distance for the migrated data in onUpgrade(9, 10)
        database.execSQL("ALTER TABLE _measurements_old ADD COLUMN distance REAL NOT NULL DEFAULT 0.0;");

        // Columns "finished" and "synced" are now in the "status" column
        // To migrate old measurements we need to set a default which is then adjusted
        database.execSQL("ALTER TABLE _measurements_old ADD COLUMN status TEXT NOT NULL DEFAULT 'MIGRATION'");
        database.execSQL("UPDATE _measurements_old SET status = 'OPEN' WHERE finished = 0 AND synced = 0");
        database.execSQL("UPDATE _measurements_old SET status = 'FINISHED' WHERE finished = 1 AND synced = 0");
        database.execSQL("UPDATE _measurements_old SET status = 'SYNCED' WHERE finished = 1 AND synced = 1");

        // To drop columns "finished" and "synced" we need to create a new table
        database.execSQL("CREATE TABLE measurements (_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "status TEXT NOT NULL, vehicle TEXT NOT NULL, accelerations INTEGER NOT NULL, "
                + "rotations INTEGER NOT NULL, directions INTEGER NOT NULL, file_format_version INTEGER NOT NULL, "
                + "distance REAL NOT NULL);");
        // and insert the old data accordingly. This is anyway cleaner (no defaults)
        database.execSQL("INSERT INTO measurements "
                + "(_id,status,vehicle,accelerations,rotations,directions,file_format_version,distance) "
                + "SELECT _id,status,vehicle,accelerations,rotations,directions,file_format_version,distance "
                + "FROM _measurements_old");

        // Remove temp table
        database.execSQL("DROP TABLE _measurements_old;");
    }

    @Override
    protected String[] getDatabaseTableColumns() {
        return COLUMNS;
    }
}
