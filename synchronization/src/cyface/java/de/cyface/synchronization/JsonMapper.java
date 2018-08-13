package de.cyface.synchronization;

import java.util.Collection;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentProviderOperation;
import android.database.Cursor;

public interface JsonMapper {
    JSONObject map(Cursor cursor) throws JSONException;
    Collection<ContentProviderOperation> buildMarkSyncedOperation(JSONObject measurementSlice, String authority) throws SynchronisationException;
}
