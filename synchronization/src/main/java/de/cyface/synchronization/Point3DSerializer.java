package de.cyface.synchronization;

import android.content.ContentProviderClient;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.support.annotation.NonNull;

import java.nio.ByteBuffer;

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

//    /**
//     * The client used to load the data to serialize from the <code>ContentProvider</code>.
//     */
//    private final ContentProviderClient databaseClient;
//
//    /**
//     * Creates a new completely initialized <code>Point3DSerializer</code>, with access to a
//     * <code>MeasuringPointContentProvider</code> to load data from.
//     *
//     * @param databaseClient The client used to load the data to serialize from the <code>ContentProvider</code>.
//     */
//    Point3DSerializer(final @NonNull ContentProviderClient databaseClient) {
//        this.databaseClient = databaseClient;
//    }

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

    /**
     * Serializes all the points from the measurement identified by the provided
     * <code>measurementIdentifier</code>.
     *
     * @param measurementIdentifier The device wide unqiue identifier of the measurement to serialize.
     * @return A <code>byte</code> array containing all the data.
     */
    byte[] serialize(final @NonNull Cursor pointCursor) {
            ByteBuffer buffer = ByteBuffer.allocate(pointCursor.getCount() * (ByteSizes.LONG_BYTES + 3 * ByteSizes.DOUBLE_BYTES));
            while (pointCursor.moveToNext()) {
                buffer.putLong(pointCursor.getLong(pointCursor.getColumnIndex(getTimestampColumnName())));
                buffer.putDouble(pointCursor.getDouble(pointCursor.getColumnIndex(getXColumnName())));
                buffer.putDouble(pointCursor.getDouble(pointCursor.getColumnIndex(getYColumnName())));
                buffer.putDouble(pointCursor.getDouble(pointCursor.getColumnIndex(getZColumnName())));
            }
            byte[] payload = new byte[buffer.capacity()];
            ((ByteBuffer)buffer.duplicate().clear()).get(payload);
            // if we want to switch from write to read mode on the byte buffer we need to .flip() !!
            return payload;
    }
}
