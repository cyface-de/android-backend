package de.cyface.datacapturing;

import static de.cyface.datacapturing.TestUtils.ACCOUNT_TYPE;
import static de.cyface.datacapturing.TestUtils.AUTHORITY;
import static de.cyface.persistence.model.MeasurementStatus.FINISHED;
import static de.cyface.persistence.model.MeasurementStatus.OPEN;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

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
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.rule.ServiceTestRule;
import androidx.test.rule.provider.ProviderTestRule;
import de.cyface.datacapturing.backend.TestCallback;
import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.exception.MissingPermissionException;
import de.cyface.datacapturing.exception.SetupException;
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour;
import de.cyface.datacapturing.ui.UIListener;
import de.cyface.persistence.MeasuringPointsContentProvider;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.Vehicle;
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
 * @version 5.2.4
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
     * Callback triggered if the test successfully establishes a connection with the background service or times out.
     */
    private TestCallback runningStatusCallback;
    /**
     * Lock used to synchronize with the background service.
     */
    private Lock lock;
    /**
     * Condition waiting for the background service to wake up this test case.
     */
    private Condition condition;
    /**
     * The {@link Context} needed to access the persistence layer
     */
    private Context context;

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
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                try {
                    oocut = new CyfaceDataCapturingService(context, context.getContentResolver(), AUTHORITY,
                            ACCOUNT_TYPE, "http://localhost:8080", new IgnoreEventsStrategy());
                } catch (SetupException | CursorIsNullException e) {
                    throw new IllegalStateException(e);
                }
            }
        });

        // Prepare
        lock = new ReentrantLock();
        condition = lock.newCondition();
        testListener = new TestListener(lock, condition);
        // A listener catching messages send to the UI in real applications.
        runningStatusCallback = new TestCallback("Default Callback", lock, condition);

        // Making sure there is no service instance of a previous test running
        Validate.isTrue(!isDataCapturingServiceRunning());
    }

    /**
     * Tries to stop the DataCapturingService if a test failed to do so.
     *
     * @throws NoSuchMeasurementException If no measurement was {@link MeasurementStatus#OPEN} or
     *             {@link MeasurementStatus#PAUSED} while stopping the service. This usually occurs if
     *             there was no call to
     *             {@link DataCapturingService#start(DataCapturingListener, Vehicle, StartUpFinishedHandler)}
     *             prior to stopping.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @After
    public void tearDown() throws CursorIsNullException, NoSuchMeasurementException {
        if (isDataCapturingServiceRunning()) {

            // Stop zombie
            final TestShutdownFinishedHandler shutDownFinishedHandler = new TestShutdownFinishedHandler(lock,
                    condition);
            oocut.stop(shutDownFinishedHandler);

            // Ensure the zombie sent a stopped message back to the DataCapturingService
            TestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
            assertThat(shutDownFinishedHandler.receivedServiceStopped(), is(equalTo(true)));

            // Get the current isRunning state (i.e. updates runningStatusCallback). This is important, see #MOV-484.
            TestUtils.callCheckForRunning(oocut, runningStatusCallback);
            TestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);

            // Ensure that the zombie was not running during the callCheckForRunning
            assertThat(runningStatusCallback.wasRunning(), is(equalTo(false)));
            assertThat(runningStatusCallback.didTimeOut(), is(equalTo(true)));
        }

        SharedTestUtils.clearPersistenceLayer(context, context.getContentResolver(), AUTHORITY);
    }

    /**
     * Makes sure a test did not forget to stop the capturing.
     */
    private boolean isDataCapturingServiceRunning() {

        // Get the current isRunning state (i.e. updates runningStatusCallback). This is important, see #MOV-484.
        TestUtils.callCheckForRunning(oocut, runningStatusCallback);
        TestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);

        return runningStatusCallback.wasRunning() && !runningStatusCallback.timedOut;
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
    private long startAndCheckThatLaunched()
            throws MissingPermissionException, DataCapturingException, CursorIsNullException {

        final TestStartUpFinishedHandler startUpFinishedHandler = new TestStartUpFinishedHandler(lock, condition,
                oocut.getDeviceIdentifier());
        oocut.start(testListener, Vehicle.UNKNOWN, startUpFinishedHandler);

        return checkThatLaunched(startUpFinishedHandler);
    }

    /**
     * Pauses a {@link DataCapturingService} and checks that it's not running afterwards.
     *
     * @param measurementIdentifier The if of the measurement expected to be closed.
     * @throws DataCapturingException In case the service was not stopped successfully.
     * @throws NoSuchMeasurementException If no measurement was {@link MeasurementStatus#OPEN} while pausing the
     *             service. This usually occurs if there was no call to
     *             {@link DataCapturingService#start(DataCapturingListener, Vehicle, StartUpFinishedHandler)} prior to
     *             pausing.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    private void pauseAndCheckThatStopped(long measurementIdentifier)
            throws NoSuchMeasurementException, DataCapturingException, CursorIsNullException {

        final TestShutdownFinishedHandler shutDownFinishedHandler = new TestShutdownFinishedHandler(lock, condition);
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
     *             {@link DataCapturingService#start(DataCapturingListener, Vehicle, StartUpFinishedHandler)} prior to
     *             pausing.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    private void resumeAndCheckThatLaunched(long measurementIdentifier) throws MissingPermissionException,
            DataCapturingException, CursorIsNullException, NoSuchMeasurementException {

        final TestStartUpFinishedHandler startUpFinishedHandler = new TestStartUpFinishedHandler(lock, condition,
                oocut.getDeviceIdentifier());
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
     *             {@link DataCapturingService#start(DataCapturingListener, Vehicle, StartUpFinishedHandler)}
     *             prior to stopping.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    private void stopAndCheckThatStopped(final long measurementIdentifier)
            throws NoSuchMeasurementException, CursorIsNullException {

        final TestShutdownFinishedHandler shutDownFinishedHandler = new TestShutdownFinishedHandler(lock, condition);
        oocut.stop(shutDownFinishedHandler);

        checkThatStopped(shutDownFinishedHandler, measurementIdentifier);
    }

    /**
     * Checks that a {@link DataCapturingService} actually started after calling the life-cycle method
     * {@link DataCapturingService#start(DataCapturingListener, Vehicle, StartUpFinishedHandler)} or
     * {@link DataCapturingService#resume(StartUpFinishedHandler)}.
     *
     * This also updates the {@link #runningStatusCallback}.
     *
     * @param startUpFinishedHandler The {@link TestStartUpFinishedHandler} which was used to start the service
     * @return The id of the measurement which was started
     */
    private long checkThatLaunched(final TestStartUpFinishedHandler startUpFinishedHandler) {

        // Ensure the DataCapturingBackgroundService sent a started message back to the DataCapturingService
        TestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(startUpFinishedHandler.receivedServiceStarted(), is(equalTo(true)));

        // Get the current isRunning state (i.e. updates runningStatusCallback). This is important, see #MOV-484.
        TestUtils.callCheckForRunning(oocut, runningStatusCallback);
        TestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);

        // Ensure that the DataCapturingBackgroundService was running during the callCheckForRunning
        assertThat(runningStatusCallback.wasRunning(), is(equalTo(true)));
        assertThat(runningStatusCallback.didTimeOut(), is(equalTo(false)));

        // Return the id of the started measurement
        assertThat(startUpFinishedHandler.receivedMeasurementIdentifier, is(not(equalTo(-1L))));
        return startUpFinishedHandler.receivedMeasurementIdentifier;
    }

    /**
     * Checks that a {@link DataCapturingService} actually stopped after calling the life-cycle method
     * {@link DataCapturingService#stop(ShutDownFinishedHandler)} or
     * {@link DataCapturingService#pause(ShutDownFinishedHandler)}.
     *
     * This also updates the {@link #runningStatusCallback}.
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
        TestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(shutDownFinishedHandler.receivedServiceStopped(), is(equalTo(true)));

        // Get the current isRunning state (i.e. updates runningStatusCallback). This is important, see #MOV-484.
        TestUtils.callCheckForRunning(oocut, runningStatusCallback);
        TestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);

        // Ensure that the DataCapturingBackgroundService was not running during the callCheckForRunning
        assertThat(runningStatusCallback.wasRunning(), is(equalTo(false)));
        assertThat(runningStatusCallback.didTimeOut(), is(equalTo(true)));

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
            CursorIsNullException {

        final long receivedMeasurementIdentifier = startAndCheckThatLaunched();
        stopAndCheckThatStopped(receivedMeasurementIdentifier);
    }

    /**
     * Tests that a double start-stop combination without waiting for the callback does not break the service.
     *
     * IGNORED: This test fails as our library currently runs lifecycle tasks (start/stop) in parallel.
     * To fix this we need to re-use a handler for a sequential execution. See CY-4098, MOV-378
     * We should consider refactoring the code before to use startCommandReceived as intended CY-4097.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test
    @Ignore
    public void testMultipleStartStopWithoutDelay() throws DataCapturingException, MissingPermissionException,
            NoSuchMeasurementException, CursorIsNullException {
        final TestStartUpFinishedHandler startUpFinishedHandler1 = new TestStartUpFinishedHandler(lock, condition,
                oocut.getDeviceIdentifier());
        final TestStartUpFinishedHandler startUpFinishedHandler2 = new TestStartUpFinishedHandler(lock, condition,
                oocut.getDeviceIdentifier());
        final TestStartUpFinishedHandler startUpFinishedHandler3 = new TestStartUpFinishedHandler(lock, condition,
                oocut.getDeviceIdentifier());
        final TestShutdownFinishedHandler shutDownFinishedHandler1 = new TestShutdownFinishedHandler(lock, condition);
        final TestShutdownFinishedHandler shutDownFinishedHandler2 = new TestShutdownFinishedHandler(lock, condition);
        final TestShutdownFinishedHandler shutDownFinishedHandler3 = new TestShutdownFinishedHandler(lock, condition);

        // First Start/stop without waiting
        oocut.start(testListener, Vehicle.UNKNOWN, startUpFinishedHandler1);
        oocut.stop(shutDownFinishedHandler1);
        // Second start/stop without waiting
        oocut.start(testListener, Vehicle.UNKNOWN, startUpFinishedHandler2);
        oocut.stop(shutDownFinishedHandler2);
        // Second start/stop without waiting
        oocut.start(testListener, Vehicle.UNKNOWN, startUpFinishedHandler3);
        oocut.stop(shutDownFinishedHandler3);

        // Now let's make sure all measurements started and stopped as expected
        TestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);

        List<Measurement> measurements = oocut.getCachedMeasurements();
        assertThat(measurements.size(), is(equalTo(3)));

        final long measurementId1 = startUpFinishedHandler1.receivedMeasurementIdentifier;
        assertThat(measurements.get(0).getIdentifier(), is(equalTo(measurementId1)));
        final long measurementId2 = startUpFinishedHandler2.receivedMeasurementIdentifier;
        assertThat(measurements.get(1).getIdentifier(), is(equalTo(measurementId2)));
        final long measurementId3 = startUpFinishedHandler3.receivedMeasurementIdentifier;
        assertThat(measurements.get(2).getIdentifier(), is(equalTo(measurementId3)));

        /*
         * assertThat(measurementId1, is(not(equalTo(-1L))));
         * assertThat(shutDownFinishedHandler1.receivedMeasurementIdentifier, is(equalTo(measurementId1)));
         * assertThat(measurementId2, is(not(equalTo(-1L))));
         * assertThat(shutDownFinishedHandler2.receivedMeasurementIdentifier, is(equalTo(measurementId2)));
         * assertThat(measurementId3, is(not(equalTo(-1L))));
         * assertThat(shutDownFinishedHandler3.receivedMeasurementIdentifier, is(equalTo(measurementId3)));
         */
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
            NoSuchMeasurementException, CursorIsNullException {

        final long measurementIdentifier = startAndCheckThatLaunched();

        oocut.disconnect();
        oocut.reconnect();
        TestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        TestUtils.callCheckForRunning(oocut, runningStatusCallback);
        TestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);

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
            CursorIsNullException {

        final long measurementIdentifier = startAndCheckThatLaunched();

        // Second start - should not launch anything
        final TestStartUpFinishedHandler startUpFinishedHandler = new TestStartUpFinishedHandler(lock, condition,
                oocut.getDeviceIdentifier());
        oocut.start(testListener, Vehicle.UNKNOWN, startUpFinishedHandler);
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
            CursorIsNullException {

        final long measurementId = startAndCheckThatLaunched();

        stopAndCheckThatStopped(measurementId);
        // must throw NoSuchMeasurementException
        oocut.stop(new TestShutdownFinishedHandler(lock, condition));
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
            NoSuchMeasurementException, CursorIsNullException {

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
            NoSuchMeasurementException, CursorIsNullException {

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
            NoSuchMeasurementException, CursorIsNullException {

        final long measurementIdentifier = startAndCheckThatLaunched();
        oocut.disconnect();

        oocut.reconnect();
        oocut.reconnect();
        TestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        TestUtils.callCheckForRunning(oocut, runningStatusCallback);
        TestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);

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
            NoSuchMeasurementException, CursorIsNullException {

        final long measurementIdentifier = startAndCheckThatLaunched();

        oocut.disconnect();
        oocut.reconnect();
        oocut.disconnect();
        oocut.reconnect();

        TestUtils.callCheckForRunning(oocut, runningStatusCallback);
        TestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat("Service seems not to be running anymore after two disconnect/reconnect cycles!",
                runningStatusCallback.wasRunning(), is(equalTo(true)));

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
            CursorIsNullException {

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
            CursorIsNullException {

        // Start, pause
        final long measurementIdentifier = startAndCheckThatLaunched();
        pauseAndCheckThatStopped(measurementIdentifier);

        // Resume 1
        resumeAndCheckThatLaunched(measurementIdentifier);

        // Resume 2: must be ignored by resumeAsync
        PersistenceLayer persistence = new PersistenceLayer(context, context.getContentResolver(), AUTHORITY,
                new CapturingPersistenceBehaviour());
        final TestStartUpFinishedHandler startUpFinishedHandler = new TestStartUpFinishedHandler(lock, condition,
                oocut.getDeviceIdentifier());
        oocut.resume(startUpFinishedHandler);
        TestUtils.callCheckForRunning(oocut, runningStatusCallback);
        TestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(runningStatusCallback.wasRunning(), is(equalTo(true)));
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
            NoSuchMeasurementException, CursorIsNullException {

        final long measurementIdentifier = startAndCheckThatLaunched();
        pauseAndCheckThatStopped(measurementIdentifier);
        stopAndCheckThatStopped(-1); // -1 because it's already stopped
    }

    /**
     * Tests if the service lifecycle is running successfully.
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
    public void testStartPauseResumeStop() throws DataCapturingException, MissingPermissionException,
            NoSuchMeasurementException, CursorIsNullException {

        final long measurementIdentifier = startAndCheckThatLaunched();
        final List<Measurement> measurements = oocut.getCachedMeasurements();
        assertThat(measurements.size(), is(equalTo(1)));

        pauseAndCheckThatStopped(measurementIdentifier);

        resumeAndCheckThatLaunched(measurementIdentifier);
        final List<Measurement> newMeasurements = oocut.getCachedMeasurements();
        assertThat(measurements.size() == newMeasurements.size(), is(equalTo(true)));

        stopAndCheckThatStopped(measurementIdentifier);
    }

    /**
     * Tests whether actual sensor data is captured after running the method
     * {@link CyfaceDataCapturingService#start(DataCapturingListener, Vehicle, StartUpFinishedHandler)} ()}.
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
            NoSuchMeasurementException, CursorIsNullException {

        final long measurementIdentifier = startAndCheckThatLaunched();

        // Check sensor data
        final List<Measurement> measurements = oocut.getCachedMeasurements();
        assertThat(measurements.size() > 0, is(equalTo(true)));
        TestUtils.lockAndWait(3, TimeUnit.SECONDS, lock, condition);
        assertThat(testListener.getCapturedData().size() > 0, is(equalTo(true)));

        stopAndCheckThatStopped(measurementIdentifier);
    }

    /**
     * Tests whether reconnect throws no exception when called without a running background service and leaves the
     * DataCapturingService in the correct state (<code>isDataCapturingServiceRunning</code> is <code>false</code>.
     *
     * @throws DataCapturingException Fails the test if anything goes wrong.
     */
    @Test
    public void testReconnectOnNonRunningServer() throws DataCapturingException {
        oocut.reconnect();
        assertThat(oocut.getIsRunning(), is(equalTo(false)));
    }
}
