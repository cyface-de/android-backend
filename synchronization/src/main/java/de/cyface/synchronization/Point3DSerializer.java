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
 * @version 1.0.1
 * @since 2.0.0
 * @see SamplePointTable
 * @see RotationPointTable
 * @see MagneticValuePointTable
 */
interface Point3DSerializer {

    /**
     * @return The <code>ContentProvider</code> table URI, containing the data points.
     */
    Uri getTableUri();

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
