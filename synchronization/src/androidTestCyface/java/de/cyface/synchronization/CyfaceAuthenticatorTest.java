package de.cyface.synchronization;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.FlakyTest;
import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static de.cyface.synchronization.TestUtils.ACCOUNT_TYPE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

@RunWith(AndroidJUnit4.class)
@LargeTest
@FlakyTest
public class CyfaceAuthenticatorTest {

    private static final String TAG ="de.cyface.auth.test";

    @Test
    public void testAuthenticationHappyPath() throws AuthenticatorException, OperationCanceledException, IOException {
        Context context = InstrumentationRegistry.getTargetContext();
        AccountManager manager = AccountManager.get(context);
        Account requestAccount = new Account(TestUtils.DEFAULT_FREE_USERNAME, ACCOUNT_TYPE);
        manager.addAccountExplicitly(requestAccount, TestUtils.DEFAULT_FREE_PASSWORD, null);

        AccountManagerCallback callback = new AccountManagerCallback() {
            @Override
            public void run(AccountManagerFuture future) {
                Log.i(TAG, "Getting auth token finished!");
                try {
                    Log.i(TAG, future.getResult().toString());
                } catch (OperationCanceledException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (AuthenticatorException e) {
                    e.printStackTrace();
                }
            }
        };
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(SyncService.SYNC_ENDPOINT_URL_SETTINGS_KEY, "https://s1.cyface.de/v1/dcs");
        editor.putString(SyncService.DEVICE_IDENTIFIER_KEY, UUID.randomUUID().toString());
        editor.apply();


        AccountManagerFuture<Bundle> future = manager.getAuthToken(requestAccount, Constants.AUTH_TOKEN_TYPE, null, false, callback,null);
        Bundle bundle = future.getResult(10, TimeUnit.SECONDS);

        Log.i(TAG, bundle.toString());

        String authToken = bundle.getString("authtoken");
        assertThat(authToken, not(nullValue()));
        assertThat(authToken.isEmpty(), is(false));
        assertThat(authToken.startsWith("Bearer "), is(true));
    }

}
