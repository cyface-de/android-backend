/*
 * Copyright 2023-2025 Cyface GmbH
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
package de.cyface.persistence

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.location.Location
import android.preference.PreferenceManager
import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQueryBuilder
import de.cyface.persistence.content.LocationTable
import de.cyface.persistence.content.MeasurementTable
import de.cyface.persistence.strategy.DefaultDistanceCalculation
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * This class wraps the [Database] migration code.
 *
 * Don't forget to update the [Database]'s version if you update this class.
 *
 * The upgrades are automatically executed in a transaction by [androidx.room.migration.Migration.migrate],
 * do not wrap the code in another transaction!
 *
 * The upgrades are called incrementally, but if you provide shortcuts, they prioritized:
 * - MIGRATION_1_3 is prioritized vs MIGRATION_1_2 and MIGRATION 2_3
 *
 * If there are not enough migrations provided to move from the current version to the latest version,
 * Room will clear the database and recreate so even if you have no changes between 2 versions, *always
 * provide a Migration object to the builder*!
 *
 * @author Armin Schnabel
 * @version 1.2.0
 * @since 7.5.0
 * @property context The `Context` required to import data from a secondary data source.
 */
class DatabaseMigrator(val context: Context) {
    /**
     * The migration to migrate from database 17 which was the last version before using `Room`-generated
     * tables and before adding `ForeignKey` constraints and an index on the `measurementId`.
     *
     * This migration step is special as it checks for `altitude` data which we stored for a short time
     * in a second database with the name `v6`.
     *
     * If the second database `v6` exists, the migrations code first checks if the `v6` database
     * contains a copy of the locations for a measurement, in that case these locations are migrated as they
     * are a copy of the `measures.locations` but are annotated with `altitude` and `verticalAccuracy`.
     * For measurements which were collected before `v6` database existed / altitude data was collected the
     * locations from `measures.locations` without altitude data is migrated. Additionally,
     * if `v6` exists, the `v6.Pressure` data is imported into `measures.Pressure`.
     *
     * The second database `v6` was added in both, `6.3.0` and `7.4.0` at the same time as both SDK API
     * versions were in use at the same time by different apps.
     *
     * At the time of `7.4.0` the database version on the SDK 7 branch was `17` and the `v6` database
     * version was `1`. When this migration code is written, the SDK 6 was at `6.3.0` and was at database
     * version `16` and `v6` database version `1`. But the app which still uses SDK 6 migrates at a later
     * time (~ End of 2023), so it's still possible that SDK 6 will increase the `v6` version before migrating
     * to SDK 7. *If this is the case we need to adjust this migration code to support other `v6` versions, too.*
     */
    @Suppress("PropertyName")
    val MIGRATION_17_18: Migration = migrationFrom17To18()

    /**
     * Version `9` was never released so we use this to make sure the [MeasurementTable] and [LocationTable]
     * already migrated from version `8`.
     *
     * In this migration step we calculate the correct `distance` for the migrated data.
     */
    @Suppress("PropertyName")
    val MIGRATION_9_10: Migration = migrationFrom9To10()

    companion object {
        /**
         * Adds the [de.cyface.persistence.model.Measurement.filesSize] column.
         */
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add the new filesSize column with default value 0 for existing rows
                // This should be fine as we don't upload attachments in production, yet.
                database.execSQL("ALTER TABLE Measurement ADD COLUMN filesSize INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Adds the [de.cyface.persistence.model.Attachment] table.
         */
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `Attachment` (" +
                        "`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, " +
                        "`status` TEXT NOT NULL, `type` TEXT NOT NULL, `fileFormatVersion` INTEGER NOT NULL, " +
                        "`size` INTEGER NOT NULL, `path` TEXT NOT NULL, " +
                        "`lat` REAL, `lon` REAL, `locationTimestamp` INTEGER, `measurementId` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`measurementId`) REFERENCES `Measurement`(`_id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_Attachment_measurementId` " +
                        "ON `Attachment` (`measurementId`)")
            }
        }

        //val MIGRATION_17_18 is provided from outside the companion object constructor!

        /**
         * Upgrades the `locations` `accuracy` column's type and values.
         */
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrate16To17Locations(db)
            }

            /**
             * Changes the `accuracy` from `INTEGER` to `REAL` and updates the values from unit `cm` to `m`.
             *
             * @param database The database to upgrade.
             */
            private fun migrate16To17Locations(database: SupportSQLiteDatabase) {
                // To alter columns we copy the table.
                database.execSQL("ALTER TABLE locations RENAME TO _locations_old;")

                // Create new table with the changed `accuracy` column type
                database.execSQL(
                    "CREATE TABLE locations (_id INTEGER PRIMARY KEY AUTOINCREMENT, gps_time INTEGER NOT NULL, "
                            + "lat REAL NOT NULL, lon REAL NOT NULL, speed REAL NOT NULL, accuracy REAL NOT NULL, "
                            + "measurement_fk INTEGER NOT NULL);"
                )

                // Insert the old data. Attention: accuracy 100cm is now 100.0m, see next step
                database.execSQL(
                    "INSERT INTO locations " + "(_id,gps_time,lat,lon,speed,accuracy,measurement_fk) "
                            + "SELECT _id,gps_time,lat,lon,speed,CAST(accuracy as REAL)/100,measurement_fk "
                            + "FROM _locations_old"
                )

                // Remove temp table
                database.execSQL("DROP TABLE _locations_old;")
            }
        }

        /**
         * Renames the `vehicle` column to `modality` in the `measurements` table.
         */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrate15To16Measurements(db)
            }

            /**
             * Renames the `vehicle` column to `modality` in the `measurements` table.
             *
             * @param database The database to upgrade
             */
            private fun migrate15To16Measurements(database: SupportSQLiteDatabase) {
                // To rename columns we need to copy the table.
                database.execSQL("ALTER TABLE measurements RENAME TO _measurements_old;")

                // Create the new table schema with the renamed column
                database.execSQL(
                    "CREATE TABLE measurements (_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                            + "status TEXT NOT NULL, modality TEXT NOT NULL, file_format_version INTEGER NOT NULL, "
                            + "distance REAL NOT NULL, timestamp INTEGER NOT NULL);"
                )

                // Insert the old data into the new column
                database.execSQL(
                    "INSERT INTO measurements "
                            + "(_id,status,modality,file_format_version,distance,timestamp) "
                            + "SELECT _id,status,vehicle,file_format_version,distance,timestamp "
                            + "FROM _measurements_old"
                )

                // Remove temp table
                database.execSQL("DROP TABLE _measurements_old;")
            }
        }

        /**
         * Adds the `value` column to the `events` table.
         */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Migrate Events: The `value` column was added in version `15`
                db.execSQL("ALTER TABLE events ADD COLUMN value TEXT;")
            }
        }

        /**
         * Upgrades the measurement table from `13` to `14` with a recalculated `timestamp`.
         */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrate13To14Measurements(db)
            }

            /**
             * Upgrades the measurement table from `13` to `14` with a recalculated `timestamp`.
             *
             * @param database The database to upgrade
             */
            private fun migrate13To14Measurements(database: SupportSQLiteDatabase) {
                // Add `timestamp` column
                database.execSQL("ALTER TABLE measurements ADD COLUMN timestamp INTEGER NOT NULL DEFAULT 0")
                // measurements from version `< 14` without GeoLocations will receive an `0L` timestamp
                // Calculating timestamp for migrated version `13` measurements
                var measurementCursor: Cursor? = null
                var geoLocationCursor: Cursor? = null
                try {
                    measurementCursor = database.query(
                        SupportSQLiteQueryBuilder.builder("measurements").columns(arrayOf("_id"))
                            .create()
                    )
                    if (measurementCursor.count == 0) {
                        Log.v(Constants.TAG, "No measurements for migration found")
                        return
                    }

                    // Check all measurements
                    while (measurementCursor.moveToNext()) {
                        val measurementId =
                            measurementCursor.getLong(measurementCursor.getColumnIndexOrThrow("_id"))
                        geoLocationCursor = database.query(
                            SupportSQLiteQueryBuilder
                                .builder("locations")
                                .selection("measurement_fk = ?", arrayOf(measurementId))
                                .columns(arrayOf("gps_time"))
                                .orderBy("gps_time ASC")
                                .limit("1")
                                .create()
                        )
                        var timestamp = 0L // Default value for measurements without GeoLocations
                        if (geoLocationCursor.moveToNext()) {
                            timestamp =
                                geoLocationCursor.getLong(geoLocationCursor.getColumnIndexOrThrow("gps_time"))
                        }
                        require(timestamp >= 0L)
                        Log.v(
                            Constants.TAG,
                            "Updating timestamp for measurement $measurementId to $timestamp"
                        )
                        database.execSQL("UPDATE measurements SET timestamp = $timestamp WHERE _id = $measurementId")
                    }
                } finally {
                    measurementCursor?.close()
                    geoLocationCursor?.close()
                }
            }
        }

        /**
         * Removing sensor point counter columns from measurement table.
         */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrate12To13Measurements(db)
            }

            /**
             * Removing sensor point counter columns from measurement table.
             *
             * @param database The database to upgrade
             */
            private fun migrate12To13Measurements(database: SupportSQLiteDatabase) {
                // To drop columns we need to copy the table.
                database.execSQL("ALTER TABLE measurements RENAME TO _measurements_old;")

                // To drop columns "accelerations", "rotations" and "directions" we need to create a new table
                database.execSQL(
                    "CREATE TABLE measurements (_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                            + "status TEXT NOT NULL, vehicle TEXT NOT NULL, file_format_version INTEGER NOT NULL, "
                            + "distance REAL NOT NULL);"
                )
                // and insert the old data accordingly
                database.execSQL(
                    "INSERT INTO measurements (_id,status,vehicle,file_format_version,distance) "
                            + "SELECT _id,status,vehicle,file_format_version,distance FROM _measurements_old"
                )

                // Remove temp table
                database.execSQL("DROP TABLE _measurements_old;")
            }
        }

        /**
         * Adds the `events` table which was introduced in version `12` (SDK 4.0.0).
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Migrate Events: create table in the schema at the time of version `12`.
                db.execSQL(
                    "CREATE TABLE events (_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                            + "timestamp INTEGER NOT NULL, type TEXT NOT NULL, measurement_fk INTEGER);"
                )
            }
        }

        /**
         * No migration steps required between version `10` and `11` anymore, but we need to provide
         * an empty migration object to `Room` or it will clear and recreate the database.
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {}
        }

        //val MIGRATION_9_10 = is provided from outside the companion object constructor!

        /**
         * From `7.4.0`.`DatabaseHelper.onUpgrade`.
         *
         * Version `8` was the last version in SDK 2 (SDK 3 was release in Feb'2019).
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrate8To9SensorPoints(db)
                migrate8To9Measurements(db)
                migrate8To9Locations(db)
            }

            /**
             * Removes the tables where we stored the sensor data until version `8`.
             *
             * The senor data is stored in binary files since version `9` (SDK 3.0.0)
             *
             * @param database The database to upgrade
             */
            private fun migrate8To9SensorPoints(database: SupportSQLiteDatabase) {
                database.execSQL("DELETE FROM sample_points;")
                database.execSQL("DROP TABLE sample_points;")
                database.execSQL("DELETE FROM rotation_points;")
                database.execSQL("DROP TABLE rotation_points;")
                database.execSQL("DELETE FROM magnetic_value_points;")
                database.execSQL("DROP TABLE magnetic_value_points;")
            }

            /**
             * Renames table, updates the table structure and copies the data.
             *
             * The distance column is set to `0.0` as it's calculated in the next migration to ensure both
             * [LocationTable] and [MeasurementTable] is already upgraded.
             *
             * @param database The database to upgrade
             */
            private fun migrate8To9Measurements(database: SupportSQLiteDatabase) {
                // To drop columns we need to copy the table. We anyway renamed the table to measurement*s*.
                database.execSQL("ALTER TABLE measurement RENAME TO _measurements_old;")

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
                database.execSQL("ALTER TABLE _measurements_old ADD COLUMN accelerations INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE _measurements_old ADD COLUMN rotations INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE _measurements_old ADD COLUMN directions INTEGER NOT NULL DEFAULT 0")
                // For the same reason we can just set the file_format_version to 1 (first supported version)
                database.execSQL("ALTER TABLE _measurements_old ADD COLUMN file_format_version " +
                        "INTEGER NOT NULL DEFAULT 1")

                // Distance column was added. We calculate the distance for the migrated data in onUpgrade(9, 10)
                database.execSQL("ALTER TABLE _measurements_old ADD COLUMN distance REAL NOT NULL DEFAULT 0.0;")

                // Columns "finished" and "synced" are now in the "status" column
                // To migrate old measurements we need to set a default which is then adjusted
                database.execSQL("ALTER TABLE _measurements_old ADD COLUMN status TEXT NOT NULL DEFAULT 'MIGRATION'")
                database.execSQL("UPDATE _measurements_old SET status = 'OPEN' WHERE finished = 0 AND synced = 0")
                database.execSQL("UPDATE _measurements_old SET status = 'FINISHED' WHERE finished = 1 AND synced = 0")
                database.execSQL("UPDATE _measurements_old SET status = 'SYNCED' WHERE finished = 1 AND synced = 1")

                // To drop columns "finished" and "synced" we need to create a new table
                database.execSQL(
                    "CREATE TABLE measurements (_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                            + "status TEXT NOT NULL, vehicle TEXT NOT NULL, accelerations INTEGER NOT NULL, "
                            + "rotations INTEGER NOT NULL, directions INTEGER NOT NULL, file_format_version " +
                            "INTEGER NOT NULL, distance REAL NOT NULL);"
                )
                // and insert the old data accordingly. This is anyway cleaner (no defaults)
                database.execSQL(
                    "INSERT INTO measurements "
                            + "(_id,status,vehicle,accelerations,rotations,directions,file_format_version,distance) "
                            + "SELECT _id,status,vehicle,accelerations,rotations,directions," +
                            "file_format_version,distance "
                            + "FROM _measurements_old"
                )

                // Remove temp table
                database.execSQL("DROP TABLE _measurements_old;")
            }

            /**
             * Renames table, updates the table structure and copies the data.
             *
             * @param database The database to upgrade
             */
            private fun migrate8To9Locations(database: SupportSQLiteDatabase) {
                // To drop columns we need to copy the table. We anyway renamed the table to locations.
                database.execSQL("ALTER TABLE gps_points RENAME TO _locations_old;")

                // To drop columns "is_synced" we need to create a new table
                database.execSQL(
                    "CREATE TABLE locations (_id INTEGER PRIMARY KEY AUTOINCREMENT, gps_time INTEGER NOT NULL, "
                            + "lat REAL NOT NULL, lon REAL NOT NULL, speed REAL NOT NULL, accuracy INTEGER NOT NULL, "
                            + "measurement_fk INTEGER NOT NULL);"
                )
                // and insert the old data accordingly. This is anyway cleaner (no defaults)
                // We ignore the value as we upload to a new API.
                database.execSQL(
                    "INSERT INTO locations " + "(_id,gps_time,lat,lon,speed,accuracy,measurement_fk) "
                            + "SELECT _id,gps_time,lat,lon,speed,accuracy,measurement_fk " + "FROM _locations_old"
                )

                // Remove temp table
                database.execSQL("DROP TABLE _locations_old;")
            }
        }
    }

    /**
     * This cannot be a companion object as this requires the [context].
     *
     * @see [MIGRATION_17_18]
     */
    private fun migrationFrom17To18(): Migration {
        return object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Migrate Identifier data
                // Create table with Room generated name and new schema
                db.execSQL("CREATE TABLE IF NOT EXISTS `Identifier` " +
                        "(`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `deviceId` TEXT NOT NULL);")
                // Insert the data from old table
                db.execSQL("INSERT INTO `Identifier` (`_id`, `deviceId`) " +
                        "SELECT `_id`, `device_id` FROM `identifiers`;")
                // Drop the old table
                db.execSQL("DROP TABLE `identifiers`;")

                // Migrate Measurement data
                // Create table with Room generated name and new schema
                db.execSQL("CREATE TABLE IF NOT EXISTS `Measurement` " +
                        "(`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `status` TEXT NOT NULL, " +
                        "`modality` TEXT NOT NULL, `fileFormatVersion` INTEGER NOT NULL, " +
                        "`distance` REAL NOT NULL, `timestamp` INTEGER NOT NULL);")
                // Insert the data from old table
                db.execSQL(
                    "INSERT INTO `Measurement` (`_id`, `status`, `modality`, `fileFormatVersion`, " +
                            "`distance`, `timestamp`)"
                            + " SELECT `_id`, `status`, `modality`, `file_format_version`, `distance`, " +
                            "`timestamp` FROM `measurements`;"
                )
                // Drop the old table
                db.execSQL("DROP TABLE `measurements`;")

                // Migrate Event data
                // Create table with Room generated name and new schema
                db.execSQL("CREATE TABLE IF NOT EXISTS `Event` " +
                        "(`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, `type` TEXT NOT NULL, `value` TEXT, " +
                        "`measurementId` INTEGER NOT NULL, FOREIGN KEY(`measurementId`) " +
                        "REFERENCES `Measurement`(`_id`) ON UPDATE NO ACTION ON DELETE CASCADE );")
                // Insert the data from old table
                db.execSQL(
                    "INSERT INTO `Event` (`_id`, `timestamp`, `type`, `value`, `measurementId`)"
                            + " SELECT `_id`, `timestamp`, `type`, `value`, `measurement_fk` FROM `events`;"
                )
                // Create index
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_Event_measurementId` ON `Event` (`measurementId`);")
                // Drop the old table
                db.execSQL("DROP TABLE `events`;")

                // Check if database `v6` exists
                val v6DatabaseName = "v6"
                val v6File = context.getDatabasePath(v6DatabaseName)
                if (v6File.exists()) {
                    val version = sqLiteDatabaseVersion(v6File)
                    require(version == 1) { "Unsupported version $version" }
                    migrate17To18LocationsAndPressuresWithV6Data(db, v6File, v6DatabaseName)
                } else {
                    migrate17To18LocationsAndPressuresWithoutV6Data(db)
                }
            }

            /**
             * When `v6` database exists, we first check which measurements have locations in `v6`
             * and choose these locations before the locations from `measures` as the earlier are a
             * copy of the later but contain the `altitude` and `verticalAccuracy` data.
             *
             * Instead of loading the `v6` data via Room, DAOs and model.v6, we load the data via a
             * SQLite `Cursor` so we don't have to keep the `v6` code after migration. We add `ForeignKey`
             * constraints in the new database schema, so constrains are checked during insert.
             *
             * @param database The database to upgrade
             */
            private fun migrate17To18LocationsAndPressuresWithV6Data(
                database: SupportSQLiteDatabase,
                v6DatabaseFile: File,
                @Suppress("SameParameterValue") v6DatabaseName: String
            ) {
                val v6Database: SQLiteDatabase?
                try {
                    v6Database = SQLiteDatabase.openDatabase(
                        v6DatabaseFile.path,
                        null,
                        SQLiteDatabase.OPEN_READONLY
                    )
                } catch (e: RuntimeException) {
                    throw IllegalStateException("Unable to open database at ${v6DatabaseFile.path}", e)
                }

                // Migrate GeoLocation data
                // Create table with Room generated name and new schema
                database.execSQL("CREATE TABLE IF NOT EXISTS `Location` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `lat` REAL NOT NULL, `lon` REAL NOT NULL, `altitude` REAL, `speed` REAL NOT NULL, `accuracy` REAL, `verticalAccuracy` REAL, `measurementId` INTEGER NOT NULL, FOREIGN KEY(`measurementId`) REFERENCES `Measurement`(`_id`) ON UPDATE NO ACTION ON DELETE CASCADE );")
                // Load all measurements to see which also have locations in `v6` and which only have locations in `measures`
                val measurementCursor = database.query(
                    SupportSQLiteQueryBuilder.builder("Measurement").orderBy("_id ASC").create()
                )
                while (measurementCursor.moveToNext()) {
                    val measurementId =
                        measurementCursor.getLong(measurementCursor.getColumnIndexOrThrow("_id"))
                    // Check if secondary `v6` database contains locations for that measurement
                    val locationCursor = v6Database.query(
                        "Location",
                        null,
                        "measurement_fk = ?",
                        arrayOf(measurementId.toString()),
                        null,
                        null,
                        "uid ASC"
                    )
                    if (locationCursor.count > 0) {
                        // Insert the data from the `v6` database version `1`
                        while (locationCursor.moveToNext()) {
                            val id =
                                locationCursor.getInt(locationCursor.getColumnIndexOrThrow("uid"))
                            val timestamp =
                                locationCursor.getLong(locationCursor.getColumnIndexOrThrow("timestamp"))
                            val lat =
                                locationCursor.getDouble(locationCursor.getColumnIndexOrThrow("lat"))
                            val lon =
                                locationCursor.getDouble(locationCursor.getColumnIndexOrThrow("lon"))
                            val altitude =
                                locationCursor.getDouble(locationCursor.getColumnIndexOrThrow("altitude"))
                            val speed =
                                locationCursor.getDouble(locationCursor.getColumnIndexOrThrow("speed"))
                            val accuracyCm =
                                locationCursor.getDouble(locationCursor.getColumnIndexOrThrow("accuracy"))
                            // Convert accuracy from cm to meters
                            val accuracy = accuracyCm / 100.0
                            val verticalAccuracy =
                                locationCursor.getDouble(locationCursor.getColumnIndexOrThrow("vertical_accuracy"))
                            val locationMeasurementId =
                                locationCursor.getInt(locationCursor.getColumnIndexOrThrow("measurement_fk"))
                            // v6.1 `altitude` and `verticalAccuracy` are already in the same format
                            database.execSQL(
                                "INSERT INTO `Location` (`_id`, `timestamp`, `lat`, `lon`, `altitude`, `speed`, `accuracy`, `verticalAccuracy`, `measurementId`)"
                                        + " VALUES ('" + id + "', '" + timestamp + "', '" + lat + "', '" + lon + "', '" + altitude + "', '" + speed + "', '" + accuracy + "', '" + verticalAccuracy + "', '" + locationMeasurementId + "');"
                            )
                        }
                    }
                    // Secondary `v6` database contains no locations for that measurement, load from main database
                    else {
                        // Insert the data from old table
                        /// `accuracy` in `measures` version `17` is already in meters
                        database.execSQL(
                            "INSERT INTO `Location` (`_id`, `timestamp`, `lat`, `lon`, `altitude`, `speed`, `accuracy`, `verticalAccuracy`, `measurementId`)"
                                    + " SELECT `_id`, `gps_time`, `lat`, `lon`, null, `speed`, `accuracy`, null, `measurement_fk` FROM `locations`"
                                    + " WHERE measurement_fk = $measurementId;"
                        )
                    }
                    locationCursor.close()
                }
                /// Set `accuracy` values with `0` m to `null`
                database.execSQL("UPDATE `Location` SET `accuracy` = null WHERE `accuracy` = 0;")
                // Create index
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_Location_measurementId` ON `Location` (`measurementId`);")
                // Drop the old `measures` table for locations, as we already imported `v6.Location`
                database.execSQL("DROP TABLE `locations`;")

                // Migrate Pressure data
                // Create table with Room generated name and new schema
                database.execSQL("CREATE TABLE IF NOT EXISTS `Pressure` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `pressure` REAL NOT NULL, `measurementId` INTEGER NOT NULL, FOREIGN KEY(`measurementId`) REFERENCES `Measurement`(`_id`) ON UPDATE NO ACTION ON DELETE CASCADE );")
                // Insert the data from the `v6` database version `1`
                val pressureCursor =
                    v6Database.query("Pressure", null, null, null, null, null, "uid ASC")
                while (pressureCursor.moveToNext()) {
                    val id = pressureCursor.getInt(pressureCursor.getColumnIndexOrThrow("uid"))
                    val timestamp =
                        pressureCursor.getLong(pressureCursor.getColumnIndexOrThrow("timestamp"))
                    val pressure =
                        pressureCursor.getDouble(pressureCursor.getColumnIndexOrThrow("pressure"))
                    val measurementId =
                        pressureCursor.getInt(pressureCursor.getColumnIndexOrThrow("measurement_fk"))
                    // v6.1 `altitude` and `verticalAccuracy` are already in the same format
                    database.execSQL(
                        "INSERT INTO `Pressure` (`_id`, `timestamp`, `pressure`, `measurementId`)"
                                + " VALUES ('" + id + "', '" + timestamp + "', '" + pressure + "', '" + measurementId + "');"
                    )
                }
                pressureCursor.close()
                // Create index
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_Pressure_measurementId` ON `Pressure` (`measurementId`);")

                // Drop the old database `v6`
                v6Database.close()
                context.deleteDatabase(v6DatabaseName)
            }

            /**
             * When `v6` database does not exist, we only migrate the locations and pressures from `measures` version `17`.
             *
             * @param database The database to upgrade
             */
            private fun migrate17To18LocationsAndPressuresWithoutV6Data(database: SupportSQLiteDatabase) {
                // Migrate GeoLocation data
                // Create table with Room generated name and new schema
                database.execSQL("CREATE TABLE IF NOT EXISTS `Location` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `lat` REAL NOT NULL, `lon` REAL NOT NULL, `altitude` REAL, `speed` REAL NOT NULL, `accuracy` REAL, `verticalAccuracy` REAL, `measurementId` INTEGER NOT NULL, FOREIGN KEY(`measurementId`) REFERENCES `Measurement`(`_id`) ON UPDATE NO ACTION ON DELETE CASCADE );")
                // Insert the data from old table
                /// `accuracy` in `measures` version `17` is already in meters
                database.execSQL(
                    "INSERT INTO `Location` (`_id`, `timestamp`, `lat`, `lon`, `altitude`, `speed`, `accuracy`, `verticalAccuracy`, `measurementId`)"
                            + " SELECT `_id`, `gps_time`, `lat`, `lon`, null, `speed`, `accuracy`, null, `measurement_fk` FROM `locations`;"
                )
                /// Set `accuracy` values with `0` m to `null`
                database.execSQL("UPDATE `Location` SET `accuracy` = null WHERE `accuracy` = 0;")
                // Create index
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_Location_measurementId` ON `Location` (`measurementId`);")
                // Drop the old table
                database.execSQL("DROP TABLE `locations`;")

                // Migrate Pressure data
                // Create table with Room generated name and new schema
                database.execSQL("CREATE TABLE IF NOT EXISTS `Pressure` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `pressure` REAL NOT NULL, `measurementId` INTEGER NOT NULL, FOREIGN KEY(`measurementId`) REFERENCES `Measurement`(`_id`) ON UPDATE NO ACTION ON DELETE CASCADE );")
                // Create index
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_Pressure_measurementId` ON `Pressure` (`measurementId`);")
            }
        }
    }

    /**
     * This cannot be a companion object as this requires the [context].
     *
     * @see [MIGRATION_9_10]
     */
    private fun migrationFrom9To10(): Migration {
        return object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrate9To10Identifiers(db)
                migrate9To10Measurements(db)
            }

            /**
             * The table was added in version `10` (SDK 3.1.0). Before we stored the `deviceId` in the
             * 'SharedPreferences` with the key `de.cyface.identifier.device`.
             *
             * @param database The database to upgrade
             */
            private fun migrate9To10Identifiers(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE identifiers " +
                        "(_id INTEGER PRIMARY KEY AUTOINCREMENT, device_id TEXT NOT NULL);")
                // Try to migrate old device id (which was stored in the preferences)
                @Suppress("DEPRECATION") // (!) Don't change this, as this is migration code !
                val preferences = PreferenceManager.getDefaultSharedPreferences(context)
                val installId = preferences.getString("de.cyface.identifier.device", null)
                if (installId != null) {
                    Log.d(Constants.TAG, "Migrating old device id: $installId")
                    database.execSQL("INSERT INTO identifiers (_id,device_id) VALUES (1,'$installId');")
                }
            }

            /**
             * The migration from `9` to `10` corrects the `distance` of measurements migrated from
             * version `8` to `9` which was accidentally set to `0.0`.
             *
             * @param database The database to upgrade
             */
            private fun migrate9To10Measurements(database: SupportSQLiteDatabase) {
                var measurementCursor: Cursor? = null
                var geoLocationCursor: Cursor? = null
                try {
                    // table & column name need to equal version `9`
                    measurementCursor = database.query(
                        SupportSQLiteQueryBuilder.builder("measurements").columns(arrayOf("_id"))
                            .create()
                    )
                    if (measurementCursor.count == 0) {
                        Log.v(Constants.TAG, "No measurements for migration found")
                        return
                    }

                    // Check all measurements
                    while (measurementCursor.moveToNext()) {
                        val measurementId =
                            measurementCursor.getLong(measurementCursor.getColumnIndexOrThrow("_id"))
                        geoLocationCursor = database.query(
                            SupportSQLiteQueryBuilder
                                .builder("locations")
                                .selection("measurement_fk = ?", arrayOf(measurementId))
                                .columns(arrayOf("lat", "lon", "gps_time", "speed", "accuracy"))
                                .orderBy("gps_time ASC")
                                .create()
                        )
                        if (geoLocationCursor.count < 2) {
                            Log.v(
                                Constants.TAG,
                                "Not enough geoLocations to update distance in measurement entry:$measurementId"
                            )
                            continue
                        }

                        // Calculate distance for selected measurement
                        var distance = 0.0
                        var lastLocationLat: Double? = null
                        var lastLocationLon: Double? = null
                        while (geoLocationCursor.moveToNext()) {
                            val newLocationLat =
                                geoLocationCursor.getFloat(geoLocationCursor.getColumnIndexOrThrow("lat"))
                                    .toDouble()
                            val newLocationLon =
                                geoLocationCursor.getFloat(geoLocationCursor.getColumnIndexOrThrow("lon"))
                                    .toDouble()

                            // We cannot calculate a distance from just one geoLocation:
                            if (lastLocationLat == null || lastLocationLon == null) {
                                require(lastLocationLat == null && lastLocationLon == null)
                                lastLocationLat = newLocationLat
                                lastLocationLon = newLocationLon
                                continue
                            }

                            // Calculate distance between last two locations
                            val newDistance = calculateDistance(
                                lastLocationLat,
                                lastLocationLon,
                                newLocationLat,
                                newLocationLon
                            )
                            require(newDistance >= 0)
                            distance += newDistance
                            lastLocationLat = newLocationLat
                            lastLocationLon = newLocationLon
                        }
                        Log.v(
                            Constants.TAG,
                            "Updating distance for measurement $measurementId to $distance"
                        )
                        database.execSQL("UPDATE measurements SET distance = $distance WHERE _id = $measurementId")
                    }
                } finally {
                    measurementCursor?.close()
                    geoLocationCursor?.close()
                }
            }

            /**
             * Calculates the distance between two locations like in the [DefaultDistanceCalculation]
             * implementation at the time when version `10` was introduced.
             */
            private fun calculateDistance(
                lastLocationLat: Double,
                lastLocationLon: Double,
                newLocationLat: Double,
                newLocationLon: Double
            ): Double {
                val defaultProvider = "default"
                val previousLocation = Location(defaultProvider)
                val nextLocation = Location(defaultProvider)
                previousLocation.latitude = lastLocationLat
                previousLocation.longitude = lastLocationLon
                nextLocation.latitude = newLocationLat
                nextLocation.longitude = newLocationLon
                return previousLocation.distanceTo(nextLocation).toDouble()
            }
        }
    }

    /**
     * The SQLite database version is stored in 4 bytes at offset 60 in the field `user cookie`.
     */
    @Throws(Exception::class)
    private fun sqLiteDatabaseVersion(file: File): Int {
        val fileAccess = RandomAccessFile(file, "r")
        fileAccess.seek(60)
        val buff = ByteArray(4)
        fileAccess.read(buff, 0, 4)
        return ByteBuffer.wrap(buff).int
    }
}