package de.cyface.synchronization;

import android.content.ContentProviderOperation;
import android.database.Cursor;
import android.support.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;

import de.cyface.persistence.GpsPointsTable;
import de.cyface.persistence.MeasuringPointsContentProvider;

final class GeoLocationJsonMapper implements JsonMapper {
    @Override
    public JSONObject map(final @NonNull Cursor cursor) throws JSONException {
        JSONObject geoLocationJson = new JSONObject();
        geoLocationJson.put("lat", cursor.getDouble(cursor.getColumnIndex(GpsPointsTable.COLUMN_LAT)));
        geoLocationJson.put("lon", cursor.getDouble(cursor.getColumnIndex(GpsPointsTable.COLUMN_LAT)));
        geoLocationJson.put("timestamp", cursor.getLong(cursor.getColumnIndex(GpsPointsTable.COLUMN_GPS_TIME)));
        geoLocationJson.put("speed", cursor.getDouble(cursor.getColumnIndex(GpsPointsTable.COLUMN_SPEED)));
        geoLocationJson.put("accuracy", cursor.getInt(cursor.getColumnIndex(GpsPointsTable.COLUMN_ACCURACY)));
        return geoLocationJson;
    }

    @Override
    public Collection<ContentProviderOperation> buildMarkSyncedOperation(final @NonNull JSONObject measurementSlice)
            throws SynchronisationException {
        Collection<ContentProviderOperation> updateOperations = new ArrayList<>();

        try {
            String measurementIdentifier = measurementSlice.getString("id");
            JSONArray geoLocationsArray = measurementSlice.getJSONArray("gpsPoints");
            for (int i = 0; i < geoLocationsArray.length(); i++) {
                JSONObject geoLocation = geoLocationsArray.getJSONObject(i);
                ContentProviderOperation operation = ContentProviderOperation
                        .newUpdate(MeasuringPointsContentProvider.GPS_POINTS_URI)
                        .withSelection(
                                GpsPointsTable.COLUMN_MEASUREMENT_FK + "=? AND " + GpsPointsTable.COLUMN_GPS_TIME
                                        + "=?",
                                new String[] {measurementIdentifier,
                                        geoLocation.getString("timestamp")})
                        .withValue(GpsPointsTable.COLUMN_IS_SYNCED, MeasuringPointsContentProvider.SQLITE_TRUE).build();
                updateOperations.add(operation);
            }
        } catch (JSONException e) {
            throw new SynchronisationException(e);
        }

        return updateOperations;
    }
}
