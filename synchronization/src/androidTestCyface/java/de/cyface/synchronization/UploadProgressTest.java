package de.cyface.synchronization;

import org.junit.Test;
import org.junit.runner.RunWith;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.BroadcastReceiver;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.FlakyTest;
import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import java.util.UUID;

import de.cyface.persistence.BuildConfig;
import de.cyface.persistence.MeasuringPointsContentProvider;

@RunWith(AndroidJUnit4.class)
@FlakyTest
@LargeTest
public class UploadProgressTest {

    @Test
    public void testUploadProgressHappyPath() {
        Context context = InstrumentationRegistry.getTargetContext();

        CyfaceSyncAdapter syncAdapter = new CyfaceSyncAdapter(context, false);
        AccountManager manager = AccountManager.get(context);
        Account account = new Account(Constants.DEFAULT_FREE_USERNAME, Constants.ACCOUNT_TYPE);
        manager.addAccountExplicitly(account, Constants.DEFAULT_FREE_PASSWORD, null);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(SyncService.SYNC_ENDPOINT_URL_SETTINGS_KEY, "https://s1.cyface.de/v1/dcs");
        editor.putString(SyncService.DEVICE_IDENTIFIER_KEY, UUID.randomUUID().toString());
        editor.apply();
        TestReceiver receiver = new TestReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(CyfaceSyncProgressListener.SYNC_FINISHED);
        filter.addAction(CyfaceSyncProgressListener.SYNC_PROGRESS);
        filter.addAction(CyfaceSyncProgressListener.SYNC_PROGRESS_TRANSMITTED);
        filter.addAction(CyfaceSyncProgressListener.SYNC_PROGRESS_TOTAL);
        filter.addAction(CyfaceSyncProgressListener.SYNC_STARTED);
        filter.addAction(CyfaceSyncProgressListener.SYNC_ERROR_MESSAGE);
        filter.addAction(CyfaceSyncProgressListener.SYNC_EXCEPTION_TYPE);
        filter.addAction(CyfaceSyncProgressListener.SYNC_READ_ERROR);
        filter.addAction(CyfaceSyncProgressListener.SYNC_TRANSMIT_ERROR);
        context.registerReceiver(receiver, filter);

        ContentProviderClient client = null;
        try {
            client = context.getContentResolver()
                    .acquireContentProviderClient(MeasuringPointsContentProvider.GPS_POINTS_URI);
            SyncResult result = new SyncResult();
            syncAdapter.onPerformSync(account, new Bundle(), BuildConfig.provider, client, result);
        } finally {
            if (client != null) {
                client.close();
            }
            context.unregisterReceiver(receiver);
        }
    }
}

class TestReceiver extends BroadcastReceiver {

    private final static String TAG = "de.cyface.sync.test";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        switch (intent.getAction()) {
            case CyfaceSyncProgressListener.SYNC_FINISHED:
                Log.d(TAG, "SYNC FINISHED");
                break;
            case CyfaceSyncProgressListener.SYNC_PROGRESS:
                Log.d(TAG, "SYNC PROGRESS");
                break;
            case CyfaceSyncProgressListener.SYNC_PROGRESS_TRANSMITTED:
                Log.d(TAG, "SYNC PROGRESS TRANSMITTED");
                break;
            case CyfaceSyncProgressListener.SYNC_PROGRESS_TOTAL:
                Log.d(TAG, "SYNC PROGRESS TOTAL");
                break;
            case CyfaceSyncProgressListener.SYNC_STARTED:
                Log.d(TAG, "SYNC STARTED");
                break;
            case CyfaceSyncProgressListener.SYNC_ERROR_MESSAGE:
                Log.d(TAG, "SYNC ERROR MESSAGE");
                break;
            case CyfaceSyncProgressListener.SYNC_EXCEPTION_TYPE:
                Log.d(TAG, "SYNC EXCEPTION TYPE");
                break;
            case CyfaceSyncProgressListener.SYNC_READ_ERROR:
                Log.d(TAG, "SYNC READ ERROR");
                break;
            case CyfaceSyncProgressListener.SYNC_TRANSMIT_ERROR:
                Log.d(TAG, "SYNC TRANSIT ERROR");
                break;
            default:
                throw new IllegalStateException(String.format("Invalid message %s", intent.getAction()));
        }
    }
}
