/*
 * Copyright 2017-2023 Cyface GmbH
 *
 * This file is part of the Cyface SDK for Android.
 *
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.synchronization;

import static de.cyface.synchronization.CyfaceSyncService.AUTH_TOKEN_TYPE;
import static de.cyface.synchronization.TestUtils.ACCOUNT_TYPE;
import static de.cyface.synchronization.TestUtils.AUTHORITY;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import de.cyface.testutils.SharedTestUtils;
import de.cyface.utils.Validate;
import de.cyface.synchronization.TestUtils;

/**
 * Tests the internal workings of the {@link CyfaceAuthenticator} using an actual api.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.2.2
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
@Ignore("Because it requires an actual api")
public class CyfaceAuthenticatorTest {

    private AccountManager accountManager;
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        // Ensure reproducibility
        accountManager = AccountManager.get(context);
        SharedTestUtils.cleanupOldAccounts(accountManager, ACCOUNT_TYPE, AUTHORITY);
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

        // Act
        // Explicitly calling CyfaceAuthenticator.getAuthToken(), see its documentation
        Bundle bundle = new CyfaceAuthenticator(context)
                .getAuthToken(null, requestAccount, AUTH_TOKEN_TYPE, null);

        // Assert
        String authToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);
        assertThat(authToken, not(nullValue()));
        assertThat(authToken.isEmpty(), is(false));
        assertThat(authToken.startsWith("ey"), is(true));
    }

}
