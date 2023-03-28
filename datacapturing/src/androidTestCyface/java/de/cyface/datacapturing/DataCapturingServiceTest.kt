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
import android.accounts.Account
import android.accounts.AccountAuthenticatorActivity
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.FlakyTest
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.rule.ServiceTestRule
import de.cyface.datacapturing.backend.TestCallback
import de.cyface.datacapturing.exception.CorruptedMeasurementException
import de.cyface.datacapturing.exception.DataCapturingException
import de.cyface.datacapturing.exception.MissingPermissionException
import de.cyface.datacapturing.exception.SetupException
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.PersistenceBehaviour
import de.cyface.persistence.PersistenceLayer
import de.cyface.persistence.exception.NoSuchMeasurementException
import de.cyface.persistence.model.EventType
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.model.MeasurementStatus
import de.cyface.persistence.model.Modality
import de.cyface.synchronization.CyfaceAuthenticator
import de.cyface.testutils.SharedTestUtils.clearPersistenceLayer
import de.cyface.utils.Validate
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

/**
 * Tests whether the [DataCapturingService] works correctly. This is a flaky test since it starts a service that
 * relies on external sensors and the availability of a GNSS signal. Each tests waits a few seconds to actually capture
 * some data, but it might still fail if you use a real device indoors.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 5.7.6
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class DataCapturingServiceTest {
    private var contentResolver: ContentResolver? = null

    /**
     * Rule used to run
     */
    @get:Rule
    val serviceTestRule = ServiceTestRule()

    /**
     * Grants the access location permission to this test.
     */
    @get:Rule
    val grantPermissionRule = GrantPermissionRule
        .grant(Manifest.permission.ACCESS_FINE_LOCATION)

    /**
     * The object of class under test.
     */
    private var oocut: DataCapturingService? = null

    /**
     * Listener for messages from the service. This is used to assert correct service startup and shutdown.
     */
    private var testListener: TestListener? = null

    /**
     * The [Context] needed to access the persistence layer
     */
    private var context: Context? = null

    /**
     * [DefaultPersistenceLayer] required to access stored [Measurement]s.
     */
    private var persistenceLayer: PersistenceLayer<PersistenceBehaviour>? = null

    /**
     * Initializes the super class as well as the object of the class under test and the synchronization lock. This is
     * called prior to every single test case.
     */
    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        contentResolver = context!!.contentResolver
        clearPersistenceLayer(context!!, contentResolver!!, TestUtils.AUTHORITY)

        // The LOGIN_ACTIVITY is normally set to the LoginActivity of the SDK implementing app
        CyfaceAuthenticator.LOGIN_ACTIVITY = AccountAuthenticatorActivity::class.java

        // Add test account
        val requestAccount = Account(TestUtils.DEFAULT_USERNAME, TestUtils.ACCOUNT_TYPE)
        AccountManager.get(context)
            .addAccountExplicitly(requestAccount, TestUtils.DEFAULT_PASSWORD, null)

        // Start DataCapturingService
        testListener = TestListener()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            oocut = try {
                CyfaceDataCapturingService(
                    context!!,
                    TestUtils.AUTHORITY,
                    TestUtils.ACCOUNT_TYPE,
                    "https://localhost:8080",
                    IgnoreEventsStrategy(),
                    testListener!!,
                    100
                )
            } catch (e: SetupException) {
                throw IllegalStateException(e)
            }
        }

        // Prepare
        persistenceLayer =
            DefaultPersistenceLayer(context!!, TestUtils.AUTHORITY, DefaultPersistenceBehaviour())

        // Making sure there is no service instance of a previous test running
        Validate.isTrue(!isDataCapturingServiceRunning)
    }

    /**
     * Tries to stop the DataCapturingService if a test failed to do so.
     *
     * @throws NoSuchMeasurementException If no measurement was [MeasurementStatus.OPEN] or
     * [MeasurementStatus.PAUSED] while stopping the service. This usually occurs if
     * there was no call to
     * [DataCapturingService.start]
     * prior to stopping.
     */
    @After
    @Throws(NoSuchMeasurementException::class)
    fun tearDown() {
        if (isDataCapturingServiceRunning) {

            // Stop zombie
            // Do not reuse the lock/condition!
            val lock: Lock = ReentrantLock()
            val condition = lock.newCondition()
            val shutDownFinishedHandler = TestShutdownFinishedHandler(
                lock,
                condition, MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED
            )
            oocut!!.stop(shutDownFinishedHandler)

            // Ensure the zombie sent a stopped message back to the DataCapturingService
            TestUtils.lockAndWait(
                2, TimeUnit.SECONDS, shutDownFinishedHandler.lock,
                shutDownFinishedHandler.condition
            )
            assertThat(
                shutDownFinishedHandler.receivedServiceStopped(), CoreMatchers.`is`(
                    CoreMatchers.equalTo(true)
                )
            )

            // Ensure that the zombie was not running during the callCheckForRunning
            val isRunning = isDataCapturingServiceRunning
            assertThat(isRunning, CoreMatchers.`is`(CoreMatchers.equalTo(false)))
        }
        clearPersistenceLayer(context!!, contentResolver!!, TestUtils.AUTHORITY)
    }

    /**
     * Makes sure a test did not forget to stop the capturing.
     */
    private val isDataCapturingServiceRunning: Boolean
        get() {

            // Get the current isRunning state (i.e. updates runningStatusCallback). This is important, see #MOV-484.
            // Do not reuse the lock/condition/runningStatusCallback!
            val runningStatusCallbackLock: Lock = ReentrantLock()
            val runningStatusCallbackCondition = runningStatusCallbackLock.newCondition()
            val runningStatusCallback = TestCallback(
                "Default Callback", runningStatusCallbackLock,
                runningStatusCallbackCondition
            )
            TestUtils.callCheckForRunning(oocut!!, runningStatusCallback)
            TestUtils.lockAndWait(
                2, TimeUnit.SECONDS, runningStatusCallback.lock,
                runningStatusCallback.condition
            )
            return runningStatusCallback.wasRunning() && !runningStatusCallback.didTimeOut()
        }

    /**
     * Starts a [DataCapturingService] and checks that it's running afterwards.
     *
     * @return the measurement id of the started capturing
     * @throws DataCapturingException If the asynchronous background service did not start successfully or no valid
     * Android context was available.
     * @throws MissingPermissionException If no Android `ACCESS_FINE_LOCATION` has been granted. You may
     * register a [de.cyface.datacapturing.ui.UIListener] to ask the user for this permission and prevent the
     * `Exception`. If the `Exception` was thrown the service does not start.
     */
    @Throws(
        MissingPermissionException::class,
        DataCapturingException::class,
        CorruptedMeasurementException::class
    )
    private fun startAndCheckThatLaunched(): Long {

        // Do not reuse the lock/condition!
        val lock: Lock = ReentrantLock()
        val condition = lock.newCondition()
        val startUpFinishedHandler = TestStartUpFinishedHandler(
            lock, condition,
            MessageCodes.GLOBAL_BROADCAST_SERVICE_STARTED
        )
        oocut!!.start(Modality.UNKNOWN, startUpFinishedHandler)
        return checkThatLaunched(startUpFinishedHandler)
    }

    /**
     * Pauses a [DataCapturingService] and checks that it's not running afterwards.
     *
     * @param measurementIdentifier The if of the measurement expected to be closed.
     * @throws NoSuchMeasurementException If no measurement was [MeasurementStatus.OPEN] while pausing the
     * service. This usually occurs if there was no call to
     * [DataCapturingService.start] prior to
     * pausing.
     */
    @Throws(NoSuchMeasurementException::class)
    private fun pauseAndCheckThatStopped(measurementIdentifier: Long) {

        // Do not reuse the lock/condition!
        val lock: Lock = ReentrantLock()
        val condition = lock.newCondition()
        val shutDownFinishedHandler = TestShutdownFinishedHandler(
            lock, condition,
            MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED
        )
        oocut!!.pause(shutDownFinishedHandler)
        checkThatStopped(shutDownFinishedHandler, measurementIdentifier)
    }

    /**
     * Resumes a [DataCapturingService] and checks that it's running afterwards.
     *
     * @param measurementIdentifier The id of the measurement which is expected to be resumed
     * @throws DataCapturingException If starting the background service was not successful.
     * @throws MissingPermissionException If permission to access geo location via satellite has not been granted or
     * revoked. The current measurement is closed if you receive this `Exception`. If you get the
     * permission in the future you need to start a new measurement and not call `resumeSync`
     * again.
     * @throws NoSuchMeasurementException If no measurement was [MeasurementStatus.OPEN] while pausing the
     * service. This usually occurs if there was no call to
     * [DataCapturingService.start] prior to
     * pausing.
     */
    @Throws(
        MissingPermissionException::class,
        DataCapturingException::class,
        NoSuchMeasurementException::class
    )
    private fun resumeAndCheckThatLaunched(measurementIdentifier: Long) {

        // Do not reuse the lock/condition!
        val lock: Lock = ReentrantLock()
        val condition = lock.newCondition()
        val startUpFinishedHandler = TestStartUpFinishedHandler(
            lock, condition,
            MessageCodes.GLOBAL_BROADCAST_SERVICE_STARTED
        )
        oocut!!.resume(startUpFinishedHandler)
        val resumedMeasurementId = checkThatLaunched(startUpFinishedHandler)
        assertThat(resumedMeasurementId, CoreMatchers.`is`(measurementIdentifier))
    }

    /**
     * Stops a [DataCapturingService] and checks that it's not running afterwards.
     *
     * @param measurementIdentifier The if of the measurement expected to be closed.
     *
     * @throws NoSuchMeasurementException If no measurement was [MeasurementStatus.OPEN] or
     * [MeasurementStatus.PAUSED] while stopping the service. This usually occurs if
     * there was no call to
     * [DataCapturingService.start]
     * prior to stopping.
     */
    @Throws(NoSuchMeasurementException::class)
    private fun stopAndCheckThatStopped(measurementIdentifier: Long) {

        // Do not reuse the lock/condition!
        val lock: Lock = ReentrantLock()
        val condition = lock.newCondition()
        val shutDownFinishedHandler = TestShutdownFinishedHandler(
            lock, condition,
            MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED
        )
        oocut!!.stop(shutDownFinishedHandler)
        checkThatStopped(shutDownFinishedHandler, measurementIdentifier)
    }

    /**
     * Checks that a [DataCapturingService] actually started after calling the life-cycle method
     * [DataCapturingService.start] or
     * [DataCapturingService.resume]
     *
     * @param startUpFinishedHandler The [TestStartUpFinishedHandler] which was used to start the service
     * @return The id of the measurement which was started
     */
    private fun checkThatLaunched(startUpFinishedHandler: TestStartUpFinishedHandler): Long {

        // Ensure the DataCapturingBackgroundService sent a started message back to the DataCapturingService
        TestUtils.lockAndWait(
            2, TimeUnit.SECONDS, startUpFinishedHandler.lock,
            startUpFinishedHandler.condition
        )
        assertThat(
            startUpFinishedHandler.receivedServiceStarted(), CoreMatchers.`is`(
                CoreMatchers.equalTo(true)
            )
        )

        // Ensure that the DataCapturingBackgroundService was running during the callCheckForRunning
        val isRunning = isDataCapturingServiceRunning
        assertThat(isRunning, CoreMatchers.`is`(CoreMatchers.equalTo(true)))

        // Return the id of the started measurement
        assertThat(
            startUpFinishedHandler.receivedMeasurementIdentifier, CoreMatchers.`is`(
                CoreMatchers.not(CoreMatchers.equalTo(-1L))
            )
        )
        return startUpFinishedHandler.receivedMeasurementIdentifier
    }

    /**
     * Checks that a [DataCapturingService] actually stopped after calling the life-cycle method
     * [DataCapturingService.stop] or
     * [DataCapturingService.pause].
     *
     * Also checks that the measurement which was stopped is the expected measurement.
     *
     * @param shutDownFinishedHandler The [TestShutdownFinishedHandler] which was used to stop the service
     * @param measurementIdentifier The id of the measurement which was expected to be stopped by the references
     * life-cycle call
     */
    private fun checkThatStopped(
        shutDownFinishedHandler: TestShutdownFinishedHandler,
        measurementIdentifier: Long
    ) {

        // Ensure the DataCapturingBackgroundService sent a stopped message back to the DataCapturingService
        TestUtils.lockAndWait(
            2, TimeUnit.SECONDS, shutDownFinishedHandler.lock,
            shutDownFinishedHandler.condition
        )
        assertThat(
            shutDownFinishedHandler.receivedServiceStopped(), CoreMatchers.`is`(
                CoreMatchers.equalTo(true)
            )
        )

        // Ensure that the DataCapturingBackgroundService was not running during the callCheckForRunning
        val isRunning = isDataCapturingServiceRunning
        assertThat(isRunning, CoreMatchers.`is`(CoreMatchers.equalTo(false)))

        // Ensure that the expected measurement stopped
        assertThat(
            shutDownFinishedHandler.receivedMeasurementIdentifier, CoreMatchers.`is`(
                CoreMatchers.equalTo(measurementIdentifier)
            )
        )
    }

    /**
     * Tests a common service run.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test
    @Throws(
        DataCapturingException::class,
        MissingPermissionException::class,
        NoSuchMeasurementException::class,
        CorruptedMeasurementException::class
    )
    fun testStartStop() {
        val receivedMeasurementIdentifier = startAndCheckThatLaunched()
        stopAndCheckThatStopped(receivedMeasurementIdentifier)
    }

    /**
     * Tests that a double start-stop combination with waiting for the callback does not break the service.
     *
     * Makes sure the [DataCapturingService.pause] and
     * [DataCapturingService.resume] work correctly.
     *
     * @throws DataCapturingException Happens on unexpected states during data capturing.
     * @throws MissingPermissionException Should not happen since a `GrantPermissionRule` is used.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test
    @Throws(
        DataCapturingException::class,
        MissingPermissionException::class,
        NoSuchMeasurementException::class,
        CorruptedMeasurementException::class
    )
    fun testMultipleStartStopWithDelay() {
        val measurementIdentifier = startAndCheckThatLaunched()
        var measurements: List<Measurement?> = persistenceLayer!!.loadMeasurements()
        assertThat(measurements.size, CoreMatchers.`is`(CoreMatchers.equalTo(1)))
        stopAndCheckThatStopped(measurementIdentifier)
        val measurementIdentifier2 = startAndCheckThatLaunched()
        measurements = persistenceLayer!!.loadMeasurements()
        assertThat(measurements.size, CoreMatchers.`is`(CoreMatchers.equalTo(2)))
        stopAndCheckThatStopped(measurementIdentifier2)
    }

    /**
     * Tests that a double start-stop combination without waiting for the callback does not break the service.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test
    @Ignore(
        """This test fails as our library currently runs lifecycle tasks (start/stop) in parallel.
To fix this we need to re-use a handler for a sequential execution. See CY-4098, MOV-378
We should consider refactoring the code before to use startCommandReceived as intended CY-4097."""
    )
    @Throws(
        DataCapturingException::class,
        MissingPermissionException::class,
        NoSuchMeasurementException::class,
        CorruptedMeasurementException::class
    )
    fun testMultipleStartStopWithoutDelay() {

        // Do not reuse the lock/condition!
        val lock1: Lock = ReentrantLock()
        val condition1 = lock1.newCondition()
        val startUpFinishedHandler1 = TestStartUpFinishedHandler(
            lock1, condition1,
            MessageCodes.GLOBAL_BROADCAST_SERVICE_STARTED
        )
        // Do not reuse the lock/condition!
        val lock2: Lock = ReentrantLock()
        val condition2 = lock2.newCondition()
        val startUpFinishedHandler2 = TestStartUpFinishedHandler(
            lock2, condition2,
            MessageCodes.GLOBAL_BROADCAST_SERVICE_STARTED
        )
        // Do not reuse the lock/condition!
        val lock3: Lock = ReentrantLock()
        val condition3 = lock3.newCondition()
        val startUpFinishedHandler3 = TestStartUpFinishedHandler(
            lock3, condition3,
            MessageCodes.GLOBAL_BROADCAST_SERVICE_STARTED
        )
        // Do not reuse the lock/condition!
        val lock4: Lock = ReentrantLock()
        val condition4 = lock4.newCondition()
        val shutDownFinishedHandler1 = TestShutdownFinishedHandler(
            lock4, condition4,
            MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED
        )
        // Do not reuse the lock/condition!
        val lock5: Lock = ReentrantLock()
        val condition5 = lock5.newCondition()
        val shutDownFinishedHandler2 = TestShutdownFinishedHandler(
            lock5, condition5,
            MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED
        )
        // Do not reuse the lock/condition!
        val lock6: Lock = ReentrantLock()
        val condition6 = lock6.newCondition()
        val shutDownFinishedHandler3 = TestShutdownFinishedHandler(
            lock6, condition6,
            MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED
        )

        // First Start/stop without waiting
        oocut!!.start(Modality.UNKNOWN, startUpFinishedHandler1)
        oocut!!.stop(shutDownFinishedHandler1)
        // Second start/stop without waiting
        oocut!!.start(Modality.UNKNOWN, startUpFinishedHandler2)
        oocut!!.stop(shutDownFinishedHandler2)
        // Second start/stop without waiting
        oocut!!.start(Modality.UNKNOWN, startUpFinishedHandler3)
        oocut!!.stop(shutDownFinishedHandler3)

        // Now let's make sure all measurements started and stopped as expected
        TestUtils.lockAndWait(
            2, TimeUnit.SECONDS, startUpFinishedHandler1.lock,
            startUpFinishedHandler1.condition
        )
        TestUtils.lockAndWait(
            2, TimeUnit.SECONDS, shutDownFinishedHandler1.lock,
            shutDownFinishedHandler1.condition
        )
        TestUtils.lockAndWait(
            2, TimeUnit.SECONDS, startUpFinishedHandler2.lock,
            startUpFinishedHandler2.condition
        )
        TestUtils.lockAndWait(
            2, TimeUnit.SECONDS, shutDownFinishedHandler2.lock,
            shutDownFinishedHandler2.condition
        )
        TestUtils.lockAndWait(
            2, TimeUnit.SECONDS, startUpFinishedHandler3.lock,
            startUpFinishedHandler3.condition
        )
        TestUtils.lockAndWait(
            2, TimeUnit.SECONDS, shutDownFinishedHandler3.lock,
            shutDownFinishedHandler3.condition
        )
        val measurements = persistenceLayer!!.loadMeasurements()
        assertThat(measurements.size, CoreMatchers.`is`(CoreMatchers.equalTo(3)))
        val measurementId1 = startUpFinishedHandler1.receivedMeasurementIdentifier
        assertThat(
            measurements[0].id,
            CoreMatchers.`is`(CoreMatchers.equalTo(measurementId1))
        )
        val measurementId2 = startUpFinishedHandler2.receivedMeasurementIdentifier
        assertThat(
            measurements[1].id,
            CoreMatchers.`is`(CoreMatchers.equalTo(measurementId2))
        )
        val measurementId3 = startUpFinishedHandler3.receivedMeasurementIdentifier
        assertThat(
            measurements[2].id,
            CoreMatchers.`is`(CoreMatchers.equalTo(measurementId3))
        )
        assertThat(
            measurementId1,
            CoreMatchers.`is`(CoreMatchers.not(CoreMatchers.equalTo(-1L)))
        )
        assertThat(
            shutDownFinishedHandler1.receivedMeasurementIdentifier, CoreMatchers.`is`(
                CoreMatchers.equalTo(measurementId1)
            )
        )
        assertThat(
            measurementId2,
            CoreMatchers.`is`(CoreMatchers.not(CoreMatchers.equalTo(-1L)))
        )
        assertThat(
            shutDownFinishedHandler2.receivedMeasurementIdentifier, CoreMatchers.`is`(
                CoreMatchers.equalTo(measurementId2)
            )
        )
        assertThat(
            measurementId3,
            CoreMatchers.`is`(CoreMatchers.not(CoreMatchers.equalTo(-1L)))
        )
        assertThat(
            shutDownFinishedHandler3.receivedMeasurementIdentifier, CoreMatchers.`is`(
                CoreMatchers.equalTo(measurementId3)
            )
        )
    }

    /**
     * Tests a common service run with an intermediate disconnect and reconnect by the application. No problems should
     * occur and some points should be captured.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test
    @Throws(
        DataCapturingException::class,
        MissingPermissionException::class,
        NoSuchMeasurementException::class,
        CorruptedMeasurementException::class
    )
    fun testDisconnectReconnect() {
        val measurementIdentifier = startAndCheckThatLaunched()
        oocut!!.disconnect()
        assertThat(
            oocut!!.reconnect(DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT), CoreMatchers.`is`(
                CoreMatchers.equalTo(true)
            )
        )
        stopAndCheckThatStopped(measurementIdentifier)
    }

    /**
     * Tests that running startSync twice does not break the system. This test succeeds if no `Exception`
     * occurs. Must be supported (#MOV-460).
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test
    @Throws(
        DataCapturingException::class,
        MissingPermissionException::class,
        NoSuchMeasurementException::class,
        CorruptedMeasurementException::class
    )
    fun testDoubleStart() {
        val measurementIdentifier = startAndCheckThatLaunched()

        // Second start - should not launch anything
        // Do not reuse the lock/condition!
        val lock: Lock = ReentrantLock()
        val condition = lock.newCondition()
        val startUpFinishedHandler = TestStartUpFinishedHandler(
            lock, condition,
            MessageCodes.GLOBAL_BROADCAST_SERVICE_STARTED
        )
        oocut!!.start(Modality.UNKNOWN, startUpFinishedHandler)
        TestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition)
        assertThat(
            startUpFinishedHandler.receivedServiceStarted(), CoreMatchers.`is`(
                CoreMatchers.equalTo(false)
            )
        )
        stopAndCheckThatStopped(measurementIdentifier)
    }

    /**
     * Tests that stopping a stopped service throws the expected exception.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test(expected = NoSuchMeasurementException::class)
    @Throws(
        DataCapturingException::class,
        MissingPermissionException::class,
        NoSuchMeasurementException::class,
        CorruptedMeasurementException::class
    )
    fun testDoubleStop() {
        val measurementId = startAndCheckThatLaunched()
        stopAndCheckThatStopped(measurementId)
        // Do not reuse the lock/condition!
        val lock: Lock = ReentrantLock()
        val condition = lock.newCondition()
        // must throw NoSuchMeasurementException
        oocut!!.stop(
            TestShutdownFinishedHandler(
                lock,
                condition,
                MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED
            )
        )
    }

    /**
     * Tests for the correct `Exception` if you try to disconnect from a disconnected service.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test(expected = DataCapturingException::class)
    @Throws(
        DataCapturingException::class,
        MissingPermissionException::class,
        NoSuchMeasurementException::class,
        CorruptedMeasurementException::class
    )
    fun testDoubleDisconnect() {
        val measurementIdentifier = startAndCheckThatLaunched()
        oocut!!.disconnect()
        oocut!!.disconnect() // must throw DataCapturingException
        stopAndCheckThatStopped(measurementIdentifier) // is called by tearDown
    }

    /**
     * Tests that no `Exception` occurs if you stop a disconnected service.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test
    @Throws(
        DataCapturingException::class,
        MissingPermissionException::class,
        NoSuchMeasurementException::class,
        CorruptedMeasurementException::class
    )
    fun testStopNonConnectedService() {
        val measurementIdentifier = startAndCheckThatLaunched()
        oocut!!.disconnect()
        stopAndCheckThatStopped(measurementIdentifier)
    }

    /**
     * Tests that no `Exception` is thrown when we try to connect to the same service twice.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test
    @Throws(
        DataCapturingException::class,
        MissingPermissionException::class,
        NoSuchMeasurementException::class,
        CorruptedMeasurementException::class
    )
    fun testDoubleReconnect() {
        val measurementIdentifier = startAndCheckThatLaunched()
        oocut!!.disconnect()
        assertThat(
            oocut!!.reconnect(DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT),
            CoreMatchers.`is`(true)
        )
        assertThat(
            oocut!!.reconnect(DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT),
            CoreMatchers.`is`(true)
        )
        stopAndCheckThatStopped(measurementIdentifier)
    }

    /**
     * Tests that two correct cycles of disconnect and reconnect on a running service work fine.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test
    @Throws(
        DataCapturingException::class,
        MissingPermissionException::class,
        NoSuchMeasurementException::class,
        CorruptedMeasurementException::class
    )
    fun testDisconnectReconnectTwice() {
        val measurementIdentifier = startAndCheckThatLaunched()
        oocut!!.disconnect()
        assertThat(
            oocut!!.reconnect(DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT),
            CoreMatchers.`is`(true)
        )
        oocut!!.disconnect()
        assertThat(
            oocut!!.reconnect(DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT),
            CoreMatchers.`is`(true)
        )
        stopAndCheckThatStopped(measurementIdentifier)
    }

    /**
     * Tests that starting a service twice throws no `Exception`.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test
    @Throws(
        DataCapturingException::class,
        MissingPermissionException::class,
        NoSuchMeasurementException::class,
        CorruptedMeasurementException::class
    )
    fun testRestart() {
        val measurementIdentifier = startAndCheckThatLaunched()
        stopAndCheckThatStopped(measurementIdentifier)
        val measurementIdentifier2 = startAndCheckThatLaunched()
        assertThat(
            measurementIdentifier2,
            CoreMatchers.not(CoreMatchers.equalTo(measurementIdentifier))
        )
        stopAndCheckThatStopped(measurementIdentifier2)
    }

    /**
     * Tests that calling resume two times in a row works without causing any errors. The second call to resume should
     * just do nothing. Must be supported (#MOV-460).
     *
     * @throws MissingPermissionException If permission to access geo location sensor is missing.
     * @throws DataCapturingException If any unexpected error occurs during the test.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test
    @Throws(
        MissingPermissionException::class,
        DataCapturingException::class,
        NoSuchMeasurementException::class,
        CorruptedMeasurementException::class
    )
    fun testResumeTwice() {

        // Start, pause
        val measurementIdentifier = startAndCheckThatLaunched()
        pauseAndCheckThatStopped(measurementIdentifier)

        // Resume 1
        resumeAndCheckThatLaunched(measurementIdentifier)

        // Resume 2: must be ignored by resumeAsync
        val persistence = DefaultPersistenceLayer(
            context!!, TestUtils.AUTHORITY, CapturingPersistenceBehaviour()
        )
        // Do not reuse the lock/condition!
        val lock: Lock = ReentrantLock()
        val condition = lock.newCondition()
        val startUpFinishedHandler = TestStartUpFinishedHandler(
            lock, condition,
            MessageCodes.GLOBAL_BROADCAST_SERVICE_STARTED
        )
        oocut!!.resume(startUpFinishedHandler)
        val isRunning = isDataCapturingServiceRunning
        assertThat(isRunning, CoreMatchers.`is`(CoreMatchers.equalTo(true)))
        assertThat(
            persistence.loadMeasurementStatus(measurementIdentifier), CoreMatchers.`is`(
                CoreMatchers.equalTo(MeasurementStatus.OPEN)
            )
        )
        stopAndCheckThatStopped(measurementIdentifier)
        assertThat(
            persistence.loadMeasurementStatus(measurementIdentifier), CoreMatchers.`is`(
                CoreMatchers.equalTo(MeasurementStatus.FINISHED)
            )
        )
    }

    /**
     * Tests that stopping a paused service does work successfully.
     *
     * @throws MissingPermissionException If the test is missing the permission to access the geo location sensor.
     * @throws DataCapturingException If any unexpected error occurs.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test
    @Throws(
        MissingPermissionException::class,
        DataCapturingException::class,
        NoSuchMeasurementException::class,
        CorruptedMeasurementException::class
    )
    fun testStartPauseStop() {
        val measurementIdentifier = startAndCheckThatLaunched()
        pauseAndCheckThatStopped(measurementIdentifier)
        stopAndCheckThatStopped(measurementIdentifier) // stop paused returns mid, too [STAD-333]
    }

    /**
     * Tests that stopping a paused service does work successfully.
     *
     * As this test was flaky MOV-527, we have this test here which executes it multiple times.
     *
     * @throws MissingPermissionException If the test is missing the permission to access the geo location sensor.
     * @throws DataCapturingException If any unexpected error occurs.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test
    @Ignore("Not needed to be executed automatically as MOV-527 made the normal tests flaky")
    @Throws(
        MissingPermissionException::class,
        DataCapturingException::class,
        NoSuchMeasurementException::class,
        CorruptedMeasurementException::class
    )
    fun testStartPauseStop_MultipleTimes() {
        for (i in 0..19) {
            Log.d(TestUtils.TAG, "ITERATION: $i")
            val measurementIdentifier = startAndCheckThatLaunched()
            pauseAndCheckThatStopped(measurementIdentifier)
            stopAndCheckThatStopped(measurementIdentifier) // stop paused returns mid, too [STAD-333]
        }
    }

    /**
     * Tests that removing the [DataCapturingListener] during capturing does not stop the
     * [de.cyface.datacapturing.backend.DataCapturingBackgroundService].
     *
     * @throws MissingPermissionException If the test is missing the permission to access the geo location sensor.
     * @throws DataCapturingException If any unexpected error occurs.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test
    @Throws(
        MissingPermissionException::class,
        DataCapturingException::class,
        NoSuchMeasurementException::class,
        CorruptedMeasurementException::class
    )
    fun testRemoveDataCapturingListener() {
        val measurementIdentifier = startAndCheckThatLaunched()
        // This happens in SDK implementing apps (SR) when the app is paused and resumed
        oocut!!.removeDataCapturingListener(testListener!!)
        oocut!!.addDataCapturingListener(testListener!!)
        // Should not happen, we test it anyways
        oocut!!.addDataCapturingListener(testListener!!)
        pauseAndCheckThatStopped(measurementIdentifier)
        // Should not happen, we test it anyways
        oocut!!.removeDataCapturingListener(testListener!!)
        // Should not happen, we test it anyways
        oocut!!.removeDataCapturingListener(testListener!!)
        resumeAndCheckThatLaunched(measurementIdentifier)
        stopAndCheckThatStopped(measurementIdentifier)
    }

    /**
     * Tests if the service lifecycle is running successfully and that the life-cycle [de.cyface.persistence.model.Event]s are logged.
     *
     * Makes sure the [DataCapturingService.pause] and
     * [DataCapturingService.resume] work correctly.
     *
     * @throws DataCapturingException Happens on unexpected states during data capturing.
     * @throws MissingPermissionException Should not happen since a `GrantPermissionRule` is used.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test
    @Throws(
        DataCapturingException::class,
        MissingPermissionException::class,
        NoSuchMeasurementException::class,
        CorruptedMeasurementException::class
    )
    fun testStartPauseResumeStop_EventsAreLogged() {
        val measurementIdentifier = startPauseResumeStop()
        val events = oocut!!.persistenceLayer.loadEvents(measurementIdentifier)
        // start, pause, resume, stop and initial MODALITY_TYPE_CHANGE event
        assertThat(events.size, CoreMatchers.`is`(CoreMatchers.equalTo(5)))
        assertThat(
            events[0]!!.type,
            CoreMatchers.`is`(CoreMatchers.equalTo(EventType.LIFECYCLE_START))
        )
        assertThat(
            events[1]!!.type,
            CoreMatchers.`is`(CoreMatchers.equalTo(EventType.MODALITY_TYPE_CHANGE))
        )
        assertThat(
            events[2]!!.type,
            CoreMatchers.`is`(CoreMatchers.equalTo(EventType.LIFECYCLE_PAUSE))
        )
        assertThat(
            events[3]!!.type,
            CoreMatchers.`is`(CoreMatchers.equalTo(EventType.LIFECYCLE_RESUME))
        )
        assertThat(
            events[4]!!.type,
            CoreMatchers.`is`(CoreMatchers.equalTo(EventType.LIFECYCLE_STOP))
        )
    }

    /**
     * Tests if the service lifecycle is running successfully and that the life-cycle [de.cyface.persistence.model.Event]s are logged.
     *
     * Makes sure the [DataCapturingService.pause] and
     * [DataCapturingService.resume] work correctly.
     *
     * As this test was flaky MOV-527, we have this test here which executes it multiple times.
     *
     * @throws DataCapturingException Happens on unexpected states during data capturing.
     * @throws MissingPermissionException Should not happen since a `GrantPermissionRule` is used.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Ignore("Not needed to be executed automatically as MOV-527 made the normal tests flaky")
    @Test
    @Throws(
        DataCapturingException::class,
        MissingPermissionException::class,
        NoSuchMeasurementException::class,
        CorruptedMeasurementException::class
    )
    fun testStartPauseResumeStop_MultipleTimes() {
        for (i in 0..49) {
            Log.d(TestUtils.TAG, "ITERATION: $i")
            startPauseResumeStop()

            // For for-i-loops within this test
            runBlocking {
                clearPersistenceLayer(context!!, persistenceLayer!!)
            }
        }
    }

    @Throws(
        DataCapturingException::class,
        NoSuchMeasurementException::class,
        CorruptedMeasurementException::class,
        MissingPermissionException::class
    )
    private fun startPauseResumeStop(): Long {
        val measurementIdentifier = startAndCheckThatLaunched()
        val measurements = persistenceLayer!!.loadMeasurements()
        assertThat(measurements.size, CoreMatchers.`is`(CoreMatchers.equalTo(1)))
        pauseAndCheckThatStopped(measurementIdentifier)
        resumeAndCheckThatLaunched(measurementIdentifier)
        val newMeasurements = persistenceLayer!!.loadMeasurements()
        assertThat(
            measurements.size == newMeasurements.size, CoreMatchers.`is`(
                CoreMatchers.equalTo(true)
            )
        )
        stopAndCheckThatStopped(measurementIdentifier)

        runBlocking {
            // Check Events
            val events =
                persistenceLayer!!.eventRepository!!.loadAllByMeasurementId(measurementIdentifier)
            assertThat(events!!.size, CoreMatchers.`is`(CoreMatchers.equalTo(5)))
            assertThat(
                events[0].type,
                CoreMatchers.`is`(CoreMatchers.equalTo(EventType.LIFECYCLE_START))
            )
            assertThat(
                events.get(1).type,
                CoreMatchers.`is`(CoreMatchers.equalTo(EventType.MODALITY_TYPE_CHANGE))
            )
            assertThat(
                events.get(1).value,
                CoreMatchers.`is`(CoreMatchers.equalTo(Modality.UNKNOWN.databaseIdentifier))
            )
            assertThat(
                events.get(2).type,
                CoreMatchers.`is`(CoreMatchers.equalTo(EventType.LIFECYCLE_PAUSE))
            )
            assertThat(
                events.get(3).type,
                CoreMatchers.`is`(CoreMatchers.equalTo(EventType.LIFECYCLE_RESUME))
            )
            assertThat(
                events.get(4).type,
                CoreMatchers.`is`(CoreMatchers.equalTo(EventType.LIFECYCLE_STOP))
            )
        }
        return measurementIdentifier
    }

    /**
     * Tests whether actual sensor data is captured after running the method
     * [CyfaceDataCapturingService.start] ()}.
     * In bug #CY-3862 only the [DataCapturingService] was started and measurements created
     * but no sensor data was captured as the [de.cyface.datacapturing.backend.DataCapturingBackgroundService]
     * was not started. The cause was: disables sensor capturing.
     *
     * This test is Flaky because it's success depends on if sensor data was captured during the
     * lockAndWait timeout. It's large because multiple seconds are waited until during the test.
     *
     * @throws DataCapturingException If any unexpected errors occur during data capturing.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test
    @LargeTest
    @FlakyTest
    @Throws(
        DataCapturingException::class,
        MissingPermissionException::class,
        NoSuchMeasurementException::class,
        CorruptedMeasurementException::class,
        InterruptedException::class
    )
    fun testSensorDataCapturing() {
        val measurementIdentifier = startAndCheckThatLaunched()

        // Check sensor data
        val measurements = persistenceLayer!!.loadMeasurements()
        assertThat(
            measurements.size > 0,
            CoreMatchers.`is`(CoreMatchers.equalTo(true))
        )
        Thread.sleep(3000L)
        assertThat(
            testListener!!.capturedData.size > 0, CoreMatchers.`is`(
                CoreMatchers.equalTo(true)
            )
        )
        stopAndCheckThatStopped(measurementIdentifier)
    }

    /**
     * Tests whether reconnect throws no exception when called without a running background service and leaves the
     * DataCapturingService in the correct state (`isDataCapturingServiceRunning` is `false`.
     */
    @Test
    fun testReconnectOnNonRunningServer() {
        assertThat(
            oocut!!.reconnect(DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT),
            CoreMatchers.`is`(false)
        )
        assertThat(oocut!!.isRunning, CoreMatchers.`is`(CoreMatchers.equalTo(false)))
    }

    /**
     * Test that checks that the [DataCapturingService] constructor only accepts API URls with "https://" as
     * protocol.
     *
     *
     * We had twice the problem that SDK implementors used no or a false protocol. This test ensures that
     * our code throws a hard exception if this happens again which should help to identify this prior to release.
     */
    @Test(expected = SetupException::class)
    @Throws(SetupException::class)
    fun testDataCapturingService_doesNotAcceptUrlWithoutProtocol() {
        CyfaceDataCapturingService(
            context!!, TestUtils.AUTHORITY,
            TestUtils.ACCOUNT_TYPE, "localhost:8080", IgnoreEventsStrategy(), testListener!!, 100
        )
    }

    /**
     * Tests that starting a new `Measurement` and changing the `Modality` during runtime creates two
     * [EventType.MODALITY_TYPE_CHANGE] entries.
     *
     * @throws MissingPermissionException If the test is missing the permission to access the geo location sensor.
     * @throws DataCapturingException If any unexpected error occurs.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test
    @Throws(
        MissingPermissionException::class,
        DataCapturingException::class,
        NoSuchMeasurementException::class,
        CorruptedMeasurementException::class
    )
    fun testChangeModality_EventLogContainsTwoModalities() {
        val measurementIdentifier = startAndCheckThatLaunched()
        oocut!!.changeModalityType(Modality.CAR)
        stopAndCheckThatStopped(measurementIdentifier)
        val modalityTypeChanges = oocut!!.persistenceLayer.loadEvents(
            measurementIdentifier,
            EventType.MODALITY_TYPE_CHANGE
        )
        assertThat(
            modalityTypeChanges!!.size,
            CoreMatchers.`is`(CoreMatchers.equalTo(2))
        )
        assertThat(
            modalityTypeChanges[0].value, CoreMatchers.`is`(
                CoreMatchers.equalTo(
                    Modality.UNKNOWN.databaseIdentifier
                )
            )
        )
        assertThat(
            modalityTypeChanges[1].value, CoreMatchers.`is`(
                CoreMatchers.equalTo(
                    Modality.CAR.databaseIdentifier
                )
            )
        )
    }

    /**
     * Tests that changing to the same `Modality` twice does not produce a new
     * [EventType.MODALITY_TYPE_CHANGE] `Event`.
     *
     * @throws MissingPermissionException If the test is missing the permission to access the geo location sensor.
     * @throws DataCapturingException If any unexpected error occurs.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test
    @Throws(
        MissingPermissionException::class,
        DataCapturingException::class,
        NoSuchMeasurementException::class,
        CorruptedMeasurementException::class
    )
    fun testChangeModalityToSameModalityTwice_EventLogStillContainsOnlyTwoModalities() {
        val measurementIdentifier = startAndCheckThatLaunched()
        oocut!!.changeModalityType(Modality.CAR)
        oocut!!.changeModalityType(Modality.CAR)
        stopAndCheckThatStopped(measurementIdentifier)
        val modalityTypeChanges = oocut!!.persistenceLayer.loadEvents(
            measurementIdentifier,
            EventType.MODALITY_TYPE_CHANGE
        )
        assertThat(
            modalityTypeChanges!!.size,
            CoreMatchers.`is`(CoreMatchers.equalTo(2))
        )
    }

    /**
     * Tests that changing `Modality` during a [EventType.LIFECYCLE_PAUSE] works as expected.
     *
     * @throws MissingPermissionException If the test is missing the permission to access the geo location sensor.
     * @throws DataCapturingException If any unexpected error occurs.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test
    @Throws(
        MissingPermissionException::class,
        DataCapturingException::class,
        NoSuchMeasurementException::class,
        CorruptedMeasurementException::class
    )
    fun testChangeModalityWhilePaused_EventLogStillContainsModalityChange() {
        val measurementIdentifier = startAndCheckThatLaunched()
        pauseAndCheckThatStopped(measurementIdentifier)
        oocut!!.changeModalityType(Modality.CAR)
        stopAndCheckThatStopped(measurementIdentifier) // stop paused returns mid, too [STAD-333]
        val modalityTypeChanges = oocut!!.persistenceLayer.loadEvents(
            measurementIdentifier,
            EventType.MODALITY_TYPE_CHANGE
        )
        assertThat(
            modalityTypeChanges!!.size,
            CoreMatchers.`is`(CoreMatchers.equalTo(2))
        )
        assertThat(
            modalityTypeChanges[0].value, CoreMatchers.`is`(
                CoreMatchers.equalTo(
                    Modality.UNKNOWN.databaseIdentifier
                )
            )
        )
        assertThat(
            modalityTypeChanges[1].value, CoreMatchers.`is`(
                CoreMatchers.equalTo(
                    Modality.CAR.databaseIdentifier
                )
            )
        )
    }
}