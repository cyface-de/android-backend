package de.cyface.persistence.dao;

import java.util.List;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import de.cyface.persistence.model.GeoLocationV6;
import de.cyface.persistence.model.Pressure;

/**
 * Data access object which provides the API to interact with the {@link GeoLocationV6} database table.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.3.0
 */
@Dao
public interface GeoLocationDao {

    @Query("SELECT * FROM location")
    List<GeoLocationV6> getAll();

    @Query("SELECT * FROM location WHERE uid IN (:locationIds)")
    List<GeoLocationV6> loadAllByIds(int[] locationIds);

    @Insert
    void insertAll(GeoLocationV6... locations);

    @Delete
    void delete(GeoLocationV6 location);
}