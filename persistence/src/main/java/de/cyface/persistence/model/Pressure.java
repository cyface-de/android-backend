package de.cyface.persistence.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * An {@code @Entity} which represents the data type of a pressure point, usually captured by a barometer.
 * <p>
 * An instance of this class represents one row in a database table containing the pressure data.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.3.0
 */
// foreignKeys not linked as `Measurement` is not stored with `Room`
@Entity(/* foreignKeys = {@ForeignKey(entity = Measurement.class, ..., ..., onDelete = ForeignKey.CASCADE)} */)
public class Pressure {

    @PrimaryKey(autoGenerate = true)
    public long uid;

    @ColumnInfo(name = "timestamp")
    public long timestamp;

    @ColumnInfo(name = "pressure")
    public float pressure;

    // foreignKeys not linked as `Measurement` is not stored with `Room`
    @ColumnInfo(name = "measurement_fk")
    public long measurementId;
}
