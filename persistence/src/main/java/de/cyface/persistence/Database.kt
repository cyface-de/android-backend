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
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import de.cyface.persistence.dao.EventDao
import de.cyface.persistence.dao.GeoLocationDao
import de.cyface.persistence.dao.IdentifierDao
import de.cyface.persistence.dao.MeasurementDao
import de.cyface.persistence.dao.PressureDao
import de.cyface.persistence.model.Event
import de.cyface.persistence.model.GeoLocation
import de.cyface.persistence.model.Identifier
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.model.Pressure

/**
 * FIXME: documentation
 * This class holds the database added in SDK 7, which is why the database file is called `v7`.
 *
 * It defines the database configuration and serves as the main access point to the persisted data.
 *
 * FIXME: when fully migrating to Room, check if we need to use pagination like in our SQLite implementation
 * where we used a database limit of 10k because of performance issues. [MOV-248]
 * Maybe library androidx.room:room-paging can be used for this.
 *
 * FIXME: The Data Access objects (DAOs) implemented are currently only executed synchronously, i.e. cannot be executed
 * from main thread as this would freeze the UI. To ease merging the SDK 6 features into SDK 7, we use the
 * synchronous execution and AsyncTasks, like everywhere else in SDK 6. When migrating to SDK 7, check if
 * we want to use the async queries https://developer.android.com/training/data-storage/room/async-queries,
 * where we cannot migrate to LiveData.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.5.0
 */
@androidx.room.Database(
    entities = [
        Identifier::class,
        Measurement::class,
        Event::class,
        Pressure::class,
        GeoLocation::class
    ],
    // version 18 imported data from `v6.1` database into `measures.17` and migrated `measures` to Room
    version = 18,
    autoMigrations = []
)
abstract class Database : RoomDatabase() {
    /**
     * @return Data access object which provides the API to interact with the [Identifier] database table.
     */
    abstract fun identifierDao(): IdentifierDao?

    /**
     * @return Data access object which provides the API to interact with the [Measurement] database table.
     */
    abstract fun measurementDao(): MeasurementDao?

    /**
     * @return Data access object which provides the API to interact with the [Event] database table.
     */
    abstract fun eventDao(): EventDao?

    /**
     * @return Data access object which provides the API to interact with the [Pressure] database table.
     */
    abstract fun pressureDao(): PressureDao?

    /**
     * @return Data access object which provides the API to interact with the [GeoLocation] database
     * table.
     */
    abstract fun geoLocationDao(): GeoLocationDao?

    // See https://developer.android.com/codelabs/android-room-with-a-view-kotlin#7
    companion object {
        @Volatile // Singleton to prevent multiple open database-instances at the same time
        private var INSTANCE: Database? = null

        /**
         * The file name of the database represented by this class.
         */
        private const val DATABASE_NAME = "measures"

        /**
         * Returns the singleton instance of this class
         *
         * From Room guide: https://developer.android.com/training/data-storage/room
         * If the app runs in multiple processes, include enableMultiInstanceInvalidation() in the builder.
         * That way, when when you have an instance of AppDatabase in each process, you can invalidate the shared
         * database file in one process, and this invalidation automatically propagates to the instances of AppDatabase
         * within other processes.
         *
         * Additional notes: Room itself (like SQLite, Room is thread-safe) and only uses one connection for writing.
         * I.e. we only need to worry about deadlocks when running manual transactions (`db.beginTransaction` or
         * `roomDb.runInTransaction`. See
         * https://www.reddit.com/r/androiddev/comments/9s2m4x/comment/e8nklbg/?utm_source=share&utm_medium=web2x&context=3
         * The PersistenceLayer constructor is called from main UI and non-UI threads, e.g.:
         * - main UI thread: CyfaceDataCapturingService, DataCapturingBackgroundService/DataCapturingService,
         * DataCapturingButton, MeasurementOverviewFragment
         * - other threads: SyncAdapter, Event-/MeasurementDeleteController
         */
        fun getDatabase(context: Context): Database {
            return INSTANCE ?: synchronized(this) {

                // This needs to be here as it requires a context to load database `v6`
                @Suppress("LocalVariableName")
                val MIGRATION_17_18 = object : Migration(17, 18) {
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
                            database.execSQL("DROP TABLE `locations`")

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
                        // If `v6` db doesn't exist, migrate the `locations` from `measures` version `17`:
                        else {
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

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    Database::class.java,
                    DATABASE_NAME
                )
                    .enableMultiInstanceInvalidation()
                    .addMigrations(MIGRATION_17_18)
                    .build()
                INSTANCE = instance
                return instance
            }
        }
    }
}