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
 * This class represents the database stored in an SQLite file named `measures`.
 *
 * It defines the database configuration and serves as the main access point to the persisted data.
 *
 * The Data Access objects (DAOs) implemented are currently only used from `runBlocking` to offer the
 * synchronous API used until now. We might want to add an async API, too, in the future.
 * For this, see: https://developer.android.com/training/data-storage/room/async-queries
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
    version = 18
    //autoMigrations = [] // test this feature on the next version change
)
abstract class Database : RoomDatabase() {
    /**
     * @return Data access object which provides the API to interact with the [Identifier] database table.
     */
    abstract fun identifierDao(): IdentifierDao

    /**
     * @return Data access object which provides the API to interact with the [Measurement] database table.
     */
    abstract fun measurementDao(): MeasurementDao

    /**
     * @return Data access object which provides the API to interact with the [Event] database table.
     */
    abstract fun eventDao(): EventDao

    /**
     * @return Data access object which provides the API to interact with the [Pressure] database table.
     */
    abstract fun pressureDao(): PressureDao

    /**
     * @return Data access object which provides the API to interact with the [GeoLocation] database
     * table.
     */
    abstract fun geoLocationDao(): GeoLocationDao

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

                // This needs to be here as it requires a context to load database `v6` (17->18)
                val migrator = DatabaseMigrator(context)

                // Enabling `multiInstanceInvalidation` tells Room that we use it across processes.
                // A `MultiInstanceInvalidationService` is used to transfer database modifications
                // between the processes, so we can use it safely across processes. No Singleton
                // should be necessary, see: https://github.com/cyface-de/android-backend/pull/268
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    Database::class.java,
                    DATABASE_NAME
                )
                    .enableMultiInstanceInvalidation()
                    .addMigrations(
                        DatabaseMigrator.MIGRATION_8_9,
                        migrator.MIGRATION_9_10,
                        DatabaseMigrator.MIGRATION_10_11,
                        DatabaseMigrator.MIGRATION_11_12,
                        DatabaseMigrator.MIGRATION_12_13,
                        DatabaseMigrator.MIGRATION_13_14,
                        DatabaseMigrator.MIGRATION_14_15,
                        DatabaseMigrator.MIGRATION_15_16,
                        DatabaseMigrator.MIGRATION_16_17,
                        migrator.MIGRATION_17_18
                    )
                    .build()
                INSTANCE = instance
                return instance
            }
        }
    }
}