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

import de.cyface.persistence.DirectionPointTable;
import de.cyface.persistence.MeasuringPointsContentProvider;

/**
 * Mapper used to parse direction points from and to {@link JSONObject}s.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.0
 * @since 2.0.0
 */
final class DirectionJsonMapper implements JsonMapper {
    @Override
    public JSONObject map(final @NonNull Cursor cursor) throws JSONException {
        final JSONObject json = new JSONObject();
        json.put("mX", cursor.getDouble(cursor.getColumnIndex(DirectionPointTable.COLUMN_MX)));
        json.put("mY", cursor.getDouble(cursor.getColumnIndex(DirectionPointTable.COLUMN_MY)));
        json.put("mZ", cursor.getDouble(cursor.getColumnIndex(DirectionPointTable.COLUMN_MZ)));
        json.put("timestamp", cursor.getLong(cursor.getColumnIndex(DirectionPointTable.COLUMN_TIME)));
        return json;
    }

    @Override
    public Collection<ContentProviderOperation> buildMarkSyncedOperation(final @NonNull JSONObject measurementSlice,
            final @NonNull String authority) throws SynchronisationException {
        final Collection<ContentProviderOperation> updateOperations = new ArrayList<>();
        final Uri tableUri = new Uri.Builder().scheme("content").authority(authority)
                .appendPath(DirectionPointTable.URI_PATH).build();

        try {
            final String measurementIdentifier = measurementSlice.getString("id");
            final JSONArray directionArray = measurementSlice.getJSONArray("directionPoints");
            for (int i = 0; i < directionArray.length(); i++) {
                final JSONObject direction = directionArray.getJSONObject(i);
                final ContentProviderOperation operation = ContentProviderOperation.newUpdate(tableUri)
                        .withSelection(DirectionPointTable.COLUMN_MEASUREMENT_FK + "=? AND "
                                + DirectionPointTable.COLUMN_TIME + "=?",
                                new String[] {measurementIdentifier, direction.getString("timestamp")})
                        .withValue(DirectionPointTable.COLUMN_IS_SYNCED, MeasuringPointsContentProvider.SQLITE_TRUE)
                        .build();
                updateOperations.add(operation);
            }
        } catch (final JSONException e) {
            throw new SynchronisationException(e);
        }

        return updateOperations;
    }

    @Override
    public Collection<ContentProviderOperation> buildDeleteOperation(JSONObject measurementSlice, String authority)
            throws SynchronisationException {
        final Collection<ContentProviderOperation> deleteOperations = new ArrayList<>();
        final Uri tableUri = new Uri.Builder().scheme("content").authority(authority)
                .appendPath(DirectionPointTable.URI_PATH).build();

        try {
            final String measurementIdentifier = measurementSlice.getString("id");
            final JSONArray directionsArray = measurementSlice.getJSONArray("directionPoints");

            for (int i = 0; i < directionsArray.length(); i++) {
                final JSONObject direction = directionsArray.getJSONObject(i);
                final ContentProviderOperation operation = ContentProviderOperation.newDelete(tableUri)
                        .withSelection(DirectionPointTable.COLUMN_MEASUREMENT_FK + "=? AND "
                                + DirectionPointTable.COLUMN_TIME + "=?",
                                new String[] {measurementIdentifier, direction.getString("timestamp")})
                        .build();
                deleteOperations.add(operation);
            }
        } catch (final JSONException e) {
            throw new SynchronisationException(e);
        }

        return deleteOperations;
    }
}
