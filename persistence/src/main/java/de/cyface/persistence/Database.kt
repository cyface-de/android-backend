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

import androidx.room.RoomDatabase
import de.cyface.persistence.dao.EventDao
import de.cyface.persistence.dao.IdentifierDao
import de.cyface.persistence.dao.LocationDao
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
 * Room itself (like SQLite, Room is thread-safe) and only uses one connection for writing.
 * We only need to worry about deadlocks when running manual transactions. See
 * https://www.reddit.com/r/androiddev/comments/9s2m4x/comment/e8nklbg/?utm_source=share&utm_medium=web2x&context=3
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
    abstract fun locationDao(): LocationDao
}