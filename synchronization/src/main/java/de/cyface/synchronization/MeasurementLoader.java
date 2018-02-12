package de.cyface.synchronization;

import android.content.ContentProviderClient;
import android.database.Cursor;
import android.os.RemoteException;
import android.support.annotation.NonNull;

import de.cyface.persistence.GpsPointsTable;
import de.cyface.persistence.MeasuringPointsContentProvider;

/**
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
public class MeasurementLoader {

    private final long measurementIdentifier;
    /**
     * The client used to load the data to serialize from the <code>ContentProvider</code>.
     */
    private final ContentProviderClient client;

    /**
     * @param measurementIdentifier The device wide unqiue identifier of the measurement to serialize.
     * @param client
     */
    MeasurementLoader(final long measurementIdentifier, final @NonNull ContentProviderClient client) {
        this.measurementIdentifier = measurementIdentifier;
        this.client = client;
    }

    Cursor loadGeoLocations() throws RemoteException {
        return client.query(MeasuringPointsContentProvider.GPS_POINTS_URI,
                new String[] {GpsPointsTable.COLUMN_GPS_TIME, GpsPointsTable.COLUMN_LAT, GpsPointsTable.COLUMN_LON,
                        GpsPointsTable.COLUMN_SPEED, GpsPointsTable.COLUMN_ACCURACY},
                GpsPointsTable.COLUMN_MEASUREMENT_FK + "=?",
                new String[] {Long.valueOf(measurementIdentifier).toString()}, null);
    }

    Cursor load3DPoint(final @NonNull Point3DSerializer serializer) throws RemoteException {
        return client.query(serializer.getTableUri(),
                new String[] {serializer.getTimestampColumnName(), serializer.getXColumnName(), serializer.getYColumnName(), serializer.getZColumnName()},
                serializer.getMeasurementKeyColumnName() + "=?", new String[] {Long.valueOf(measurementIdentifier).toString()},
                null);
    }
}
