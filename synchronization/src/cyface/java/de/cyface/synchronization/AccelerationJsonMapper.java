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

import de.cyface.persistence.AccelerationPointTable;
import de.cyface.persistence.MeasuringPointsContentProvider;

/**
 * Mapper used to parse acceleration points from and to {@link JSONObject}s.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.0
 * @since 2.0.0
 */
final class AccelerationJsonMapper implements JsonMapper {
    @Override
    public JSONObject map(final @NonNull Cursor cursor) throws JSONException {
        final JSONObject json = new JSONObject();
        json.put("ax", cursor.getDouble(cursor.getColumnIndex(AccelerationPointTable.COLUMN_AX)));
        json.put("ay", cursor.getDouble(cursor.getColumnIndex(AccelerationPointTable.COLUMN_AY)));
        json.put("az", cursor.getDouble(cursor.getColumnIndex(AccelerationPointTable.COLUMN_AZ)));
        json.put("timestamp", cursor.getLong(cursor.getColumnIndex(AccelerationPointTable.COLUMN_TIME)));
        return json;
    }

    @Override
    public Collection<ContentProviderOperation> buildMarkSyncedOperation(final @NonNull JSONObject measurementSlice,
            final @NonNull String authority) throws SynchronisationException {
        final Collection<ContentProviderOperation> updateOperations = new ArrayList<>();
        final Uri tableUri = new Uri.Builder().scheme("content").authority(authority)
                .appendPath(AccelerationPointTable.URI_PATH).build();

        try {
            final String measurementIdentifier = measurementSlice.getString("id");
            final JSONArray accelerationsArray = measurementSlice.getJSONArray("accelerationPoints");

            for (int i = 0; i < accelerationsArray.length(); i++) {
                final JSONObject acceleration = accelerationsArray.getJSONObject(i);
                final ContentProviderOperation operation = ContentProviderOperation.newUpdate(tableUri)
                        .withSelection(
                                AccelerationPointTable.COLUMN_MEASUREMENT_FK + "=? AND "
                                        + AccelerationPointTable.COLUMN_TIME + "=?",
                                new String[] {measurementIdentifier, acceleration.getString("timestamp")})
                        .withValue(AccelerationPointTable.COLUMN_IS_SYNCED, MeasuringPointsContentProvider.SQLITE_TRUE)
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
                .appendPath(AccelerationPointTable.URI_PATH).build();

        try {
            final String measurementIdentifier = measurementSlice.getString("id");
            final JSONArray accelerationsArray = measurementSlice.getJSONArray("accelerationPoints");

            for (int i = 0; i < accelerationsArray.length(); i++) {
                final JSONObject acceleration = accelerationsArray.getJSONObject(i);
                final ContentProviderOperation operation = ContentProviderOperation.newDelete(tableUri)
                        .withSelection(
                                AccelerationPointTable.COLUMN_MEASUREMENT_FK + "=? AND "
                                        + AccelerationPointTable.COLUMN_TIME + "=?",
                                new String[] {measurementIdentifier, acceleration.getString("timestamp")})
                        .build();
                deleteOperations.add(operation);
            }
        } catch (final JSONException e) {
            throw new SynchronisationException(e);
        }

        return deleteOperations;
    }
}
