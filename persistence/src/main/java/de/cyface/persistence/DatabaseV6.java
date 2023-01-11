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
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.3.0
 */
@Database(entities = {Pressure.class, GeoLocationV6.class}, version = 1)
public abstract class DatabaseV6 extends RoomDatabase {

    public abstract PressureDao pressureDao();
    public abstract GeoLocationDao geoLocationDao();
}