package de.cyface.synchronization;

import static de.cyface.synchronization.Constants.DEVICE_IDENTIFIER_KEY;
import static de.cyface.synchronization.TestUtils.ACCOUNT_TYPE;
import static de.cyface.synchronization.TestUtils.TEST_API_URL;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;

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
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

/**
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.3
 * @since 2.0.0
 */

@RunWith(AndroidJUnit4.class)
@LargeTest
@FlakyTest // Flaky means (because of build.gradle) that this test is not executed in the Mock flavour (because it
           // required an actual api)
public class CyfaceAuthenticatorTest {

    private static final String TAG = "de.cyface.auth.test";

    /**
     * This test calls an actual api to test if the client can log in and request an authentication token correctly.
     */
    @Test
    public void testAuthenticationHappyPath() throws AuthenticatorException, OperationCanceledException, IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AccountManager manager = AccountManager.get(context);
        Account requestAccount = new Account(TestUtils.DEFAULT_USERNAME, ACCOUNT_TYPE);
        manager.addAccountExplicitly(requestAccount, TestUtils.DEFAULT_PASSWORD, null);

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
        editor.putString(SyncService.SYNC_ENDPOINT_URL_SETTINGS_KEY, TEST_API_URL);
        editor.putString(DEVICE_IDENTIFIER_KEY, UUID.randomUUID().toString());
        editor.apply();

        AccountManagerFuture<Bundle> future = manager.getAuthToken(requestAccount, Constants.AUTH_TOKEN_TYPE, null,
                false, callback, null);
        Bundle bundle = future.getResult(10, TimeUnit.SECONDS);

        Log.i(TAG, bundle.toString());

        String authToken = bundle.getString("authtoken");
        assertThat(authToken, not(nullValue()));
        assertThat(authToken.isEmpty(), is(false));
        assertThat(authToken.startsWith("ey"), is(true));
    }

}
