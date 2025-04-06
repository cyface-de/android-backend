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
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import de.cyface.persistence.dao.EventDao
import de.cyface.persistence.dao.AttachmentDao
import de.cyface.persistence.dao.IdentifierDao
import de.cyface.persistence.dao.LocationDao
import de.cyface.persistence.dao.MeasurementDao
import de.cyface.persistence.dao.PressureDao
import de.cyface.persistence.model.Event
import de.cyface.persistence.model.Attachment
import de.cyface.persistence.model.GeoLocation
import de.cyface.persistence.model.Identifier
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.model.PathTypeConverter
import de.cyface.persistence.model.Pressure

/**
 * This class represents the database stored in an SQLite file named `measures`.
 *
 * It defines the database configuration and serves as the main access point to the persisted data.
 *
 * Room itself (like SQLite, Room is thread-safe) and only uses one connection for writing.
 * We only need to worry about deadlocks when running manual transactions. See
 * https://www.reddit.com/r/androiddev/comments/9s2m4x/comment/e8nklbg/?utm_source=share&utm_medium=web2x&context=3
 *
 * @author Armin Schnabel
 * @version 1.1.1
 * @since 7.5.0
 */
@androidx.room.Database(
    entities = [
        Identifier::class,
        Measurement::class,
        Event::class,
        Pressure::class,
        GeoLocation::class,
        Attachment::class
    ],
    // version 18 imported data from `v6.1` database into `measures.17` and migrated `measures` to Room
    // version 19 adds the attachments table
    // version 20 adds filesSize to the measurement table [RFR-1213]
    version = 20
    //autoMigrations = [] // test this feature on the next version change
)
@TypeConverters(PathTypeConverter::class)
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
    abstract fun locationDao(): LocationDao

    /**
     * @return Data access object which provides the API to interact with the [Attachment] database table.
     */
    abstract fun attachmentDao(): AttachmentDao

    companion object {
        /**
         * The file name of the database represented by this class.
         */
        private const val DATABASE_NAME = "measures"

        /**
         * Creates a new instance of this class.
         *
         * No Singleton should be necessary (https://github.com/cyface-de/android-backend/pull/268).
         */
        fun build(context: Context): Database {
            val migrator = DatabaseMigrator(context)
            // Enabling `multiInstanceInvalidation` tells Room that we use it across processes.
            // A `MultiInstanceInvalidationService` is used to transfer database modifications
            // between the processes, so we can use it safely across processes.
            return Room.databaseBuilder(
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
                    migrator.MIGRATION_17_18,
                    DatabaseMigrator.MIGRATION_18_19,
                    DatabaseMigrator.MIGRATION_19_20,
                )
                .build()
        }
    }
}