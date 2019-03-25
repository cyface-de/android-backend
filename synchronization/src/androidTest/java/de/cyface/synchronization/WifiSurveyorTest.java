/*
 * Copyright 2019 Cyface GmbH
 * This file is part of the Cyface SDK for Android.
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.synchronization;

import static de.cyface.synchronization.TestUtils.ACCOUNT_TYPE;
import static de.cyface.synchronization.TestUtils.AUTHORITY;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import de.cyface.testutils.SharedTestUtils;
import de.cyface.utils.Validate;

/**
 * Tests the correct functionality of the {@link WiFiSurveyor} class.
 * <p>
 * The tests in this class require an emulator or a real device.
 *
 * @author Armin Schnabel
 * @version 1.0.2
 * @since 4.0.0
 */
@RunWith(AndroidJUnit4.class)
public class WifiSurveyorTest {

    /**
     * An object of the class under test.
     */
    private WiFiSurveyor objectUnderTest;
    /**
     * The {@link AccountManager} to check which accounts are registered.
     */
    private AccountManager accountManager;

    /**
     * Initializes the properties for each test case individually.
     */
    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ConnectivityManager connectivityManager = (ConnectivityManager)context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        Validate.notNull(connectivityManager);

        objectUnderTest = new WiFiSurveyor(context, connectivityManager, AUTHORITY, ACCOUNT_TYPE);

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
        objectUnderTest = null;
    }

    /**
     * Tests that marking the connection as syncable using the account flags works.
     * <p>
     * This test reproduced MOV-635 where the periodic sync flag did not change because syncAutomatically was not set.
     * This bug was only reproducible in integration environment (device and emulator) but not as roboelectric test.
     * <p>
     * This test may be flaky on a read device when the network changes during the test.
     */
    @Test
    public void testSetConnected() throws InterruptedException {

        // Arrange
        Account account = objectUnderTest.createAccount(TestUtils.DEFAULT_USERNAME, null);

        // Make sure the new account is in the expected default state
        validateAccountFlags(account);

        // Instead of calling startSurveillance as in production we directly call it's implementation
        // Without the networkCallback or networkConnectivity BroadcastReceiver as this would make this test
        // flaky when the network changes during the test
        objectUnderTest.currentSynchronizationAccount = account;
        objectUnderTest.scheduleSyncNow();
        Thread.sleep(1000); // CI emulator seems to be too slow for less
        validateAccountFlags(account);
        Validate.isTrue(!objectUnderTest.isConnected()); // Ensure default state after startSurveillance

        // Act & Assert 1
        objectUnderTest.setConnected(true);
        Thread.sleep(1000); // CI emulator seems to be to slow for less
        validateAccountFlags(account);
        assertThat(objectUnderTest.isConnected(), is(equalTo(true)));

        // Act & Assert 2
        objectUnderTest.setConnected(false);
        Thread.sleep(1000); // CI emulator seems to be to slow for less
        assertThat(objectUnderTest.isConnected(), is(equalTo(false)));
    }

    /**
     * Makes sure the account flags used by {@link WiFiSurveyor#isConnected()} are valid.
     * <p>
     * See {@link WiFiSurveyor#makeAccountSyncable(Account, boolean)} for details.
     * <p>
     * <b>Attention:</b> Never use this method in production as the periodicSync flags are set async
     * by the system so we can never be sure if they are already set or not. For this reason we have the
     * {@link #testSetConnected()} test.
     *
     * @param account The {@code Account} to be checked.
     */
    private static void validateAccountFlags(@NonNull final Account account) {
        final boolean periodicSyncRegistered = ContentResolver.getPeriodicSyncs(account, TestUtils.AUTHORITY)
                .size() > 0;
        final boolean autoSyncEnabled = ContentResolver.getSyncAutomatically(account, TestUtils.AUTHORITY);
        Validate.isTrue(periodicSyncRegistered == autoSyncEnabled,
                "Both, periodicSync and autoSync, must be in the same state but are: " + periodicSyncRegistered
                        + " and " + autoSyncEnabled + ", in this order");
    }
}
