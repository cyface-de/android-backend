package de.cyface.synchronization;

import java.util.ArrayList;
import java.util.Collection;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentProviderOperation;
import android.database.Cursor;
import android.net.Uri;

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
     * which delete the points when executed.
     *
     * @param measurementSlice The measurement slice which holds the points
     * @param authority The authority to access the data in the database.
     * @return A {@link Collection<ContentProviderOperation>}s which deletes the points.
     * @throws SynchronisationException When the data could not be mapped.
     */
    Collection<ContentProviderOperation> buildDeleteDataPointsOperation(JSONObject measurementSlice, String authority)
            throws SynchronisationException;

    /**
     * A delegate which offers methods to build database operations for {@link JsonMapper}s of measurement data points.
     *
     * @author Armin Schnabel
     * @version 1.0.0
     * @since 2.4.0
     */
    class JsonDatabaseMapper {
        public Collection<ContentProviderOperation> buildDeleteOperation(final JSONObject measurementSlice,
                final String authority, final String uriPath, final String dataPointArrayName,
                final String columnMeasurementFk, final String columnTimestamp) throws SynchronisationException {

            final Collection<ContentProviderOperation> deleteOperations = new ArrayList<>();
            final Uri tableUri = new Uri.Builder().scheme("content").authority(authority).appendPath(uriPath).build();

            try {
                final String measurementIdentifier = measurementSlice.getString("id");
                final JSONArray dataPointArray = measurementSlice.getJSONArray(dataPointArrayName);

                for (int i = 0; i < dataPointArray.length(); i++) {
                    final JSONObject dataPoint = dataPointArray.getJSONObject(i);
                    final ContentProviderOperation operation = ContentProviderOperation.newDelete(tableUri)
                            .withSelection(columnMeasurementFk + "=? AND " + columnTimestamp + "=?",
                                    new String[] {measurementIdentifier, dataPoint.getString("timestamp")})
                            .build();
                    deleteOperations.add(operation);
                }
            } catch (final JSONException e) {
                throw new SynchronisationException(e);
            }

            return deleteOperations;
        }
    }
}
