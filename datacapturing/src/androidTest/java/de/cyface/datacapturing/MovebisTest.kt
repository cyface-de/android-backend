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
package de.cyface.datacapturing

import android.Manifest
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import de.cyface.datacapturing.backend.SensorCaptureEnabled
import de.cyface.testutils.SharedTestUtils.cleanupOldAccounts
import de.cyface.uploader.exception.SynchronisationException
import de.cyface.utils.Validate
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

/**
 * Tests whether the specific features required for the Movebis project work as expected.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.3.8
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class MovebisTest {
    /**
     * Grants the access fine location permission to this test.
     */
    @get:Rule
    var grantFineLocationPermissionRule: GrantPermissionRule = GrantPermissionRule
        .grant(Manifest.permission.ACCESS_FINE_LOCATION)

    /**
     * Grants the access coarse location permission to this test.
     */
    @get:Rule
    var grantCoarseLocationPermissionRule: GrantPermissionRule = GrantPermissionRule
        .grant(Manifest.permission.ACCESS_COARSE_LOCATION)

    /**
     * A `MovebisDataCapturingService` as object of class under test, used for testing.
     */
    private var oocut: MovebisDataCapturingService? = null

    /**
     * A lock used to wait for asynchronous calls to the service, before continuing with the test execution.
     */
    private var lock: Lock? = null

    /**
     * A `Condition` used to wait for a signal from asynchronously called callbacks and listeners before
     * continuing with the test execution.
     */
    private var condition: Condition? = null

    /**
     * A listener catching messages send to the UI in real applications.
     */
    private var testUIListener: TestUIListener? = null

    /**
     * The context of the test installation.
     */
    private var context: Context? = null

    /**
     * Listener for messages from the service. This is used to assert correct service startup and shutdown.
     */
    private var testListener: TestListener? = null

    /**
     * The [AccountManager] to check which accounts are registered.
     */
    private var accountManager: AccountManager? = null

    /**
     * Initializes the object of class under test.
     */
    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        lock = ReentrantLock()
        condition = lock!!.newCondition()
        testListener = TestListener()
        testUIListener = TestUIListener(lock!!, condition!!)
        InstrumentationRegistry.getInstrumentation()
            .runOnMainSync {
                oocut = MovebisDataCapturingService(
                    context!!,
                    TestUtils.AUTHORITY,
                    TestUtils.ACCOUNT_TYPE,
                    testUIListener!!,
                    0L,
                    IgnoreEventsStrategy(),
                    testListener!!,
                    SensorCaptureEnabled(100),
                )
            }

        // Ensure reproducibility
        accountManager = AccountManager.get(context)
        cleanupOldAccounts(accountManager!!, TestUtils.ACCOUNT_TYPE, TestUtils.AUTHORITY)
    }

    @After
    fun tearDown() {
        val oldAccounts = accountManager!!.getAccountsByType(TestUtils.ACCOUNT_TYPE)
        if (oldAccounts.isNotEmpty()) {
            for (oldAccount in oldAccounts) {
                ContentResolver.removePeriodicSync(oldAccount, TestUtils.AUTHORITY, Bundle.EMPTY)
                Validate.isTrue(accountManager!!.removeAccountExplicitly(oldAccount))
            }
        }
    }

    /**
     * Tests that registering a JWT auth token (and with that, creating an account) works.
     *
     *
     * This tests the code used by movebis and reproduced bug MOV-631
     *
     * @throws SynchronisationException This should not happen in the test environment. Occurs if no Android
     * `Context` is available.
     */
    @Test
    @Throws(SynchronisationException::class)
    fun testRegisterJWTAuthToken() {

        // Arrange
        val testUsername = "testUser"
        val testToken = "testToken"

        // Act
        oocut!!.registerJWTAuthToken(testUsername, testToken)

        // Assert - nothing to do - just making sure no exception is thrown
    }

    /**
     * Tests if one lifecycle of starting and stopping location updates works as expected.
     * FlakyTest: This integration test may be dependent on position / location updates on real devices.
     */
    @Test
    @Ignore("Flaky both locally and also on the CI [CY-5713]")
    @SdkSuppress(minSdkVersion = 28) // Only succeeded on (Pixel 2) API 28 emulators (only on the CI)
    fun testUiLocationUpdateLifecycle() {
        InstrumentationRegistry.getInstrumentation()
            .runOnMainSync { oocut!!.startUILocationUpdates() }
        TestUtils.lockAndWait(10L, TimeUnit.SECONDS, lock!!, condition!!)
        oocut!!.stopUILocationUpdates()
        MatcherAssert.assertThat(
            testUIListener!!.receivedUpdates.isEmpty(), CoreMatchers.`is`(
                CoreMatchers.equalTo(false)
            )
        )
    }
}