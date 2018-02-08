package de.cyface.synchronization;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

/**
 * Created by muthmann on 06.02.18.
 */
public final class CyfaceSyncAdapter extends AbstractThreadedSyncAdapter {

    private static final String TAG = "de.cyface.sync";

    private final ContentResolver resolver;

    public CyfaceSyncAdapter(final @NonNull Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        resolver = context.getContentResolver();
    }

    public CyfaceSyncAdapter(final @NonNull Context context, boolean autoInitialize, boolean allowParallelSyncs) {
        super(context, autoInitialize, allowParallelSyncs);
        resolver = context.getContentResolver();
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
        Log.d(TAG,"syncing");
    }
}
