/*
 * Copyright 2017-2025 Cyface GmbH
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
package de.cyface.synchronization

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.NetworkErrorException
import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import de.cyface.synchronization.TestUtils.ACCOUNT_TYPE
import de.cyface.synchronization.TestUtils.AUTHORITY
import de.cyface.testutils.SharedTestUtils.cleanupOldAccounts
import de.cyface.utils.Validate.isTrue
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests the internal workings of the [CyfaceAuthenticator] using an actual api.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.2.3
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@Ignore("Because it requires an actual api")
class CyfaceAuthenticatorTest {
    private var accountManager: AccountManager? = null
    private var context: Context? = null

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext

        // Ensure reproducibility
        accountManager = AccountManager.get(context)
        cleanupOldAccounts(accountManager!!, ACCOUNT_TYPE, AUTHORITY)
    }

    @After
    fun tearDown() {
        val oldAccounts = accountManager!!.getAccountsByType(ACCOUNT_TYPE)
        if (oldAccounts.isNotEmpty()) {
            for (oldAccount in oldAccounts) {
                ContentResolver.removePeriodicSync(oldAccount, AUTHORITY, Bundle.EMPTY)
                isTrue(accountManager!!.removeAccountExplicitly(oldAccount))
            }
        }
    }

    /**
     * This test calls an actual api to test if the client can log in and request an authentication token correctly.
     */
    @Test
    @Throws(NetworkErrorException::class)
    fun testGetAuthToken() {
        // Arrange

        val requestAccount = Account(TestUtils.DEFAULT_USERNAME, ACCOUNT_TYPE)
        accountManager!!.addAccountExplicitly(requestAccount, TestUtils.DEFAULT_PASSWORD, null)

        // Act
        // Explicitly calling CyfaceAuthenticator.getAuthToken(), see its documentation
        val bundle = CyfaceAuthenticator(context!!)
            .getAuthToken(null, requestAccount, CyfaceSyncService.AUTH_TOKEN_TYPE, null)

        // Assert
        val authToken = bundle.getString(AccountManager.KEY_AUTHTOKEN)
        MatcherAssert.assertThat(authToken, CoreMatchers.not(CoreMatchers.nullValue()))
        MatcherAssert.assertThat(authToken!!.isEmpty(), CoreMatchers.`is`(false))
        MatcherAssert.assertThat(authToken.startsWith("ey"), CoreMatchers.`is`(true))
    }
}
