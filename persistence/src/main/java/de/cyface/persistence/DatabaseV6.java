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
package de.cyface.persistence;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import de.cyface.persistence.dao.GeoLocationDao;
import de.cyface.persistence.dao.PressureDao;
import de.cyface.persistence.model.GeoLocationV6;
import de.cyface.persistence.model.Pressure;

/**
 * This class holds the database for V6 specific data. [STAD-380]
 * <p>
 * It defines the database configuration and serves as the main access point to the persisted data.
 * <p>
 * TODO: when fully migrating to Room, check if we need to use pagination like in our SQLite implementation
 * where we used a database limit of 10k because of performance issues. [MOV-248]
 * Maybe library androidx.room:room-paging can be used for this.
 * <p>
 * The Data Access objects (DAOs) implemented are currently only executed synchronously, i.e. cannot be executed
 * from main thread as this would freeze the UI. To ease merging the SDK 6 features into SDK 7, we use the
 * synchronous execution and AsyncTasks, like everywhere else in SDK 6. When migrating to SDK 7, check if
 * we want to use the async queries https://developer.android.com/training/data-storage/room/async-queries,
 * where we cannot migrate to LiveData.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.3.0
 */
@Database(entities = {Pressure.class, GeoLocationV6.class}, version = 1)
public abstract class DatabaseV6 extends RoomDatabase {

    /**
     * @return Data access object which provides the API to interact with the {@link Pressure} database table.
     */
    public abstract PressureDao pressureDao();

    /**
     * @return Data access object which provides the API to interact with the {@link GeoLocationV6} database table.
     */
    public abstract GeoLocationDao geoLocationDao();
}