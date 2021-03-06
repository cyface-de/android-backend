/*
 * Copyright 2017 Cyface GmbH
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
package de.cyface.datacapturing;

import static de.cyface.datacapturing.TestUtils.ACCOUNT_TYPE;
import static de.cyface.datacapturing.TestUtils.AUTHORITY;
import static de.cyface.datacapturing.TestUtils.TAG;
import static de.cyface.persistence.Utils.getEventUri;
import static de.cyface.persistence.model.MeasurementStatus.FINISHED;
import static de.cyface.persistence.model.MeasurementStatus.OPEN;
import static de.cyface.persistence.model.Modality.CAR;
import static de.cyface.persistence.model.Modality.UNKNOWN;
import static de.cyface.utils.CursorIsNullException.softCatchNullCursor;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.BaseColumns;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.rule.ServiceTestRule;
import androidx.test.rule.provider.ProviderTestRule;

import de.cyface.datacapturing.backend.DataCapturingBackgroundService;
import de.cyface.datacapturing.backend.TestCallback;
import de.cyface.datacapturing.exception.CorruptedMeasurementException;
import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.exception.MissingPermissionException;
import de.cyface.datacapturing.exception.SetupException;
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour;
import de.cyface.datacapturing.ui.UIListener;
import de.cyface.persistence.DefaultPersistenceBehaviour;
import de.cyface.persistence.EventTable;
import de.cyface.persistence.MeasuringPointsContentProvider;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.Event;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.Modality;
import de.cyface.synchronization.CyfaceAuthenticator;
import de.cyface.testutils.SharedTestUtils;
import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;

/**
 * Tests whether the {@link DataCapturingService} works correctly. This is a flaky test since it starts a service that
 * relies on external sensors and the availability of a GNSS signal. Each tests waits a few seconds to actually capture
 * some data, but it might still fail if you are indoors (which you will usually be while running tests, right?)
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 5.7.1
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class DataCapturingServiceTest {

    /**
     * Test rule that provides a mock connection to a <code>ContentProvider</code> to test against.
     */
    @Rule
    public ProviderTestRule providerRule = new ProviderTestRule.Builder(MeasuringPointsContentProvider.class, AUTHORITY)
            .build();
    /**
     * Rule used to run
     */
    @Rule
    public ServiceTestRule serviceTestRule = new ServiceTestRule();
    /**
     * Grants the access location permission to this test.
     */
    @Rule
    public GrantPermissionRule grantPermissionRule = GrantPermissionRule
            .grant(android.Manifest.permission.ACCESS_FINE_LOCATION);
    /**
     * The object of class under test.
     */
    private DataCapturingService oocut;
    /**
     * Listener for messages from the service. This is used to assert correct service startup and shutdown.
     */
    private TestListener testListener;
    /**
     * The {@link Context} needed to access the persistence layer
     */
    private Context context;
    /**
     * {@link PersistenceLayer} required to access stored {@link Measurement}s.
     */
    private PersistenceLayer<DefaultPersistenceBehaviour> persistenceLayer;
    /**
     * A device-wide unique identifier for the application containing this SDK such as
     * {@code Context#getPackageName()} which is required to generate unique global broadcasts for this app.
     * <p>
     * <b>Attention:</b> The identifier must be identical in the global broadcast sender and receiver.
     */
    private String appId;

    /**
     * Initializes the super class as well as the object of the class under test and the synchronization lock. This is
     * called prior to every single test case.
     */
    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        // The LOGIN_ACTIVITY is normally set to the LoginActivity of the SDK implementing app
        CyfaceAuthenticator.LOGIN_ACTIVITY = AccountAuthenticatorActivity.class;

        // Add test account
        final Account requestAccount = new Account(TestUtils.DEFAULT_USERNAME, TestUtils.ACCOUNT_TYPE);
        AccountManager.get(context).addAccountExplicitly(requestAccount, TestUtils.DEFAULT_PASSWORD, null);

        // Start DataCapturingService
        testListener = new TestListener();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                try {
                    oocut = new CyfaceDataCapturingService(context, context.getContentResolver(), AUTHORITY,
                            ACCOUNT_TYPE, "https://localhost:8080", new IgnoreEventsStrategy(), testListener, 100);
                } catch (SetupException | CursorIsNullException e) {
                    throw new IllegalStateException(e);
                }
            }
        });

        // Prepare
        SharedTestUtils.clearPersistenceLayer(context, context.getContentResolver(), AUTHORITY);
        persistenceLayer = new PersistenceLayer<>(context, context.getContentResolver(), AUTHORITY,
                new DefaultPersistenceBehaviour());
        appId = context.getPackageName();

        // Making sure there is no service instance of a previous test running
        Validate.isTrue(!isDataCapturingServiceRunning());
    }

    /**
     * Tries to stop the DataCapturingService if a test failed to do so.
     *
     * @throws NoSuchMeasurementException If no measurement was {@link MeasurementStatus#OPEN} or
     *             {@link MeasurementStatus#PAUSED} while stopping the service. This usually occurs if
     *             there was no call to
     *             {@link DataCapturingService#start(Modality, StartUpFinishedHandler)}
     *             prior to stopping.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @After
    public void tearDown() throws CursorIsNullException, NoSuchMeasurementException {
        if (isDataCapturingServiceRunning()) {

            // Stop zombie
            // Do not reuse the lock/condition!
            final Lock lock = new ReentrantLock();
            final Condition condition = lock.newCondition();
            final TestShutdownFinishedHandler shutDownFinishedHandler = new TestShutdownFinishedHandler(lock,
                    condition, MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED);
            oocut.stop(shutDownFinishedHandler);

            // Ensure the zombie sent a stopped message back to the DataCapturingService
            TestUtils.lockAndWait(2, TimeUnit.SECONDS, shutDownFinishedHandler.getLock(),
                    shutDownFinishedHandler.getCondition());
            assertThat(shutDownFinishedHandler.receivedServiceStopped(), is(equalTo(true)));

            // Ensure that the zombie was not running during the callCheckForRunning
            final boolean isRunning = isDataCapturingServiceRunning();
            assertThat(isRunning, is(equalTo(false)));
        }

        SharedTestUtils.clearPersistenceLayer(context, context.getContentResolver(), AUTHORITY);
    }

    /**
     * Makes sure a test did not forget to stop the capturing.
     */
    private boolean isDataCapturingServiceRunning() {

        // Get the current isRunning state (i.e. updates runningStatusCallback). This is important, see #MOV-484.
        // Do not reuse the lock/condition/runningStatusCallback!
        final Lock runningStatusCallbackLock = new ReentrantLock();
        final Condition runningStatusCallbackCondition = runningStatusCallbackLock.newCondition();
        final TestCallback runningStatusCallback = new TestCallback("Default Callback", runningStatusCallbackLock,
                runningStatusCallbackCondition);
        TestUtils.callCheckForRunning(oocut, runningStatusCallback);
        TestUtils.lockAndWait(2, TimeUnit.SECONDS, runningStatusCallback.getLock(),
                runningStatusCallback.getCondition());

        return runningStatusCallback.wasRunning() && !runningStatusCallback.didTimeOut();
    }

    /**
     * Starts a {@link DataCapturingService} and checks that it's running afterwards.
     *
     * @return the measurement id of the started capturing
     * @throws DataCapturingException If the asynchronous background service did not start successfully or no valid
     *             Android context was available.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     * @throws MissingPermissionException If no Android <code>ACCESS_FINE_LOCATION</code> has been granted. You may
     *             register a {@link UIListener} to ask the user for this permission and prevent the
     *             <code>Exception</code>. If the <code>Exception</code> was thrown the service does not start.
     */
    private long startAndCheckThatLaunched() throws MissingPermissionException, DataCapturingException,
            CursorIsNullException, CorruptedMeasurementException {

        // Do not reuse the lock/condition!
        final Lock lock = new ReentrantLock();
        final Condition condition = lock.newCondition();
        final TestStartUpFinishedHandler startUpFinishedHandler = new TestStartUpFinishedHandler(lock, condition,
                MessageCodes.getServiceStartedActionId(appId));
        oocut.start(UNKNOWN, startUpFinishedHandler);

        return checkThatLaunched(startUpFinishedHandler);
    }

    /**
     * Pauses a {@link DataCapturingService} and checks that it's not running afterwards.
     *
     * @param measurementIdentifier The if of the measurement expected to be closed.
     * @throws NoSuchMeasurementException If no measurement was {@link MeasurementStatus#OPEN} while pausing the
     *             service. This usually occurs if there was no call to
     *             {@link DataCapturingService#start(Modality, StartUpFinishedHandler)} prior to
     *             pausing.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    private void pauseAndCheckThatStopped(long measurementIdentifier)
            throws NoSuchMeasurementException, CursorIsNullException {

        // Do not reuse the lock/condition!
        final Lock lock = new ReentrantLock();
        final Condition condition = lock.newCondition();
        final TestShutdownFinishedHandler shutDownFinishedHandler = new TestShutdownFinishedHandler(lock, condition,
                MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED);
        oocut.pause(shutDownFinishedHandler);

        checkThatStopped(shutDownFinishedHandler, measurementIdentifier);
    }

    /**
     * Resumes a {@link DataCapturingService} and checks that it's running afterwards.
     *
     * @param measurementIdentifier The id of the measurement which is expected to be resumed
     * @throws DataCapturingException If starting the background service was not successful.
     * @throws MissingPermissionException If permission to access geo location via satellite has not been granted or
     *             revoked. The current measurement is closed if you receive this <code>Exception</code>. If you get the
     *             permission in the future you need to start a new measurement and not call <code>resumeSync</code>
     *             again.
     * @throws NoSuchMeasurementException If no measurement was {@link MeasurementStatus#OPEN} while pausing the
     *             service. This usually occurs if there was no call to
     *             {@link DataCapturingService#start(Modality, StartUpFinishedHandler)} prior to
     *             pausing.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    private void resumeAndCheckThatLaunched(long measurementIdentifier) throws MissingPermissionException,
            DataCapturingException, CursorIsNullException, NoSuchMeasurementException {

        // Do not reuse the lock/condition!
        final Lock lock = new ReentrantLock();
        final Condition condition = lock.newCondition();
        final TestStartUpFinishedHandler startUpFinishedHandler = new TestStartUpFinishedHandler(lock, condition,
                MessageCodes.getServiceStartedActionId(appId));
        oocut.resume(startUpFinishedHandler);

        final long resumedMeasurementId = checkThatLaunched(startUpFinishedHandler);
        assertThat(resumedMeasurementId, is(measurementIdentifier));
    }

    /**
     * Stops a {@link DataCapturingService} and checks that it's not running afterwards.
     *
     * @param measurementIdentifier The if of the measurement expected to be closed.
     *
     * @throws NoSuchMeasurementException If no measurement was {@link MeasurementStatus#OPEN} or
     *             {@link MeasurementStatus#PAUSED} while stopping the service. This usually occurs if
     *             there was no call to
     *             {@link DataCapturingService#start(Modality, StartUpFinishedHandler)}
     *             prior to stopping.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    private void stopAndCheckThatStopped(final long measurementIdentifier)
            throws NoSuchMeasurementException, CursorIsNullException {

        // Do not reuse the lock/condition!
        final Lock lock = new ReentrantLock();
        final Condition condition = lock.newCondition();
        final TestShutdownFinishedHandler shutDownFinishedHandler = new TestShutdownFinishedHandler(lock, condition,
                MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED);
        oocut.stop(shutDownFinishedHandler);

        checkThatStopped(shutDownFinishedHandler, measurementIdentifier);
    }

    /**
     * Checks that a {@link DataCapturingService} actually started after calling the life-cycle method
     * {@link DataCapturingService#start(Modality, StartUpFinishedHandler)} or
     * {@link DataCapturingService#resume(StartUpFinishedHandler)}
     *
     * @param startUpFinishedHandler The {@link TestStartUpFinishedHandler} which was used to start the service
     * @return The id of the measurement which was started
     */
    private long checkThatLaunched(final TestStartUpFinishedHandler startUpFinishedHandler) {

        // Ensure the DataCapturingBackgroundService sent a started message back to the DataCapturingService
        TestUtils.lockAndWait(2, TimeUnit.SECONDS, startUpFinishedHandler.getLock(),
                startUpFinishedHandler.getCondition());
        assertThat(startUpFinishedHandler.receivedServiceStarted(), is(equalTo(true)));

        // Ensure that the DataCapturingBackgroundService was running during the callCheckForRunning
        final boolean isRunning = isDataCapturingServiceRunning();
        assertThat(isRunning, is(equalTo(true)));

        // Return the id of the started measurement
        assertThat(startUpFinishedHandler.receivedMeasurementIdentifier, is(not(equalTo(-1L))));
        return startUpFinishedHandler.receivedMeasurementIdentifier;
    }

    /**
     * Checks that a {@link DataCapturingService} actually stopped after calling the life-cycle method
     * {@link DataCapturingService#stop(ShutDownFinishedHandler)} or
     * {@link DataCapturingService#pause(ShutDownFinishedHandler)}.
     *
     * Also checks that the measurement which was stopped is the expected measurement.
     *
     * @param shutDownFinishedHandler The {@link TestShutdownFinishedHandler} which was used to stop the service
     * @param measurementIdentifier The id of the measurement which was expected to be stopped by the references
     *            life-cycle call
     */
    private void checkThatStopped(final TestShutdownFinishedHandler shutDownFinishedHandler,
            final long measurementIdentifier) {

        // Ensure the DataCapturingBackgroundService sent a stopped message back to the DataCapturingService
        TestUtils.lockAndWait(2, TimeUnit.SECONDS, shutDownFinishedHandler.getLock(),
                shutDownFinishedHandler.getCondition());
        assertThat(shutDownFinishedHandler.receivedServiceStopped(), is(equalTo(true)));

        // Ensure that the DataCapturingBackgroundService was not running during the callCheckForRunning
        final boolean isRunning = isDataCapturingServiceRunning();
        assertThat(isRunning, is(equalTo(false)));

        // Ensure that the expected measurement stopped
        assertThat(shutDownFinishedHandler.receivedMeasurementIdentifier, is(equalTo(measurementIdentifier)));
    }

    /**
     * Tests a common service run.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test
    public void testStartStop() throws DataCapturingException, MissingPermissionException, NoSuchMeasurementException,
            CursorIsNullException, CorruptedMeasurementException {

        final long receivedMeasurementIdentifier = startAndCheckThatLaunched();
        stopAndCheckThatStopped(receivedMeasurementIdentifier);
    }

    /**
     * Tests that a double start-stop combination with waiting for the callback does not break the service.
     * <p>
     * Makes sure the {@link DataCapturingService#pause(ShutDownFinishedHandler)} and
     * {@link DataCapturingService#resume(StartUpFinishedHandler)} work correctly.
     *
     * @throws DataCapturingException Happens on unexpected states during data capturing.
     * @throws MissingPermissionException Should not happen since a <code>GrantPermissionRule</code> is used.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test
    public void testMultipleStartStopWithDelay() throws DataCapturingException, MissingPermissionException,
            NoSuchMeasurementException, CursorIsNullException, CorruptedMeasurementException {

        final long measurementIdentifier = startAndCheckThatLaunched();
        List<Measurement> measurements = persistenceLayer.loadMeasurements();
        assertThat(measurements.size(), is(equalTo(1)));

        stopAndCheckThatStopped(measurementIdentifier);

        final long measurementIdentifier2 = startAndCheckThatLaunched();
        measurements = persistenceLayer.loadMeasurements();
        assertThat(measurements.size(), is(equalTo(2)));

        stopAndCheckThatStopped(measurementIdentifier2);
    }

    /**
     * Tests that a double start-stop combination without waiting for the callback does not break the service.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test
    @Ignore("This test fails as our library currently runs lifecycle tasks (start/stop) in parallel.\n" +
            "To fix this we need to re-use a handler for a sequential execution. See CY-4098, MOV-378\n" +
            "We should consider refactoring the code before to use startCommandReceived as intended CY-4097.")
    public void testMultipleStartStopWithoutDelay() throws DataCapturingException, MissingPermissionException,
            NoSuchMeasurementException, CursorIsNullException, CorruptedMeasurementException {

        // Do not reuse the lock/condition!
        final Lock lock1 = new ReentrantLock();
        final Condition condition1 = lock1.newCondition();
        final TestStartUpFinishedHandler startUpFinishedHandler1 = new TestStartUpFinishedHandler(lock1, condition1,
                MessageCodes.getServiceStartedActionId(appId));
        // Do not reuse the lock/condition!
        final Lock lock2 = new ReentrantLock();
        final Condition condition2 = lock2.newCondition();
        final TestStartUpFinishedHandler startUpFinishedHandler2 = new TestStartUpFinishedHandler(lock2, condition2,
                MessageCodes.getServiceStartedActionId(appId));
        // Do not reuse the lock/condition!
        final Lock lock3 = new ReentrantLock();
        final Condition condition3 = lock3.newCondition();
        final TestStartUpFinishedHandler startUpFinishedHandler3 = new TestStartUpFinishedHandler(lock3, condition3,
                MessageCodes.getServiceStartedActionId(appId));
        // Do not reuse the lock/condition!
        final Lock lock4 = new ReentrantLock();
        final Condition condition4 = lock4.newCondition();
        final TestShutdownFinishedHandler shutDownFinishedHandler1 = new TestShutdownFinishedHandler(lock4, condition4,
                MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED);
        // Do not reuse the lock/condition!
        final Lock lock5 = new ReentrantLock();
        final Condition condition5 = lock5.newCondition();
        final TestShutdownFinishedHandler shutDownFinishedHandler2 = new TestShutdownFinishedHandler(lock5, condition5,
                MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED);
        // Do not reuse the lock/condition!
        final Lock lock6 = new ReentrantLock();
        final Condition condition6 = lock6.newCondition();
        final TestShutdownFinishedHandler shutDownFinishedHandler3 = new TestShutdownFinishedHandler(lock6, condition6,
                MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED);

        // First Start/stop without waiting
        oocut.start(UNKNOWN, startUpFinishedHandler1);
        oocut.stop(shutDownFinishedHandler1);
        // Second start/stop without waiting
        oocut.start(UNKNOWN, startUpFinishedHandler2);
        oocut.stop(shutDownFinishedHandler2);
        // Second start/stop without waiting
        oocut.start(UNKNOWN, startUpFinishedHandler3);
        oocut.stop(shutDownFinishedHandler3);

        // Now let's make sure all measurements started and stopped as expected
        TestUtils.lockAndWait(2, TimeUnit.SECONDS, startUpFinishedHandler1.getLock(),
                startUpFinishedHandler1.getCondition());
        TestUtils.lockAndWait(2, TimeUnit.SECONDS, shutDownFinishedHandler1.getLock(),
                shutDownFinishedHandler1.getCondition());
        TestUtils.lockAndWait(2, TimeUnit.SECONDS, startUpFinishedHandler2.getLock(),
                startUpFinishedHandler2.getCondition());
        TestUtils.lockAndWait(2, TimeUnit.SECONDS, shutDownFinishedHandler2.getLock(),
                shutDownFinishedHandler2.getCondition());
        TestUtils.lockAndWait(2, TimeUnit.SECONDS, startUpFinishedHandler3.getLock(),
                startUpFinishedHandler3.getCondition());
        TestUtils.lockAndWait(2, TimeUnit.SECONDS, shutDownFinishedHandler3.getLock(),
                shutDownFinishedHandler3.getCondition());

        List<Measurement> measurements = persistenceLayer.loadMeasurements();
        assertThat(measurements.size(), is(equalTo(3)));

        final long measurementId1 = startUpFinishedHandler1.receivedMeasurementIdentifier;
        assertThat(measurements.get(0).getIdentifier(), is(equalTo(measurementId1)));
        final long measurementId2 = startUpFinishedHandler2.receivedMeasurementIdentifier;
        assertThat(measurements.get(1).getIdentifier(), is(equalTo(measurementId2)));
        final long measurementId3 = startUpFinishedHandler3.receivedMeasurementIdentifier;
        assertThat(measurements.get(2).getIdentifier(), is(equalTo(measurementId3)));

        assertThat(measurementId1, is(not(equalTo(-1L))));
        assertThat(shutDownFinishedHandler1.receivedMeasurementIdentifier, is(equalTo(measurementId1)));
        assertThat(measurementId2, is(not(equalTo(-1L))));
        assertThat(shutDownFinishedHandler2.receivedMeasurementIdentifier, is(equalTo(measurementId2)));
        assertThat(measurementId3, is(not(equalTo(-1L))));
        assertThat(shutDownFinishedHandler3.receivedMeasurementIdentifier, is(equalTo(measurementId3)));
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
    public void testDisconnectReconnect() throws DataCapturingException, MissingPermissionException,
            NoSuchMeasurementException, CursorIsNullException, CorruptedMeasurementException {

        final long measurementIdentifier = startAndCheckThatLaunched();

        oocut.disconnect();
        assertThat(oocut.reconnect(DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT), is(true));

        stopAndCheckThatStopped(measurementIdentifier);
    }

    /**
     * Tests that running startSync twice does not break the system. This test succeeds if no <code>Exception</code>
     * occurs. Must be supported (#MOV-460).
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test
    public void testDoubleStart() throws DataCapturingException, MissingPermissionException, NoSuchMeasurementException,
            CursorIsNullException, CorruptedMeasurementException {

        final long measurementIdentifier = startAndCheckThatLaunched();

        // Second start - should not launch anything
        // Do not reuse the lock/condition!
        final Lock lock = new ReentrantLock();
        final Condition condition = lock.newCondition();
        final TestStartUpFinishedHandler startUpFinishedHandler = new TestStartUpFinishedHandler(lock, condition,
                MessageCodes.getServiceStartedActionId(appId));
        oocut.start(UNKNOWN, startUpFinishedHandler);
        TestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(startUpFinishedHandler.receivedServiceStarted(), is(equalTo(false)));

        stopAndCheckThatStopped(measurementIdentifier);
    }

    /**
     * Tests that stopping a stopped service throws the expected exception.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test(expected = NoSuchMeasurementException.class)
    public void testDoubleStop() throws DataCapturingException, MissingPermissionException, NoSuchMeasurementException,
            CursorIsNullException, CorruptedMeasurementException {

        final long measurementId = startAndCheckThatLaunched();

        stopAndCheckThatStopped(measurementId);
        // Do not reuse the lock/condition!
        final Lock lock = new ReentrantLock();
        final Condition condition = lock.newCondition();
        // must throw NoSuchMeasurementException
        oocut.stop(new TestShutdownFinishedHandler(lock, condition, MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED));
    }

    /**
     * Tests for the correct <code>Exception</code> if you try to disconnect from a disconnected service.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test(expected = DataCapturingException.class)
    public void testDoubleDisconnect() throws DataCapturingException, MissingPermissionException,
            NoSuchMeasurementException, CursorIsNullException, CorruptedMeasurementException {

        final long measurementIdentifier = startAndCheckThatLaunched();

        oocut.disconnect();
        oocut.disconnect(); // must throw DataCapturingException
        stopAndCheckThatStopped(measurementIdentifier); // is called by tearDown
    }

    /**
     * Tests that no <code>Exception</code> occurs if you stop a disconnected service.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test
    public void testStopNonConnectedService() throws DataCapturingException, MissingPermissionException,
            NoSuchMeasurementException, CursorIsNullException, CorruptedMeasurementException {

        final long measurementIdentifier = startAndCheckThatLaunched();

        oocut.disconnect();

        stopAndCheckThatStopped(measurementIdentifier);
    }

    /**
     * Tests that no <code>Exception</code> is thrown when we try to connect to the same service twice.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test
    public void testDoubleReconnect() throws DataCapturingException, MissingPermissionException,
            NoSuchMeasurementException, CursorIsNullException, CorruptedMeasurementException {

        final long measurementIdentifier = startAndCheckThatLaunched();
        oocut.disconnect();

        assertThat(oocut.reconnect(DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT), is(true));
        assertThat(oocut.reconnect(DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT), is(true));

        stopAndCheckThatStopped(measurementIdentifier);
    }

    /**
     * Tests that two correct cycles of disconnect and reconnect on a running service work fine.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test
    public void testDisconnectReconnectTwice() throws DataCapturingException, MissingPermissionException,
            NoSuchMeasurementException, CursorIsNullException, CorruptedMeasurementException {

        final long measurementIdentifier = startAndCheckThatLaunched();

        oocut.disconnect();
        assertThat(oocut.reconnect(DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT), is(true));
        oocut.disconnect();
        assertThat(oocut.reconnect(DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT), is(true));

        stopAndCheckThatStopped(measurementIdentifier);
    }

    /**
     * Tests that starting a service twice throws no <code>Exception</code>.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test
    public void testRestart() throws DataCapturingException, MissingPermissionException, NoSuchMeasurementException,
            CursorIsNullException, CorruptedMeasurementException {

        final long measurementIdentifier = startAndCheckThatLaunched();
        stopAndCheckThatStopped(measurementIdentifier);

        final long measurementIdentifier2 = startAndCheckThatLaunched();
        assertTrue(measurementIdentifier2 != measurementIdentifier);
        stopAndCheckThatStopped(measurementIdentifier2);
    }

    /**
     * Tests that calling resume two times in a row works without causing any errors. The second call to resume should
     * just do nothing. Must be supported (#MOV-460).
     *
     * @throws MissingPermissionException If permission to access geo location sensor is missing.
     * @throws DataCapturingException If any unexpected error occurs during the test.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test
    public void testResumeTwice() throws MissingPermissionException, DataCapturingException, NoSuchMeasurementException,
            CursorIsNullException, CorruptedMeasurementException {

        // Start, pause
        final long measurementIdentifier = startAndCheckThatLaunched();
        pauseAndCheckThatStopped(measurementIdentifier);

        // Resume 1
        resumeAndCheckThatLaunched(measurementIdentifier);

        // Resume 2: must be ignored by resumeAsync
        PersistenceLayer<CapturingPersistenceBehaviour> persistence = new PersistenceLayer<>(context,
                context.getContentResolver(), AUTHORITY, new CapturingPersistenceBehaviour());
        // Do not reuse the lock/condition!
        final Lock lock = new ReentrantLock();
        final Condition condition = lock.newCondition();
        final TestStartUpFinishedHandler startUpFinishedHandler = new TestStartUpFinishedHandler(lock, condition,
                MessageCodes.getServiceStartedActionId(appId));
        oocut.resume(startUpFinishedHandler);
        final boolean isRunning = isDataCapturingServiceRunning();
        assertThat(isRunning, is(equalTo(true)));
        assertThat(persistence.loadMeasurementStatus(measurementIdentifier), is(equalTo(OPEN)));

        stopAndCheckThatStopped(measurementIdentifier);
        assertThat(persistence.loadMeasurementStatus(measurementIdentifier), is(equalTo(FINISHED)));
    }

    /**
     * Tests that stopping a paused service does work successfully.
     *
     * @throws MissingPermissionException If the test is missing the permission to access the geo location sensor.
     * @throws DataCapturingException If any unexpected error occurs.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test
    public void testStartPauseStop() throws MissingPermissionException, DataCapturingException,
            NoSuchMeasurementException, CursorIsNullException, CorruptedMeasurementException {

        final long measurementIdentifier = startAndCheckThatLaunched();
        pauseAndCheckThatStopped(measurementIdentifier);
        stopAndCheckThatStopped(-1); // -1 because it's already stopped
    }

    /**
     * Tests that stopping a paused service does work successfully.
     * <p>
     * As this test was flaky MOV-527, we have this test here which executes it multiple times.
     *
     * @throws MissingPermissionException If the test is missing the permission to access the geo location sensor.
     * @throws DataCapturingException If any unexpected error occurs.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test
    @Ignore("Not needed to be executed automatically as MOV-527 made the normal tests flaky")
    public void testStartPauseStop_MultipleTimes() throws MissingPermissionException, DataCapturingException,
            NoSuchMeasurementException, CursorIsNullException, CorruptedMeasurementException {

        for (int i = 0; i < 20; i++) {
            Log.d(TAG, "ITERATION: " + i);

            final long measurementIdentifier = startAndCheckThatLaunched();
            pauseAndCheckThatStopped(measurementIdentifier);
            stopAndCheckThatStopped(-1); // -1 because it's already stopped
        }
    }

    /**
     * Tests that removing the {@link DataCapturingListener} during capturing does not stop the
     * {@link DataCapturingBackgroundService}.
     *
     * @throws MissingPermissionException If the test is missing the permission to access the geo location sensor.
     * @throws DataCapturingException If any unexpected error occurs.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test
    public void testRemoveDataCapturingListener() throws MissingPermissionException, DataCapturingException,
            NoSuchMeasurementException, CursorIsNullException, CorruptedMeasurementException {

        final long measurementIdentifier = startAndCheckThatLaunched();
        // This happens in SDK implementing apps (SR) when the app is paused and resumed
        oocut.removeDataCapturingListener(testListener);
        oocut.addDataCapturingListener(testListener);
        // Should not happen, we test it anyways
        oocut.addDataCapturingListener(testListener);
        pauseAndCheckThatStopped(measurementIdentifier);
        // Should not happen, we test it anyways
        oocut.removeDataCapturingListener(testListener);
        // Should not happen, we test it anyways
        oocut.removeDataCapturingListener(testListener);
        resumeAndCheckThatLaunched(measurementIdentifier);
        stopAndCheckThatStopped(measurementIdentifier);
    }

    /**
     * Tests if the service lifecycle is running successfully and that the life-cycle {@link Event}s are logged.
     * <p>
     * Makes sure the {@link DataCapturingService#pause(ShutDownFinishedHandler)} and
     * {@link DataCapturingService#resume(StartUpFinishedHandler)} work correctly.
     *
     * @throws DataCapturingException Happens on unexpected states during data capturing.
     * @throws MissingPermissionException Should not happen since a <code>GrantPermissionRule</code> is used.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test
    public void testStartPauseResumeStop_EventsAreLogged() throws DataCapturingException, MissingPermissionException,
            NoSuchMeasurementException, CursorIsNullException, CorruptedMeasurementException {

        final long measurementIdentifier = startPauseResumeStop();

        final List<Event> events = oocut.persistenceLayer.loadEvents(measurementIdentifier);
        // start, pause, resume, stop and initial MODALITY_TYPE_CHANGE event
        assertThat(events.size(), is(equalTo(5)));
        assertThat(events.get(0).getType(), is(equalTo(Event.EventType.LIFECYCLE_START)));
        assertThat(events.get(1).getType(), is(equalTo(Event.EventType.MODALITY_TYPE_CHANGE)));
        assertThat(events.get(2).getType(), is(equalTo(Event.EventType.LIFECYCLE_PAUSE)));
        assertThat(events.get(3).getType(), is(equalTo(Event.EventType.LIFECYCLE_RESUME)));
        assertThat(events.get(4).getType(), is(equalTo(Event.EventType.LIFECYCLE_STOP)));
    }

    /**
     * Tests if the service lifecycle is running successfully and that the life-cycle {@link Event}s are logged.
     * <p>
     * Makes sure the {@link DataCapturingService#pause(ShutDownFinishedHandler)} and
     * {@link DataCapturingService#resume(StartUpFinishedHandler)} work correctly.
     * <p>
     * As this test was flaky MOV-527, we have this test here which executes it multiple times.
     *
     * @throws DataCapturingException Happens on unexpected states during data capturing.
     * @throws MissingPermissionException Should not happen since a <code>GrantPermissionRule</code> is used.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Ignore("Not needed to be executed automatically as MOV-527 made the normal tests flaky")
    @Test
    public void testStartPauseResumeStop_MultipleTimes() throws DataCapturingException, MissingPermissionException,
            NoSuchMeasurementException, CursorIsNullException, CorruptedMeasurementException {

        for (int i = 0; i < 50; i++) {
            Log.d(TAG, "ITERATION: " + i);

            startPauseResumeStop();

            // For for-i-loops within this test
            SharedTestUtils.clearPersistenceLayer(context, context.getContentResolver(), AUTHORITY);
        }
    }

    private long startPauseResumeStop() throws DataCapturingException, NoSuchMeasurementException,
            CursorIsNullException, CorruptedMeasurementException, MissingPermissionException {

        final long measurementIdentifier = startAndCheckThatLaunched();
        final List<Measurement> measurements = persistenceLayer.loadMeasurements();
        assertThat(measurements.size(), is(equalTo(1)));

        pauseAndCheckThatStopped(measurementIdentifier);

        resumeAndCheckThatLaunched(measurementIdentifier);
        final List<Measurement> newMeasurements = persistenceLayer.loadMeasurements();
        assertThat(measurements.size() == newMeasurements.size(), is(equalTo(true)));

        stopAndCheckThatStopped(measurementIdentifier);

        // Check Events
        ContentResolver contentResolver = context.getContentResolver();
        try (final Cursor eventCursor = contentResolver.query(getEventUri(AUTHORITY), null,
                EventTable.COLUMN_MEASUREMENT_FK + "=?",
                new String[] {Long.valueOf(measurementIdentifier).toString()},
                EventTable.COLUMN_TIMESTAMP + " ASC")) {
            softCatchNullCursor(eventCursor);

            final List<Event> events = new ArrayList<>();
            while (eventCursor.moveToNext()) {
                final Event.EventType eventType = Event.EventType
                        .valueOf(eventCursor.getString(eventCursor.getColumnIndexOrThrow(EventTable.COLUMN_TYPE)));
                final long eventTime = eventCursor.getLong(eventCursor.getColumnIndexOrThrow(EventTable.COLUMN_TIMESTAMP));
                final String value = eventCursor.getString(eventCursor.getColumnIndexOrThrow(EventTable.COLUMN_VALUE));
                final long eventId = eventCursor.getLong(eventCursor.getColumnIndexOrThrow(BaseColumns._ID));
                events.add(new Event(eventId, eventType, eventTime, value));
            }

            assertThat(events.size(), is(equalTo(5)));
            assertThat(events.get(0).getType(), is(equalTo(Event.EventType.LIFECYCLE_START)));
            assertThat(events.get(1).getType(), is(equalTo(Event.EventType.MODALITY_TYPE_CHANGE)));
            assertThat(events.get(1).getValue(), is(equalTo(UNKNOWN.getDatabaseIdentifier())));
            assertThat(events.get(2).getType(), is(equalTo(Event.EventType.LIFECYCLE_PAUSE)));
            assertThat(events.get(3).getType(), is(equalTo(Event.EventType.LIFECYCLE_RESUME)));
            assertThat(events.get(4).getType(), is(equalTo(Event.EventType.LIFECYCLE_STOP)));

            return measurementIdentifier;
        }
    }

    /**
     * Tests whether actual sensor data is captured after running the method
     * {@link CyfaceDataCapturingService#start(Modality, StartUpFinishedHandler)} ()}.
     * In bug #CY-3862 only the {@link DataCapturingService} was started and measurements created
     * but no sensor data was captured as the {@link de.cyface.datacapturing.backend.DataCapturingBackgroundService}
     * was not started. The cause was: disables sensor capturing.
     *
     * This test is Flaky because it's success depends on if sensor data was captured during the
     * lockAndWait timeout. It's large because multiple seconds are waited until during the test.
     *
     * @throws DataCapturingException If any unexpected errors occur during data capturing.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test
    @LargeTest
    @FlakyTest
    public void testSensorDataCapturing() throws DataCapturingException, MissingPermissionException,
            NoSuchMeasurementException, CursorIsNullException, CorruptedMeasurementException, InterruptedException {

        final long measurementIdentifier = startAndCheckThatLaunched();

        // Check sensor data
        final List<Measurement> measurements = persistenceLayer.loadMeasurements();
        assertThat(measurements.size() > 0, is(equalTo(true)));
        Thread.sleep(3000L);
        assertThat(testListener.getCapturedData().size() > 0, is(equalTo(true)));

        stopAndCheckThatStopped(measurementIdentifier);
    }

    /**
     * Tests whether reconnect throws no exception when called without a running background service and leaves the
     * DataCapturingService in the correct state (<code>isDataCapturingServiceRunning</code> is <code>false</code>.
     */
    @Test
    public void testReconnectOnNonRunningServer() {
        assertThat(oocut.reconnect(DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT), is(false));
        assertThat(oocut.getIsRunning(), is(equalTo(false)));
    }

    /**
     * Test that checks that the {@link DataCapturingService} constructor only accepts API URls with "https://" as
     * protocol.
     * <p>
     * We had twice the problem that SDK implementors used no or a false protocol. This test ensures that
     * our code throws a hard exception if this happens again which should help to identify this prior to release.
     */
    @Test(expected = SetupException.class)
    public void testDataCapturingService_doesNotAcceptUrlWithoutProtocol()
            throws CursorIsNullException, SetupException {

        new CyfaceDataCapturingService(context, context.getContentResolver(), AUTHORITY,
                ACCOUNT_TYPE, "localhost:8080", new IgnoreEventsStrategy(), testListener, 100);
    }

    /**
     * Tests that starting a new {@code Measurement} and changing the {@code Modality} during runtime creates two
     * {@link Event.EventType#MODALITY_TYPE_CHANGE} entries.
     *
     * @throws MissingPermissionException If the test is missing the permission to access the geo location sensor.
     * @throws DataCapturingException If any unexpected error occurs.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test
    public void testChangeModality_EventLogContainsTwoModalities()
            throws MissingPermissionException, DataCapturingException,
            NoSuchMeasurementException, CursorIsNullException, CorruptedMeasurementException {

        final long measurementIdentifier = startAndCheckThatLaunched();
        oocut.changeModalityType(CAR);
        stopAndCheckThatStopped(measurementIdentifier);
        final List<Event> modalityTypeChanges = oocut.persistenceLayer.loadEvents(measurementIdentifier,
                Event.EventType.MODALITY_TYPE_CHANGE);
        assertThat(modalityTypeChanges.size(), is(equalTo(2)));
        assertThat(modalityTypeChanges.get(0).getValue(), is(equalTo(UNKNOWN.getDatabaseIdentifier())));
        assertThat(modalityTypeChanges.get(1).getValue(), is(equalTo(CAR.getDatabaseIdentifier())));
    }

    /**
     * Tests that changing to the same {@code Modality} twice does not produce a new
     * {@link Event.EventType#MODALITY_TYPE_CHANGE} {@code Event}.
     *
     * @throws MissingPermissionException If the test is missing the permission to access the geo location sensor.
     * @throws DataCapturingException If any unexpected error occurs.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test
    public void testChangeModalityToSameModalityTwice_EventLogStillContainsOnlyTwoModalities()
            throws MissingPermissionException, DataCapturingException,
            NoSuchMeasurementException, CursorIsNullException, CorruptedMeasurementException {

        final long measurementIdentifier = startAndCheckThatLaunched();
        oocut.changeModalityType(CAR);
        oocut.changeModalityType(CAR);
        stopAndCheckThatStopped(measurementIdentifier);
        final List<Event> modalityTypeChanges = oocut.persistenceLayer.loadEvents(measurementIdentifier,
                Event.EventType.MODALITY_TYPE_CHANGE);
        assertThat(modalityTypeChanges.size(), is(equalTo(2)));
    }

    /**
     * Tests that changing {@code Modality} during a {@link Event.EventType#LIFECYCLE_PAUSE} works as expected.
     *
     * @throws MissingPermissionException If the test is missing the permission to access the geo location sensor.
     * @throws DataCapturingException If any unexpected error occurs.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test
    public void testChangeModalityWhilePaused_EventLogStillContainsModalityChange()
            throws MissingPermissionException, DataCapturingException,
            NoSuchMeasurementException, CursorIsNullException, CorruptedMeasurementException {

        final long measurementIdentifier = startAndCheckThatLaunched();
        pauseAndCheckThatStopped(measurementIdentifier);
        oocut.changeModalityType(CAR);
        stopAndCheckThatStopped(-1L); // -1 because it's already stopped
        final List<Event> modalityTypeChanges = oocut.persistenceLayer.loadEvents(measurementIdentifier,
                Event.EventType.MODALITY_TYPE_CHANGE);
        assertThat(modalityTypeChanges.size(), is(equalTo(2)));
        assertThat(modalityTypeChanges.get(0).getValue(), is(equalTo(UNKNOWN.getDatabaseIdentifier())));
        assertThat(modalityTypeChanges.get(1).getValue(), is(equalTo(CAR.getDatabaseIdentifier())));
    }
}
