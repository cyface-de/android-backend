package de.cyface.synchronization;

import java.util.Collection;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentProviderOperation;
import android.database.Cursor;
import android.support.annotation.NonNull;

import de.cyface.persistence.RotationPointTable;

/**
 * Mapper used to parse rotation points from and to {@link JSONObject}s.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.0
 * @since 2.0.0
 */
final class RotationJsonMapper implements JsonMapper {

    private JsonDatabaseMapper jsonDatabaseMapper;

    RotationJsonMapper() {
        this.jsonDatabaseMapper = new JsonDatabaseMapper();
    }

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
    public Collection<ContentProviderOperation> buildDeleteDataPointsOperation(final JSONObject measurementSlice,
            final String authority) throws SynchronisationException {

        return jsonDatabaseMapper.buildDeleteOperation(measurementSlice, authority, RotationPointTable.URI_PATH,
                "rotationPoints", RotationPointTable.COLUMN_MEASUREMENT_FK, RotationPointTable.COLUMN_TIME);
    }
}
