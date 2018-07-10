package de.cyface.synchronization;

import android.content.ContentProviderOperation;
import android.database.Cursor;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;

public interface JsonMapper {
    JSONObject map(Cursor cursor) throws JSONException;
    Collection<ContentProviderOperation> buildMarkSyncedOperation(JSONObject measurementSlice) throws SynchronisationException;
}
