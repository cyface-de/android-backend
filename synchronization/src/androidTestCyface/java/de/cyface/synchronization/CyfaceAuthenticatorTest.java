package de.cyface.synchronization;

import static de.cyface.synchronization.TestUtils.ACCOUNT_TYPE;
import static de.cyface.synchronization.TestUtils.AUTHORITY;
import static de.cyface.synchronization.TestUtils.TEST_API_URL;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import de.cyface.utils.Validate;

/**
 * Tests the internal workings of the {@link CyfaceAuthenticator} using an actual api.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.6
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
@FlakyTest // Flaky means (because of build.gradle) that this test is not executed in the Mock flavour (because it
           // required an actual api)
public class CyfaceAuthenticatorTest {

    private AccountManager accountManager;
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        accountManager = AccountManager.get(context);
    }

    @After
    public void tearDown() {
        final Account[] oldAccounts = accountManager.getAccountsByType(ACCOUNT_TYPE);
        if (oldAccounts.length > 0) {
            for (Account oldAccount : oldAccounts) {
                ContentResolver.removePeriodicSync(oldAccount, AUTHORITY, Bundle.EMPTY);
                Validate.isTrue(accountManager.removeAccountExplicitly(oldAccount));
            }
        }
    }

    /**
     * This test calls an actual api to test if the client can log in and request an authentication token correctly.
     */
    @Test
    public void testGetAuthToken() throws NetworkErrorException {

        // Arrange
        Account requestAccount = new Account(TestUtils.DEFAULT_USERNAME, ACCOUNT_TYPE);
        accountManager.addAccountExplicitly(requestAccount, TestUtils.DEFAULT_PASSWORD, null);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(SyncService.SYNC_ENDPOINT_URL_SETTINGS_KEY, TEST_API_URL);
        editor.apply();

        // Act
        // Explicitly calling CyfaceAuthenticator.getAuthToken(), see its documentation
        Bundle bundle = new CyfaceAuthenticator(context).getAuthToken(null, requestAccount, Constants.AUTH_TOKEN_TYPE,
                null);

        // Assert
        String authToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);
        assertThat(authToken, not(nullValue()));
        assertThat(authToken.isEmpty(), is(false));
        assertThat(authToken.startsWith("ey"), is(true));
    }

}
