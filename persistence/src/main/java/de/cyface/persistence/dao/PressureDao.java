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

    @Query("SELECT * FROM pressure WHERE uid IN (:pressureIds)")
    List<Pressure> loadAllByIds(int[] pressureIds);

    @Insert
    void insertAll(Pressure... pressures);

    @Delete
    void delete(Pressure pressure);
}