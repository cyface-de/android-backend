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

final class RotationJsonMapper implements JsonMapper {
    @Override
    public JSONObject map(final @NonNull Cursor cursor) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("rX", cursor.getDouble(cursor.getColumnIndex(RotationPointTable.COLUMN_RX)));
        json.put("rY", cursor.getDouble(cursor.getColumnIndex(RotationPointTable.COLUMN_RY)));
        json.put("rZ", cursor.getDouble(cursor.getColumnIndex(RotationPointTable.COLUMN_RZ)));
        json.put("timestamp", cursor.getLong(cursor.getColumnIndex(RotationPointTable.COLUMN_TIME)));
        return json;
    }

    @Override
    public Collection<ContentProviderOperation> buildMarkSyncedOperation(final @NonNull JSONObject measurementSlice, final @NonNull String authority)
            throws SynchronisationException {
        Collection<ContentProviderOperation> updateOperations = new ArrayList<>();

        try {
            String measurementIdentifier = measurementSlice.getString("id");
            JSONArray rotationsArray = measurementSlice.getJSONArray("rotationPoints");
            Uri tableUri = new Uri.Builder().scheme("content").authority(authority).appendPath(RotationPointTable.URI_PATH).build();

            for (int i = 0; i < rotationsArray.length(); i++) {
                JSONObject rotation = rotationsArray.getJSONObject(i);
                ContentProviderOperation operation = ContentProviderOperation
                        .newUpdate(tableUri)
                        .withSelection(
                                RotationPointTable.COLUMN_MEASUREMENT_FK + "=? AND " + RotationPointTable.COLUMN_TIME
                                        + "=?",
                                new String[] {measurementIdentifier,
                                        rotation.getString("timestamp")})
                        .withValue(RotationPointTable.COLUMN_IS_SYNCED, MeasuringPointsContentProvider.SQLITE_TRUE).build();
                updateOperations.add(operation);
            }
        } catch (JSONException e) {
            throw new SynchronisationException(e);
        }

        return updateOperations;
    }
}
