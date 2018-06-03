package de.cyface.synchronization;

import android.net.Uri;

import de.cyface.persistence.MagneticValuePointTable;
import de.cyface.persistence.RotationPointTable;
import de.cyface.persistence.SamplePointTable;

/**
 * Serializes a point with 3 coordinates (i.e. acceleration, rotation, direction) into the Cyface binary format. An
 * actual implementation needs to provide mappings from database column names to the properties required for
 * serialization.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 * @see SamplePointTable
 * @see RotationPointTable
 * @see MagneticValuePointTable
 */
abstract class Point3DSerializer {

    /**
     * @return The <code>ContentProvider</code> table URI, containing the data points.
     */
    protected abstract Uri getTableUri();

    /**
     * @return The database name of the column containing the point's X values.
     */
    protected abstract String getXColumnName();

    /**
     * @return The database name of the column containing the point's Y values.
     */
    protected abstract String getYColumnName();

    /**
     * @return The database name of the column containing the point's Z values.
     */
    protected abstract String getZColumnName();

    /**
     * @return The database name of the column containing the point's foreign key column referencing the measurement
     *         table.
     */
    protected abstract String getMeasurementKeyColumnName();

    /**
     * @return The database name of the column containing the point's timestamp.
     */
    protected abstract String getTimestampColumnName();
}
