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

import de.cyface.persistence.MeasuringPointsContentProvider;
import de.cyface.persistence.RotationPointTable;

/**
 * Mapper used to parse rotation points from an to {@link JSONObject}s.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
final class RotationJsonMapper implements JsonMapper {
    @Override
    public JSONObject map(final @NonNull Cursor cursor) throws JSONException {
        final JSONObject json = new JSONObject();
        json.put("rX", cursor.getDouble(cursor.getColumnIndex(RotationPointTable.COLUMN_RX)));
        json.put("rY", cursor.getDouble(cursor.getColumnIndex(RotationPointTable.COLUMN_RY)));
        json.put("rZ", cursor.getDouble(cursor.getColumnIndex(RotationPointTable.COLUMN_RZ)));
        json.put("timestamp", cursor.getLong(cursor.getColumnIndex(RotationPointTable.COLUMN_TIME)));
        return json;
    }

    @Override
    public Collection<ContentProviderOperation> buildMarkSyncedOperation(final @NonNull JSONObject measurementSlice,
            final @NonNull String authority) throws SynchronisationException {
        final Collection<ContentProviderOperation> updateOperations = new ArrayList<>();
        final Uri tableUri = new Uri.Builder().scheme("content").authority(authority)
                .appendPath(RotationPointTable.URI_PATH).build();

        try {
            final String measurementIdentifier = measurementSlice.getString("id");
            final JSONArray rotationsArray = measurementSlice.getJSONArray("rotationPoints");

            for (int i = 0; i < rotationsArray.length(); i++) {
                final JSONObject rotation = rotationsArray.getJSONObject(i);
                final ContentProviderOperation operation = ContentProviderOperation.newUpdate(tableUri)
                        .withSelection(RotationPointTable.COLUMN_MEASUREMENT_FK + "=? AND "
                                + RotationPointTable.COLUMN_TIME + "=?",
                                new String[] {measurementIdentifier, rotation.getString("timestamp")})
                        .withValue(RotationPointTable.COLUMN_IS_SYNCED, MeasuringPointsContentProvider.SQLITE_TRUE)
                        .build();
                updateOperations.add(operation);
            }
        } catch (final JSONException e) {
            throw new SynchronisationException(e);
        }

        return updateOperations;
    }
}
