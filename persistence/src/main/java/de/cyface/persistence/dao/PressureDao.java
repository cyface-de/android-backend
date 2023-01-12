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
package de.cyface.persistence.dao;

import java.util.List;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import de.cyface.persistence.model.Pressure;

/**
 * Data access object which provides the API to interact with the {@link Pressure} database table.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.3.0
 */
@Dao
public interface PressureDao {

    @Query("SELECT * FROM pressure")
    List<Pressure> getAll();

    // TODO: when fully migrating to Room, check if we need to use pagination like in our SQLite implementation
    // where we used a database limit of 10k because of performance issues. [MOV-248]
    // Maybe library androidx.room:room-paging can be used for this.
    @Query("SELECT * FROM pressure WHERE uid IN (:pressureIds)")
    List<Pressure> loadAllByIds(int[] pressureIds);

    @Insert
    void insertAll(Pressure... pressures);

    @Delete
    void delete(Pressure pressure);

    @SuppressWarnings("UnusedReturnValue")
    @Query("DELETE FROM pressure WHERE measurement_fk = (:measurementId)")
    int deleteItemByMeasurementId(long measurementId);
}