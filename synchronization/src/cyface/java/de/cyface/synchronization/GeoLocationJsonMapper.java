package de.cyface.synchronization;

import java.util.ArrayList;
import java.util.Collection;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentProviderOperation;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;

import de.cyface.persistence.GpsPointsTable;
import de.cyface.persistence.MeasuringPointsContentProvider;

/**
 * Mapper used to parse geolocations from an to {@link JSONObject}s.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
final class GeoLocationJsonMapper implements JsonMapper {
    @Override
    public JSONObject map(final @NonNull Cursor cursor) throws JSONException {
        final JSONObject geoLocationJson = new JSONObject();
        geoLocationJson.put("lat", cursor.getDouble(cursor.getColumnIndex(GpsPointsTable.COLUMN_LAT)));
        geoLocationJson.put("lon", cursor.getDouble(cursor.getColumnIndex(GpsPointsTable.COLUMN_LAT)));
        geoLocationJson.put("timestamp", cursor.getLong(cursor.getColumnIndex(GpsPointsTable.COLUMN_GPS_TIME)));
        geoLocationJson.put("speed", cursor.getDouble(cursor.getColumnIndex(GpsPointsTable.COLUMN_SPEED)));
        geoLocationJson.put("accuracy", cursor.getInt(cursor.getColumnIndex(GpsPointsTable.COLUMN_ACCURACY)));
        return geoLocationJson;
    }

    @Override
    public Collection<ContentProviderOperation> buildMarkSyncedOperation(final @NonNull JSONObject measurementSlice,
            final @NonNull String authority) throws SynchronisationException {
        final Collection<ContentProviderOperation> updateOperations = new ArrayList<>();
        final Uri tableUri = new Uri.Builder().scheme("content").authority(authority)
                .appendPath(GpsPointsTable.URI_PATH).build();

        try {
            final String measurementIdentifier = measurementSlice.getString("id");
            final JSONArray geoLocationsArray = measurementSlice.getJSONArray("gpsPoints");

            for (int i = 0; i < geoLocationsArray.length(); i++) {
                final JSONObject geoLocation = geoLocationsArray.getJSONObject(i);
                final ContentProviderOperation operation = ContentProviderOperation.newUpdate(tableUri)
                        .withSelection(
                                GpsPointsTable.COLUMN_MEASUREMENT_FK + "=? AND " + GpsPointsTable.COLUMN_GPS_TIME
                                        + "=?",
                                new String[] {measurementIdentifier, geoLocation.getString("timestamp")})
                        .withValue(GpsPointsTable.COLUMN_IS_SYNCED, MeasuringPointsContentProvider.SQLITE_TRUE).build();
                updateOperations.add(operation);
            }
        } catch (final JSONException e) {
            throw new SynchronisationException(e);
        }

        return updateOperations;
    }
}
