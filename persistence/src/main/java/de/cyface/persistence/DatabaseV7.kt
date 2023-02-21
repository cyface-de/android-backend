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
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
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
@Database(
    entities = [Identifier::class, Measurement::class, Event::class, Pressure::class, GeoLocation::class],
    version = 1,
    autoMigrations = [
        //AutoMigration (from = 5, to = 6)
    ]
)
abstract class DatabaseV7 : RoomDatabase() {
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

    // See https://developer.android.com/codelabs/android-room-with-a-view-kotlin#13
    private class DatabaseV7Callback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch {
                    populateDatabase(database.identifierDao()!!)
                }
            }
        }

        suspend fun populateDatabase(identifierDao: IdentifierDao) {
            // Delete all content here.
            //identifierDao.deleteAll()

            // Add sample words.
            //var word = Word("Hello")
            //identifierDao.insert(word)
            //word = Word("World!")
            //identifierDao.insert(word)
        }
    }

    // See https://developer.android.com/codelabs/android-room-with-a-view-kotlin#7
    companion object {
        @Volatile // Singleton to prevent multiple open database-instances at the same time
        private var INSTANCE: DatabaseV7? = null

        private const val DATABASE_NAME = "v7"

        /*private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // create comic bookmark table
                database.execSQL("CREATE TABLE `Measurement` (`id` INTEGER NOT NULL, `dateOfCreate` TEXT, PRIMARY KEY(`id`))")
            }
        }*/

        fun getDatabase(context: Context, scope: CoroutineScope): DatabaseV7 {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DatabaseV7::class.java,
                    DATABASE_NAME)
                    .addCallback(DatabaseV7Callback(scope))
                    .enableMultiInstanceInvalidation()
                    //.addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                return instance
            }
        }
    }
}