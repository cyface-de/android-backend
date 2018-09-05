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

import de.cyface.persistence.MagneticValuePointTable;
import de.cyface.persistence.MeasuringPointsContentProvider;

/**
 * Mapper used to parse direction points from an to {@link JSONObject}s.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
final class DirectionJsonMapper implements JsonMapper {
    @Override
    public JSONObject map(final @NonNull Cursor cursor) throws JSONException {
        final JSONObject json = new JSONObject();
        json.put("mX", cursor.getDouble(cursor.getColumnIndex(MagneticValuePointTable.COLUMN_MX)));
        json.put("mY", cursor.getDouble(cursor.getColumnIndex(MagneticValuePointTable.COLUMN_MY)));
        json.put("mZ", cursor.getDouble(cursor.getColumnIndex(MagneticValuePointTable.COLUMN_MZ)));
        json.put("timestamp", cursor.getLong(cursor.getColumnIndex(MagneticValuePointTable.COLUMN_TIME)));
        return json;
    }

    @Override
    public Collection<ContentProviderOperation> buildMarkSyncedOperation(final @NonNull JSONObject measurementSlice,
            final @NonNull String authority) throws SynchronisationException {
        final Collection<ContentProviderOperation> updateOperations = new ArrayList<>();
        final Uri tableUri = new Uri.Builder().scheme("content").authority(authority)
                .appendPath(MagneticValuePointTable.URI_PATH).build();

        try {
            final String measurementIdentifier = measurementSlice.getString("id");
            final JSONArray directionArray = measurementSlice.getJSONArray("magneticValuePoints");
            for (int i = 0; i < directionArray.length(); i++) {
                final JSONObject direction = directionArray.getJSONObject(i);
                final ContentProviderOperation operation = ContentProviderOperation.newUpdate(tableUri)
                        .withSelection(
                                MagneticValuePointTable.COLUMN_MEASUREMENT_FK + "=? AND "
                                        + MagneticValuePointTable.COLUMN_TIME + "=?",
                                new String[] {measurementIdentifier, direction.getString("timestamp")})
                        .withValue(MagneticValuePointTable.COLUMN_IS_SYNCED, MeasuringPointsContentProvider.SQLITE_TRUE)
                        .build();
                updateOperations.add(operation);
            }
        } catch (final JSONException e) {
            throw new SynchronisationException(e);
        }

        return updateOperations;
    }
}
