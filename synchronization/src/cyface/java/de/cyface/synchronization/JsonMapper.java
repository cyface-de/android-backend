package de.cyface.synchronization;

import java.util.Collection;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentProviderOperation;
import android.database.Cursor;

/**
 * An interface for mappers used to parse measurement points from and to {@link JSONObject}s.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.0
 * @since 2.0.0
 */
public interface JsonMapper {
    /**
     * Transforms measurement points loaded from a {@link Cursor} to a {@link JSONObject}.
     *
     * @param cursor The Cursor to load the data from
     * @return The measurement points as JSONObject
     * @throws JSONException When the parsing fails.
     */
    JSONObject map(Cursor cursor) throws JSONException;

    /**
     * Transforms measurement points available as {@link JSONObject}s to {@link ContentProviderOperation}s
     * which mark the points as synced when executed.
     *
     * @param measurementSlice The measurement slice which holds the points
     * @param authority The authority to access the data in the database.
     * @return A {@link Collection<ContentProviderOperation>}s which marks the points as synced.
     * @throws SynchronisationException When the data could not be mapped.
     */
    Collection<ContentProviderOperation> buildMarkSyncedOperation(JSONObject measurementSlice, String authority)
            throws SynchronisationException;

    /**
     * Transforms measurement points available as {@link JSONObject}s to {@link ContentProviderOperation}s
     * which delete the points when executed.
     * TODO: We'll delete this method again when we implemented the Cyface Byte Code sync.
     *
     * @param measurementSlice The measurement slice which holds the points
     * @param authority The authority to access the data in the database.
     * @return A {@link Collection<ContentProviderOperation>}s which deletes the points.
     * @throws SynchronisationException When the data could not be mapped.
     */
    Collection<ContentProviderOperation> buildDeleteOperation(JSONObject measurementSlice, String authority)
            throws SynchronisationException;
}
