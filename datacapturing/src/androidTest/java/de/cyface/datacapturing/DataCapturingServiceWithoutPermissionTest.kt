/*
 * Copyright 2018-2023 Cyface GmbH
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

import android.accounts.AccountAuthenticatorActivity
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.FlakyTest
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import de.cyface.datacapturing.exception.CorruptedMeasurementException
import de.cyface.datacapturing.exception.DataCapturingException
import de.cyface.datacapturing.exception.MissingPermissionException
import de.cyface.persistence.SetupException
import de.cyface.persistence.model.Modality
import de.cyface.synchronization.CyfaceAuthenticator
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.json.JSONException
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

/**
 * Checks if missing permissions are correctly detected before starting a service.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.1.10
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
@FlakyTest
@Ignore("Ignore this test until Android is capable of resetting permissions for every test")
class DataCapturingServiceWithoutPermissionTest {
    /**
     * An object of the class under test.
     */
    private var oocut: DataCapturingService? = null

    /**
     * Lock used to synchronize with the background service.
     */
    private var lock: Lock? = null

    /**
     * Condition waiting for the background service to wake up this test case.
     */
    private var condition: Condition? = null

    /**
     * The [Context] needed to access the persistence layer
     */
    private var context: Context? = null

    /**
     * Initializes the object of class under test.
     */
    @Before
    @Throws(JSONException::class)
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext

        // The LOGIN_ACTIVITY is normally set to the LoginActivity of the SDK implementing app
        CyfaceAuthenticator.LOGIN_ACTIVITY = AccountAuthenticatorActivity::class.java
        CyfaceAuthenticator.settings = MockSynchronizationSettings()

        //final String dataUploadServerAddress = "https://localhost:8080/api/v3";
        val listener: DataCapturingListener = TestListener()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                oocut = CyfaceDataCapturingService(
                    context!!,
                    TestUtils.AUTHORITY,
                    TestUtils.ACCOUNT_TYPE,  /*dataUploadServerAddress, TestUtils.oauthConfig(),*/
                    IgnoreEventsStrategy(),
                    listener,
                    100,
                    CyfaceAuthenticator(context!!),
                )
            } catch (e: SetupException) {
                throw IllegalStateException(e)
            }
        }
        lock = ReentrantLock()
        condition = lock!!.newCondition()
    }

    /**
     * Tests that the service correctly throws an `Exception` if no `ACCESS_FINE_LOCATION` was
     * granted.
     *
     * @throws MissingPermissionException The expected `Exception` thrown if the
     * `ACCESS_FINE_LOCATION` is missing.
     * @throws DataCapturingException If the asynchronous background service did not start
     * successfully.
     */
    @Test(expected = MissingPermissionException::class)
    @Throws(
        MissingPermissionException::class,
        DataCapturingException::class,
        CorruptedMeasurementException::class,
    )
    fun testServiceDoesNotStartWithoutPermission() {
        val startUpFinishedHandler = TestStartUpFinishedHandler(
            lock!!,
            condition!!,
            MessageCodes.GLOBAL_BROADCAST_SERVICE_STARTED,
        )
        oocut!!.start(Modality.UNKNOWN, startUpFinishedHandler)
        // if the test fails we might need to wait a bit as we're async
    }

    /**
     * Tests whether a set `UIListener` is correctly informed about a missing permission.
     */
    @Test
    @Throws(CorruptedMeasurementException::class)
    @SuppressWarnings("SwallowedException")
    fun testUIListenerIsInformedOfMissingPermission() {
        val uiListener = TestUIListener()
        oocut!!.uiListener = uiListener

        var exceptionCaught = false
        try {
            val startUpFinishedHandler = TestStartUpFinishedHandler(
                lock!!, condition!!,
                MessageCodes.GLOBAL_BROADCAST_SERVICE_STARTED
            )
            oocut!!.start(Modality.UNKNOWN, startUpFinishedHandler)
        } catch (e: DataCapturingException) {
            MatcherAssert.assertThat(
                uiListener.requiredPermission,
                CoreMatchers.`is`(CoreMatchers.equalTo(true))
            )
            exceptionCaught = true
        } catch (e: MissingPermissionException) {
            MatcherAssert.assertThat(
                uiListener.requiredPermission,
                CoreMatchers.`is`(CoreMatchers.equalTo(true))
            )
            exceptionCaught = true
        }
        MatcherAssert.assertThat(exceptionCaught, CoreMatchers.`is`(CoreMatchers.equalTo(true)))
    }
}
