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
package de.cyface.datacapturing.backend

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Messenger
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.rule.ServiceTestRule
import de.cyface.datacapturing.IgnoreEventsStrategy
import de.cyface.datacapturing.MessageCodes
import de.cyface.datacapturing.PongReceiver
import de.cyface.datacapturing.TestUtils
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.PersistenceBehaviour
import de.cyface.persistence.PersistenceLayer
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.model.Modality
import de.cyface.persistence.strategy.DefaultDistanceCalculation
import de.cyface.persistence.strategy.DefaultLocationCleaning
import de.cyface.synchronization.BundlesExtrasCodes
import de.cyface.testutils.SharedTestUtils.clearPersistenceLayer
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

/**
 * Tests whether the [DataCapturingBackgroundService] handling the data capturing works correctly.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.3.8
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class DataCapturingBackgroundServiceTest {

    /**
     * Junit rule handling the service connection.
     */
    @get:Rule
    var serviceTestRule = ServiceTestRule()

    /**
     * Grants the `ACCESS_FINE_LOCATION` permission while running this test.
     */
    @get:Rule
    var mRuntimePermissionRule: GrantPermissionRule = GrantPermissionRule
        .grant(Manifest.permission.ACCESS_FINE_LOCATION)

    /**
     * The messenger used to receive messages from the data capturing service.
     */
    private var fromServiceMessenger: Messenger? = null

    /**
     * The identifier for the test measurement created in the `setUp` method.
     */
    private var testMeasurement: Measurement? = null

    /**
     * Lock used to synchronize the test case with the background service.
     */
    private var lock: Lock? = null

    /**
     * Condition waiting for the background service to message this service, that it is running.
     */
    private var condition: Condition? = null

    /**
     * The [Context] required to send unique broadcasts, to start the capturing service and more.
     */
    private var context: Context? = null
    private var persistence: PersistenceLayer<PersistenceBehaviour>? = null

    /**
     * Sets up all the instances required by all tests in this test class.
     */
    @Before
    fun setUp() = runBlocking {
        lock = ReentrantLock()
        condition = lock!!.newCondition()
        context = InstrumentationRegistry.getInstrumentation().targetContext

        // This is normally called in the <code>DataCapturingService#Constructor</code>
        persistence = DefaultPersistenceLayer(context!!, CapturingPersistenceBehaviour())
        clearPersistenceLayer(context!!, persistence!!)
        persistence!!.restoreOrCreateDeviceId()
        testMeasurement = persistence!!.newMeasurement(Modality.BICYCLE)
    }

    @After
    fun tearDown() {
        runBlocking { clearPersistenceLayer(context!!, persistence!!) }
        testMeasurement = null
    }

    /**
     * This test case checks that starting the [DataCapturingBackgroundService] works and that the service
     * actually returns some data.
     *
     * @throws TimeoutException if timed out waiting for a successful connection with the service.
     */
    @Test
    @Throws(TimeoutException::class)
    fun testStartDataCapturing() {

        // Arrange (which are normally done by the DataCapturingService which is not part of this test)
        // Instantiate the Messenger for the service connection [ usually in DataCapturingService() ]
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val fromServiceMessageHandler = FromServiceMessageHandler()
            fromServiceMessenger = Messenger(fromServiceMessageHandler)
        }

        // Instantiate ToServiceConnection [ usually in DataCapturingService: BackgroundServiceConnection ]
        val testCallback = TestCallback("testStartDataCapturing", lock!!, condition!!)
        val toServiceConnection = ToServiceConnection(fromServiceMessenger!!)
        toServiceConnection.context = context
        toServiceConnection.callback = testCallback

        // Start and bind DataCapturingBackgroundService [ usually in DCS.runService() ]
        val startIntent = Intent(context, DataCapturingBackgroundService::class.java)
        startIntent.putExtra(BundlesExtrasCodes.MEASUREMENT_ID, testMeasurement!!.id)
        startIntent.putExtra(BundlesExtrasCodes.EVENT_HANDLING_STRATEGY_ID, IgnoreEventsStrategy())
        startIntent.putExtra(
            BundlesExtrasCodes.DISTANCE_CALCULATION_STRATEGY_ID,
            DefaultDistanceCalculation()
        )
        startIntent.putExtra(
            BundlesExtrasCodes.LOCATION_CLEANING_STRATEGY_ID,
            DefaultLocationCleaning()
        )
        startIntent.putExtra(BundlesExtrasCodes.SENSOR_CAPTURE, SensorCaptureEnabled(100))
        val bindIntent = Intent(context, DataCapturingBackgroundService::class.java)
        serviceTestRule.startService(startIntent)
        // bindService() waits for ServiceConnection.onServiceConnected() to be called before returning
        serviceTestRule.bindService(bindIntent, toServiceConnection, 0)

        // Act: Check if DataCapturingBackgroundService is running by sending a Ping
        checkDataCapturingBackgroundServiceRunning(testCallback)

        // Assert
        MatcherAssert.assertThat(
            "It seems that service did not respond to a ping.",
            testCallback.wasRunning(),
            CoreMatchers.`is`(
                CoreMatchers.equalTo(true)
            )
        )
        MatcherAssert.assertThat(
            "It seems that the request to the service whether it was active timed out.",
            testCallback.didTimeOut(), CoreMatchers.`is`(CoreMatchers.equalTo(false))
        )

        // Cleanup: Unbind background service
        serviceTestRule.unbindService()
    }

    /**
     * This test case checks that starting the service works and that the service actually returns some data.
     *
     * @throws TimeoutException if timed out waiting for a successful connection with the service.
     */
    @Test //@FlakyTest // Flaky in the Github CI (2023-03-14)
    @Throws(TimeoutException::class)
    fun testStartDataCapturingTwice() {

        // Arrange
        val testCallback = TestCallback("testStartDataCapturingTwice", lock!!, condition!!)

        // Start background service twice
        val startIntent = Intent(context, DataCapturingBackgroundService::class.java)
        startIntent.putExtra(BundlesExtrasCodes.MEASUREMENT_ID, testMeasurement!!.id)
        startIntent.putExtra(BundlesExtrasCodes.EVENT_HANDLING_STRATEGY_ID, IgnoreEventsStrategy())
        startIntent.putExtra(
            BundlesExtrasCodes.DISTANCE_CALCULATION_STRATEGY_ID,
            DefaultDistanceCalculation()
        )
        startIntent.putExtra(
            BundlesExtrasCodes.LOCATION_CLEANING_STRATEGY_ID,
            DefaultLocationCleaning()
        )
        startIntent.putExtra(BundlesExtrasCodes.SENSOR_CAPTURE, SensorCaptureEnabled(100))
        val bindIntent = Intent(context, DataCapturingBackgroundService::class.java)
        serviceTestRule.startService(startIntent)
        serviceTestRule.bindService(bindIntent)
        serviceTestRule.startService(startIntent)
        serviceTestRule.bindService(bindIntent)

        // Act
        checkDataCapturingBackgroundServiceRunning(testCallback)

        // Assert
        MatcherAssert.assertThat(
            "It seems that service did not respond to a ping.",
            testCallback.wasRunning(),
            CoreMatchers.`is`(
                CoreMatchers.equalTo(true)
            )
        )
        MatcherAssert.assertThat(
            "It seems that the request to the service whether it was active timed out.",
            testCallback.didTimeOut(), CoreMatchers.`is`(CoreMatchers.equalTo(false))
        )
    }

    /**
     * Sends a ping to the [DataCapturingBackgroundService] to check if it's running.
     *
     *
     * As [PongReceiver.checkIsRunningAsync] is async this method waits
     * up to [TestUtils.TIMEOUT_TIME] to receive the pong response from the background service.
     *
     * @param testCallback The [TestCallback] used to check the async result.
     */
    private fun checkDataCapturingBackgroundServiceRunning(testCallback: TestCallback) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val isRunningChecker = PongReceiver(
                context!!, MessageCodes.GLOBAL_BROADCAST_PING,
                MessageCodes.GLOBAL_BROADCAST_PONG
            )
            isRunningChecker.checkIsRunningAsync(
                TestUtils.TIMEOUT_TIME,
                TimeUnit.SECONDS,
                testCallback
            )
        }
        // This must not run on the main thread or it will produce an ANR.
        lock!!.lock()
        try {
            if (!testCallback.wasRunning()) {
                check(
                    condition!!.await(
                        2 * TestUtils.TIMEOUT_TIME,
                        TimeUnit.SECONDS
                    )
                ) { "Waiting for pong or timeout timed out!" }
            }
        } catch (e: InterruptedException) {
            throw IllegalStateException(e)
        } finally {
            lock!!.unlock()
        }
    }
}