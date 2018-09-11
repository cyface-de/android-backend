package de.cyface.synchronization;

import java.util.ArrayList;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentProviderOperation;
import android.database.Cursor;
import android.support.annotation.NonNull;

import de.cyface.persistence.GpsPointsTable;

/**
 * Mapper used to parse geolocations from and to {@link JSONObject}s.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.0
 * @since 2.0.0
 */
final class GeoLocationJsonMapper implements JsonMapper {

    private JsonDatabaseMapper jsonDatabaseMapper;

    GeoLocationJsonMapper() {
        this.jsonDatabaseMapper = new JsonDatabaseMapper();
    }

    @Override
    public JSONObject map(final @NonNull Cursor cursor) throws JSONException {
        final JSONObject geoLocationJson = new JSONObject();
        geoLocationJson.put("lat", cursor.getDouble(cursor.getColumnIndex(GpsPointsTable.COLUMN_LAT)));
        geoLocationJson.put("lon", cursor.getDouble(cursor.getColumnIndex(GpsPointsTable.COLUMN_LON)));
        geoLocationJson.put("timestamp", cursor.getLong(cursor.getColumnIndex(GpsPointsTable.COLUMN_GPS_TIME)));
        geoLocationJson.put("speed", cursor.getDouble(cursor.getColumnIndex(GpsPointsTable.COLUMN_SPEED)));
        geoLocationJson.put("accuracy", cursor.getInt(cursor.getColumnIndex(GpsPointsTable.COLUMN_ACCURACY)));
        return geoLocationJson;
    }

    @Override
    public ArrayList<ContentProviderOperation> buildDeleteDataPointsOperation(final JSONObject measurementSlice,
                                                                              final String authority) throws SynchronisationException {

        return jsonDatabaseMapper.buildDeleteOperation(measurementSlice, authority, GpsPointsTable.URI_PATH,
                "gpsPoints", GpsPointsTable.COLUMN_MEASUREMENT_FK, GpsPointsTable.COLUMN_GPS_TIME);
    }
}
