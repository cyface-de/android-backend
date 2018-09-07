package de.cyface.synchronization;

import java.util.Collection;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentProviderOperation;
import android.database.Cursor;
import android.support.annotation.NonNull;

import de.cyface.persistence.AccelerationPointTable;

/**
 * Mapper used to parse acceleration points from and to {@link JSONObject}s.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.0
 * @since 2.0.0
 */
final class AccelerationJsonMapper implements JsonMapper {

    private JsonDatabaseMapper jsonDatabaseMapper;

    AccelerationJsonMapper() {
        this.jsonDatabaseMapper = new JsonDatabaseMapper();
    }

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
    public Collection<ContentProviderOperation> buildDeleteDataPointsOperation(final JSONObject measurementSlice,
            final String authority) throws SynchronisationException {

        return jsonDatabaseMapper.buildDeleteOperation(measurementSlice, authority, AccelerationPointTable.URI_PATH,
                "accelerationPoints", AccelerationPointTable.COLUMN_MEASUREMENT_FK, AccelerationPointTable.COLUMN_TIME);
    }
}
