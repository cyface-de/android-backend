package de.cyface.synchronization;

import android.content.ContentProviderOperation;
import android.database.Cursor;
import android.support.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;

import de.cyface.persistence.MagneticValuePointTable;
import de.cyface.persistence.MeasuringPointsContentProvider;

final class DirectionJsonMapper implements JsonMapper {
    @Override
    public JSONObject map(final @NonNull Cursor cursor) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("mX", cursor.getDouble(cursor.getColumnIndex(MagneticValuePointTable.COLUMN_MX)));
        json.put("mY", cursor.getDouble(cursor.getColumnIndex(MagneticValuePointTable.COLUMN_MY)));
        json.put("mZ", cursor.getDouble(cursor.getColumnIndex(MagneticValuePointTable.COLUMN_MZ)));
        json.put("timestamp", cursor.getLong(cursor.getColumnIndex(MagneticValuePointTable.COLUMN_TIME)));
        return json;
    }

    @Override
    public Collection<ContentProviderOperation> buildMarkSyncedOperation(final @NonNull JSONObject measurementSlice)
            throws SynchronisationException {
        Collection<ContentProviderOperation> updateOperations = new ArrayList<>();

        try {
            String measurementIdentifier = measurementSlice.getString("id");
            JSONArray directionArray = measurementSlice.getJSONArray("directionPoints");
            for (int i = 0; i < directionArray.length(); i++) {
                JSONObject direction = directionArray.getJSONObject(i);
                ContentProviderOperation operation = ContentProviderOperation
                        .newUpdate(MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI)
                        .withSelection(
                                MagneticValuePointTable.COLUMN_MEASUREMENT_FK + "=? AND "
                                        + MagneticValuePointTable.COLUMN_TIME + "=?",
                                new String[] {measurementIdentifier, direction.getString("timestamp")})
                        .withValue(MagneticValuePointTable.COLUMN_IS_SYNCED, MeasuringPointsContentProvider.SQLITE_TRUE)
                        .build();
                updateOperations.add(operation);
            }
        } catch (JSONException e) {
            throw new SynchronisationException(e);
        }

        return updateOperations;
    }
}
