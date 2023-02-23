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
package de.cyface.persistence.v6

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import de.cyface.persistence.v6.dao.GeoLocationDao
import de.cyface.persistence.v6.dao.PressureDao
import de.cyface.persistence.v6.model.PersistedGeoLocation
import de.cyface.persistence.v6.model.PersistedPressure

/**
 * This class holds the database for V6 specific data. [STAD-380]
 *
 * It defines the database configuration and serves as the main access point to the persisted data.
 *
 * TODO: when fully migrating to Room, check if we need to use pagination like in our SQLite implementation
 * where we used a database limit of 10k because of performance issues. [MOV-248]
 * Maybe library androidx.room:room-paging can be used for this.
 *
 * The Data Access objects (DAOs) implemented are currently only executed synchronously, i.e. cannot be executed
 * from main thread as this would freeze the UI. To ease merging the SDK 6 features into SDK 7, we use the
 * synchronous execution and AsyncTasks, like everywhere else in SDK 6. When migrating to SDK 7, check if
 * we want to use the async queries https://developer.android.com/training/data-storage/room/async-queries,
 * where we cannot migrate to LiveData.
 *
 * **Attention:**
 * Keep this class unchanged until SDK 6 apps (databases) are migrated to SDK 7.
 *
 * @author Armin Schnabel
 * @version 1.1.0 // Port all future changes to `release-6` branch until SDK 6 is still used
 * @since 6.3.0
 */
@Database(
    entities = [PersistedPressure::class, PersistedGeoLocation::class],
    // Don't increase here, if at all, only on `release-6` branch but then update migration to V18
    version = 1
)
abstract class DatabaseV6 : RoomDatabase() {
    /**
     * @return Data access object which provides the API to interact with the [PersistedPressure] database table.
     */
    abstract fun pressureDao(): PressureDao?

    /**
     * @return Data access object which provides the API to interact with the [PersistedGeoLocation] database
     * table.
     */
    abstract fun geoLocationDao(): GeoLocationDao?

    // See https://developer.android.com/codelabs/android-room-with-a-view-kotlin#7
    companion object {
        @Volatile // Singleton to prevent multiple open database-instances at the same time
        private var INSTANCE: DatabaseV6? = null

        /**
         * The file name of the database represented by this class.
         */
        const val DATABASE_NAME = "v6"

        /**
         * Returns the singleton instance of this class
         *
         * This method is also called by a migration step in [de.cyface.persistence.PersistenceLayer], to
         * load `v6` data into `measures.18`. Using this method ensures all migration steps are applied.
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
        fun getDatabase(context: Context): DatabaseV6 {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DatabaseV6::class.java,
                    DATABASE_NAME
                )
                    .enableMultiInstanceInvalidation()
                    .build()
                INSTANCE = instance
                return instance
            }
        }
    }
}