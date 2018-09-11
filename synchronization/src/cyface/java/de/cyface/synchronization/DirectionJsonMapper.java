package de.cyface.synchronization;

import java.util.ArrayList;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentProviderOperation;
import android.database.Cursor;
import android.support.annotation.NonNull;

import de.cyface.persistence.DirectionPointTable;

/**
 * Mapper used to parse direction points from and to {@link JSONObject}s.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.0
 * @since 2.0.0
 */
final class DirectionJsonMapper implements JsonMapper {

    private JsonDatabaseMapper jsonDatabaseMapper;

    DirectionJsonMapper() {
        this.jsonDatabaseMapper = new JsonDatabaseMapper();
    }

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
    public ArrayList<ContentProviderOperation> buildDeleteDataPointsOperation(final JSONObject measurementSlice,
                                                                              final String authority) throws SynchronisationException {

        return jsonDatabaseMapper.buildDeleteOperation(measurementSlice, authority, DirectionPointTable.URI_PATH,
                "directionPoints", DirectionPointTable.COLUMN_MEASUREMENT_FK, DirectionPointTable.COLUMN_TIME);
    }
}
