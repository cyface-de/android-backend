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
import androidx.room.Insert;
import androidx.room.Query;

import de.cyface.persistence.model.PersistedGeoLocation;

/**
 * Data access object which provides the API to interact with the {@link PersistedGeoLocation} database table.
 * <p>
 * <b>Attention:</b>
 * Keep this class unchanged until SDK 6 apps (databases) are migrated to SDK 7.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.3.0
 */
@Dao
public interface GeoLocationDao {

    @Query("SELECT * FROM location WHERE measurement_fk = :measurementId ORDER BY timestamp ASC")
    List<PersistedGeoLocation> loadAllByMeasurementId(long measurementId);

    @Insert
    void insertAll(PersistedGeoLocation... locations);

    @SuppressWarnings("UnusedReturnValue")
    @Query("DELETE FROM pressure WHERE measurement_fk = :measurementId")
    int deleteItemByMeasurementId(long measurementId);
}