/*
 * Copyright 2023 Cyface GmbH
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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import de.cyface.persistence.v6.DatabaseV6

/**
 * This class wraps the [Database] migration code.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.5.0
 * @property context The `Context` required to import data from a secondary data source.
 */
class DatabaseMigrator(val context: Context) {
    /**
     * The migration to migrate from database 17 which was the last version before using `Room`-generated
     * tables and before adding `ForeignKey` constraints and an index on the `measurementId`.
     *
     * This migration step is special as it checks for `altitude` data which we stored for a short time
     * in a second database with the name `v6`. If the second database `v6` exists, the locations of
     * measurements which also exist in `v6.Location` are used instead of `measures.locations` as they
     * are a copy of the later but are annotated with `altitude` and `verticalAccuracy`. Additionally,
     * if `v6` exists, the `v6.Pressure` data is imported into `measures.Pressure`.
     *
     * FIXME: We need to load locations of measurements which are in `measures` but not `v6` from
     * `measures.17.locations` as they where never added to `v6`!
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

    companion object {
        /**
         * From `7.4.0`.`DatabaseHelper.onUpgrade`.
         *
         * Version 8 was the last version in SDK 2 (SDK 3 was release in Feb'2019).
         */
        val MIGRATION_8_9 = migrationFrom8To9()

        /**
         * @see [MIGRATION_8_9]
         */
        private fun migrationFrom8To9(): Migration {
            return object : Migration(8, 9) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("DELETE FROM sample_points;");
                    database.execSQL("DROP TABLE sample_points;");
                    database.execSQL("DELETE FROM rotation_points;");
                    database.execSQL("DROP TABLE rotation_points;");
                    database.execSQL("DELETE FROM magnetic_value_points;");
                    database.execSQL("DROP TABLE magnetic_value_points;");
                }
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
            override fun migrate(database: SupportSQLiteDatabase) {
                // Migrate Identifier data
                // Create table with Room generated name and new schema
                database.execSQL("CREATE TABLE IF NOT EXISTS `Identifier` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `deviceId` TEXT NOT NULL)")
                // Insert the data from old table
                database.execSQL("INSERT INTO `Identifier` (`id`, `deviceId`) SELECT `_id`, `device_id` FROM `identifiers`")
                // Drop the old table
                database.execSQL("DROP TABLE `identifiers`")

                // Migrate Measurement data
                // Create table with Room generated name and new schema
                database.execSQL("CREATE TABLE IF NOT EXISTS `Measurement` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `status` TEXT NOT NULL, `modality` TEXT NOT NULL, `fileFormatVersion` INTEGER NOT NULL, `distance` REAL NOT NULL, `timestamp` INTEGER NOT NULL)")
                // Insert the data from old table
                database.execSQL(
                    "INSERT INTO `Measurement` (`id`, `status`, `modality`, `fileFormatVersion`, `distance`, `timestamp`)"
                            + " SELECT `_id`, `status`, `modality`, `file_format_version`, `distance`, `timestamp` FROM `measurements`"
                )
                // Drop the old table
                database.execSQL("DROP TABLE `measurements`")

                // Migrate Event data
                // Create table with Room generated name and new schema
                database.execSQL("CREATE TABLE IF NOT EXISTS `Event` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `type` TEXT NOT NULL, `value` TEXT, `measurementId` INTEGER NOT NULL, FOREIGN KEY(`measurementId`) REFERENCES `Measurement`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                // Insert the data from old table
                database.execSQL(
                    "INSERT INTO `Event` (`id`, `timestamp`, `type`, `value`, `measurementId`)"
                            + " SELECT `_id`, `timestamp`, `type`, `value`, `measurement_fk` FROM `events`"
                )
                // Create index
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_Event_measurementId` ON `Event` (`measurementId`)")
                // Drop the old table
                database.execSQL("DROP TABLE `events`")

                // Check if database `v6` exists
                val v6File = context.getDatabasePath(DatabaseV6.DATABASE_NAME)
                if (v6File.exists()) {
                    migrate17To18LocationsAndPressuresWithV6Data(database)
                } else {
                    migrate17To18LocationsAndPressuresWithoutV6Data(database)
                }
            }

            /**
             * When `v6` database exists, we first check which measurements have locations in `v6`
             * and choose these locations before the locations from `measures` as the earlier are a
             * copy of the later but contain the `altitude` and `verticalAccuracy` data.
             */
            private fun migrate17To18LocationsAndPressuresWithV6Data(database: SupportSQLiteDatabase) {
                val v6Database = DatabaseV6.getDatabase(context)
                // FIXME: ensure version is 1 as this only migrates from version 1

                // Migrate GeoLocation data
                // Create table with Room generated name and new schema
                database.execSQL("CREATE TABLE IF NOT EXISTS `Location` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `lat` REAL NOT NULL, `lon` REAL NOT NULL, `altitude` REAL, `speed` REAL NOT NULL, `accuracy` REAL, `verticalAccuracy` REAL, `measurementId` INTEGER NOT NULL, FOREIGN KEY(`measurementId`) REFERENCES `Measurement`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                // Insert the data from the `v6` database version `1`
                v6Database.geoLocationDao()!!.all.forEach {
                    // Set 0 `accuracy` to null and convert accuracy from cm to m
                    val accuracy = if (it.accuracy == 0.0) null else it.accuracy / 100.0
                    // v6.1 `altitude` and `verticalAccuracy` are already in the same format
                    database.execSQL(
                        "INSERT INTO `Location` (`id`, `timestamp`, `lat`, `lon`, `altitude`, `speed`, `accuracy`, `verticalAccuracy`, `measurementId`)"
                                + " VALUES ('" + it.uid + "', '" + it.timestamp + "', '" + it.lat + "', '" + it.lon + "', '" + it.altitude + "', '" + it.speed + "', '" + accuracy + "', '" + it.verticalAccuracy + "', '" + it.measurementId + "')"
                    )
                }
                // Create index
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_Location_measurementId` ON `Location` (`measurementId`)")
                // Drop the old table for locations, as we already imported `v6.Location`
                database.execSQL("DROP TABLE `locations`") // FIXME: see FIXME in method documentation! load locations which are not in `v6` from `measures`!

                // Migrate Pressure data
                // Create table with Room generated name and new schema
                database.execSQL("CREATE TABLE IF NOT EXISTS `Pressure` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `pressure` REAL NOT NULL, `measurementId` INTEGER NOT NULL, FOREIGN KEY(`measurementId`) REFERENCES `Measurement`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                // Insert the data from the `v6` database version `1`
                v6Database.pressureDao()!!.all.forEach {
                    database.execSQL(
                        "INSERT INTO `Pressure` (`id`, `timestamp`, `pressure`, `measurementId`)"
                                + " VALUES ('" + it.uid + "', '" + it.timestamp + "', '" + it.pressure + "', '" + it.measurementId + "')"
                    )
                }
                // Create index
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_Pressure_measurementId` ON `Pressure` (`measurementId`)")

                // Drop the old database `v6`
                context.deleteDatabase(DatabaseV6.DATABASE_NAME)
            }

            /**
             * When `v6` database does not exist, we only migrate the locations and pressures from `measures` version `17`.
             */
            private fun migrate17To18LocationsAndPressuresWithoutV6Data(database: SupportSQLiteDatabase) {
                // Migrate GeoLocation data
                // Create table with Room generated name and new schema
                database.execSQL("CREATE TABLE IF NOT EXISTS `Location` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `lat` REAL NOT NULL, `lon` REAL NOT NULL, `altitude` REAL, `speed` REAL NOT NULL, `accuracy` REAL, `verticalAccuracy` REAL, `measurementId` INTEGER NOT NULL, FOREIGN KEY(`measurementId`) REFERENCES `Measurement`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                // Insert the data from old table
                /// `accuracy` in `measures` version `17` is already in meters
                database.execSQL(
                    "INSERT INTO `Location` (`id`, `timestamp`, `lat`, `lon`, `altitude`, `speed`, `accuracy`, `verticalAccuracy`, `measurementId`)"
                            + " SELECT `_id`, `gps_time`, `lat`, `lon`, null, `speed`, `accuracy`, null, `measurement_fk` FROM `locations`"
                )
                /// Set `accuracy` values with `0` m to `null` FIXME: check if this would happen automatically anyways
                database.execSQL("UPDATE `Location` SET `accuracy` = null WHERE `accuracy` = 0")
                // Create index
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_Location_measurementId` ON `Location` (`measurementId`)")
                // Drop the old table
                database.execSQL("DROP TABLE `locations`")

                // Migrate Pressure data
                // Create table with Room generated name and new schema
                database.execSQL("CREATE TABLE IF NOT EXISTS `Pressure` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `pressure` REAL NOT NULL, `measurementId` INTEGER NOT NULL, FOREIGN KEY(`measurementId`) REFERENCES `Measurement`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                // Create index
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_Pressure_measurementId` ON `Pressure` (`measurementId`)")
            }
        }
    }
}