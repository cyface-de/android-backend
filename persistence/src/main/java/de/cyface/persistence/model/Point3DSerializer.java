package de.cyface.persistence.model;

import de.cyface.persistence.AccelerationPointTable;
import de.cyface.persistence.DirectionPointTable;
import de.cyface.persistence.RotationPointTable;

/**
 * Serializes a point with 3 coordinates (i.e. acceleration, rotation, direction) into the Cyface binary format. An
 * actual implementation needs to provide mappings from database column names to the properties required for
 * serialization.
 *
 * @author Klemens Muthmann
 * @version 2.0.1
 * @since 2.0.0
 * @see AccelerationPointTable
 * @see RotationPointTable
 * @see DirectionPointTable
 */
public interface Point3DSerializer {

    /**
     * @return The createFile segment from the <code>ContentProvider</code> URI, which identifies the table containing the data points.
     */
    String getTableUriPathSegment();

    /**
     * @return The database name of the column containing the point's X values.
     */
    String getXColumnName();

    /**
     * @return The database name of the column containing the point's Y values.
     */
    String getYColumnName();

    /**
     * @return The database name of the column containing the point's Z values.
     */
    String getZColumnName();

    /**
     * @return The database name of the column containing the point's foreign key column referencing the measurement
     *         table.
     */
    String getMeasurementKeyColumnName();

    /**
     * @return The database name of the column containing the point's timestamp.
     */
    String getTimestampColumnName();
}
