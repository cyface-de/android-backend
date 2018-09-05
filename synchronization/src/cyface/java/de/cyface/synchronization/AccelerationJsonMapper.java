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

/**
 * Mapper used to parse acceleration points from an to {@link JSONObject}s.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
final class AccelerationJsonMapper implements JsonMapper {
    @Override
    public JSONObject map(final @NonNull Cursor cursor) throws JSONException {
        final JSONObject json = new JSONObject();
        json.put("ax", cursor.getDouble(cursor.getColumnIndex(SamplePointTable.COLUMN_AX)));
        json.put("ay", cursor.getDouble(cursor.getColumnIndex(SamplePointTable.COLUMN_AY)));
        json.put("az", cursor.getDouble(cursor.getColumnIndex(SamplePointTable.COLUMN_AZ)));
        json.put("timestamp", cursor.getLong(cursor.getColumnIndex(SamplePointTable.COLUMN_TIME)));
        return json;
    }

    @Override
    public Collection<ContentProviderOperation> buildMarkSyncedOperation(final @NonNull JSONObject measurementSlice,
            final @NonNull String authority) throws SynchronisationException {
        final Collection<ContentProviderOperation> updateOperations = new ArrayList<>();
        final Uri tableUri = new Uri.Builder().scheme("content").authority(authority)
                .appendPath(SamplePointTable.URI_PATH).build();

        try {
            final String measurementIdentifier = measurementSlice.getString("id");
            final JSONArray accelerationsArray = measurementSlice.getJSONArray("accelerationPoints");

            for (int i = 0; i < accelerationsArray.length(); i++) {
                final JSONObject acceleration = accelerationsArray.getJSONObject(i);
                final ContentProviderOperation operation = ContentProviderOperation.newUpdate(tableUri)
                        .withSelection(
                                SamplePointTable.COLUMN_MEASUREMENT_FK + "=? AND " + SamplePointTable.COLUMN_TIME
                                        + "=?",
                                new String[] {measurementIdentifier, acceleration.getString("timestamp")})
                        .withValue(SamplePointTable.COLUMN_IS_SYNCED, MeasuringPointsContentProvider.SQLITE_TRUE)
                        .build();
                updateOperations.add(operation);
            }
        } catch (final JSONException e) {
            throw new SynchronisationException(e);
        }

        return updateOperations;
    }
}
