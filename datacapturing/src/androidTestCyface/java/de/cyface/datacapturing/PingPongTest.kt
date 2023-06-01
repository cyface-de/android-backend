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
package de.cyface.datacapturing

import android.Manifest
import android.app.Activity
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import de.cyface.datacapturing.backend.TestCallback
import de.cyface.datacapturing.exception.CorruptedMeasurementException
import de.cyface.datacapturing.exception.DataCapturingException
import de.cyface.datacapturing.exception.MissingPermissionException
import de.cyface.datacapturing.exception.SetupException
import de.cyface.persistence.DefaultPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.PersistenceBehaviour
import de.cyface.persistence.PersistenceLayer
import de.cyface.persistence.exception.NoSuchMeasurementException
import de.cyface.persistence.model.Modality
import de.cyface.synchronization.CyfaceAuthenticator
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
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

/**
 * This test checks that the ping pong mechanism works as expected. This mechanism ist used to check if a service, in
 * this case the [DataCapturingBackgroundService], is running or not.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.2.12
 * @since 2.3.2
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class PingPongTest {
    /**
     * Grants the permission required by the [DataCapturingService].
     */
    @get:Rule
    var mRuntimePermissionRule = GrantPermissionRule
        .grant(Manifest.permission.ACCESS_FINE_LOCATION)

    /**
     * An instance of the class under test (object of class under test).
     */
    private var oocut: PongReceiver? = null

    /**
     * Lock used to synchronize the asynchronous calls to the [DataCapturingService] with the test thread.
     */
    private var lock: Lock? = null

    /**
     * Condition used to synchronize the asynchronous calls to the [DataCapturingService] with the test thread.
     */
    private var condition: Condition? = null

    /**
     * The [DataCapturingService] instance used by the test to check whether a pong can be received.
     */
    private var dcs: DataCapturingService? = null

    /**
     * The [Context] required to send unique broadcasts and to start the capturing service.
     */
    private var context: Context? = null
    private lateinit var persistence: PersistenceLayer<PersistenceBehaviour>

    /**
     * Sets up all the instances required by all tests in this test class.
     *
     */
    @Before
    fun setUp() {
        lock = ReentrantLock()
        condition = lock!!.newCondition()
        context = InstrumentationRegistry.getInstrumentation().targetContext
        persistence = DefaultPersistenceLayer(context!!, DefaultPersistenceBehaviour())
        oocut = PongReceiver(
            context!!,
            MessageCodes.GLOBAL_BROADCAST_PING,
            MessageCodes.GLOBAL_BROADCAST_PONG
        )
    }

    @After
    fun tearDown() {
        runBlocking { clearPersistenceLayer(context!!, persistence) }
    }

    /**
     * Tests the ping pong with a running service. In that case it should successfully finish one round of ping/pong
     * with that service.
     *
     * @throws MissingPermissionException Should not happen, since there is a JUnit rule to prevent it.
     * @throws DataCapturingException If data capturing was not possible after starting the service.
     * @throws NoSuchMeasurementException If the service lost track of the measurement.
     */
    @Test
    @Throws(
        MissingPermissionException::class,
        DataCapturingException::class,
        NoSuchMeasurementException::class,
        CorruptedMeasurementException::class
    )
    fun testWithRunningService() {

        // Arrange
        // Instantiate DataCapturingService
        val testListener: DataCapturingListener = TestListener()
        // The LOGIN_ACTIVITY is normally set to the LoginActivity of the SDK implementing app
        CyfaceAuthenticator.LOGIN_ACTIVITY = Activity::class.java
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dcs = try {
                CyfaceDataCapturingService(
                    context!!,
                    TestUtils.AUTHORITY,
                    TestUtils.ACCOUNT_TYPE,
                    "https://upload.fake/",
                    "https://auth.fake/",
                    IgnoreEventsStrategy(),
                    testListener,
                    100
                )
            } catch (e: SetupException) {
                throw IllegalStateException(e)
            }
        }

        // Start Capturing
        val finishedHandler: StartUpFinishedHandler = TestStartUpFinishedHandler(
            lock!!, condition!!,
            MessageCodes.GLOBAL_BROADCAST_SERVICE_STARTED
        )
        dcs!!.start(Modality.UNKNOWN, finishedHandler)

        // Give the async start some time to start the DataCapturingBackgroundService
        lock!!.lock()
        try {
            condition!!.await(TestUtils.TIMEOUT_TIME, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            throw IllegalStateException(e)
        } finally {
            lock!!.unlock()
        }

        // Act
        // Check if DataCapturingBackgroundService is running
        val testCallback = TestCallback("testWithRunningService", lock!!, condition!!)
        oocut!!.checkIsRunningAsync(TestUtils.TIMEOUT_TIME, TimeUnit.SECONDS, testCallback)

        // Give the async call some time
        lock!!.lock()
        try {
            condition!!.await(2 * TestUtils.TIMEOUT_TIME, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            throw IllegalStateException(e)
        } finally {
            lock!!.unlock()
        }

        // Assert
        // Ensure DataCapturingBackgroundService was running during the async check
        MatcherAssert.assertThat(
            testCallback.wasRunning(),
            CoreMatchers.`is`(CoreMatchers.equalTo(true))
        )
        MatcherAssert.assertThat(
            testCallback.didTimeOut(),
            CoreMatchers.`is`(CoreMatchers.equalTo(false))
        )

        // Cleanup
        // Stop Capturing
        val shutdownHandler = TestShutdownFinishedHandler(
            lock!!, condition!!,
            MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED
        )
        dcs!!.stop(shutdownHandler)

        // Give the async stop some time to stop gracefully
        lock!!.lock()
        try {
            condition!!.await(TestUtils.TIMEOUT_TIME, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            throw IllegalStateException(e)
        } finally {
            lock!!.unlock()
        }
    }

    /**
     * Tests that the [PongReceiver] works without crashing as expected even when no service runs.
     */
    @Test
    fun testWithNonRunningService() {

        // Act
        // Check if DataCapturingBackgroundService is running
        val testCallback = TestCallback("testWithNonRunningService", lock!!, condition!!)
        oocut!!.checkIsRunningAsync(TestUtils.TIMEOUT_TIME, TimeUnit.SECONDS, testCallback)

        // Give the async call some time
        lock!!.lock()
        try {
            condition!!.await(2 * TestUtils.TIMEOUT_TIME, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            throw IllegalStateException(e)
        } finally {
            lock!!.unlock()
        }

        // Assert
        // Ensure DataCapturingBackgroundService was running during the async check
        MatcherAssert.assertThat(
            testCallback.didTimeOut(),
            CoreMatchers.`is`(CoreMatchers.equalTo(true))
        )
        MatcherAssert.assertThat(
            testCallback.wasRunning(),
            CoreMatchers.`is`(CoreMatchers.equalTo(false))
        )
    }
}