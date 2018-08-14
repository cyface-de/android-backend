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
import de.cyface.persistence.SamplePointTable;

final class AccelerationJsonMapper implements JsonMapper {
    @Override
    public JSONObject map(final @NonNull Cursor cursor) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("ax", cursor.getDouble(cursor.getColumnIndex(SamplePointTable.COLUMN_AX)));
        json.put("ay", cursor.getDouble(cursor.getColumnIndex(SamplePointTable.COLUMN_AY)));
        json.put("az", cursor.getDouble(cursor.getColumnIndex(SamplePointTable.COLUMN_AZ)));
        json.put("timestamp", cursor.getLong(cursor.getColumnIndex(SamplePointTable.COLUMN_TIME)));
        return json;
    }

    @Override
    public Collection<ContentProviderOperation> buildMarkSyncedOperation(final @NonNull JSONObject measurementSlice,
            final @NonNull String authority) throws SynchronisationException {
        Collection<ContentProviderOperation> updateOperations = new ArrayList<>();
        Uri tableUri = new Uri.Builder().scheme("content").authority(authority).appendPath(SamplePointTable.URI_PATH)
                .build();

        try {
            String measurementIdentifier = measurementSlice.getString("id");
            JSONArray accelerationsArray = measurementSlice.getJSONArray("accelerationPoints");
            for (int i = 0; i < accelerationsArray.length(); i++) {
                JSONObject acceleration = accelerationsArray.getJSONObject(i);
                ContentProviderOperation operation = ContentProviderOperation.newUpdate(tableUri)
                        .withSelection(
                                SamplePointTable.COLUMN_MEASUREMENT_FK + "=? AND " + SamplePointTable.COLUMN_TIME
                                        + "=?",
                                new String[] {measurementIdentifier, acceleration.getString("timestamp")})
                        .withValue(SamplePointTable.COLUMN_IS_SYNCED, MeasuringPointsContentProvider.SQLITE_TRUE)
                        .build();
                updateOperations.add(operation);
            }
        } catch (JSONException e) {
            throw new SynchronisationException(e);
        }

        return updateOperations;
    }
}
