package de.cyface.datacapturing;

import static de.cyface.datacapturing.ServiceTestUtils.ACCOUNT_TYPE;
import static de.cyface.datacapturing.ServiceTestUtils.AUTHORITY;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.FlakyTest;
import android.support.test.filters.LargeTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.GrantPermissionRule;
import android.support.test.rule.ServiceTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.ProviderTestCase2;

import de.cyface.datacapturing.backend.TestCallback;
import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.exception.MissingPermissionException;
import de.cyface.datacapturing.exception.NoSuchMeasurementException;
import de.cyface.datacapturing.exception.SetupException;
import de.cyface.datacapturing.model.Vehicle;
import de.cyface.persistence.MeasuringPointsContentProvider;
import de.cyface.synchronization.CyfaceAuthenticator;

/**
 * Tests whether the {@link DataCapturingService} works correctly. This is a flaky test since it starts a service that
 * relies on external sensors and the availability of a GPS signal. Each tests waits a few seconds to actually capture
 * some data, but it might still fail if you are indoors (which you will usually be while running tests, right?)
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 4.1.0
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class DataCapturingServiceTest extends ProviderTestCase2<MeasuringPointsContentProvider> {

    /**
     * The tag used to identify log messages.
     */
    private static final String TAG = "de.cyface.test";

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
     * Required constructor.
     */
    public DataCapturingServiceTest() {
        super(MeasuringPointsContentProvider.class, AUTHORITY);
    }

    /**
     * Initializes the super class as well as the object of the class under test and the synchronization lock. This is
     * called prior to every single test case.
     *
     * @throws Exception Aborts the test run if anything goes wrong.
     */
    @Before
    public void setUp() throws Exception {
        final Context context = InstrumentationRegistry.getTargetContext();
        // WARNING: Never change the order of the following two lines, even though the Google documentation tells you
        // something different!
        setContext(context);
        super.setUp();

        // The LOGIN_ACTIVITY is normally set to the LoginActivity of the SDK implementing app
        CyfaceAuthenticator.LOGIN_ACTIVITY = AccountAuthenticatorActivity.class;
        final Account requestAccount = new Account(ServiceTestUtils.DEFAULT_FREE_USERNAME,
                ServiceTestUtils.ACCOUNT_TYPE);
        AccountManager.get(context).addAccountExplicitly(requestAccount, ServiceTestUtils.DEFAULT_FREE_PASSWORD, null);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                try {
                    oocut = new CyfaceDataCapturingService(context, context.getContentResolver(), AUTHORITY,
                            ACCOUNT_TYPE, "http://localhost:8080");
                } catch (SetupException e) {
                    throw new IllegalStateException(e);
                }
            }
        });

        lock = new ReentrantLock();
        condition = lock.newCondition();
        testListener = new TestListener(lock, condition);
        runningStatusCallback = new TestCallback("Default Callback", lock, condition);
    }

    /**
     * Tests a common service run. Checks that some positions have been captured.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test
    public void testRunDataCapturingServiceSuccessfully()
            throws DataCapturingException, MissingPermissionException, NoSuchMeasurementException {
        TestStartUpFinishedHandler startUpFinishedHandler = new TestStartUpFinishedHandler(lock, condition);
        TestShutdownFinishedHandler shutdownFinishedHandler = new TestShutdownFinishedHandler(lock, condition);

        oocut.startAsync(testListener, Vehicle.UNKNOWN, startUpFinishedHandler);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(startUpFinishedHandler.receivedMeasurementIdentifier, is(not(equalTo(-1L))));

        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);

        oocut.stopAsync(shutdownFinishedHandler);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(runningStatusCallback.wasRunning(), is(equalTo(true)));
        assertThat(shutdownFinishedHandler.receivedMeasurementIdentifier, is(not(equalTo(-1L))));
        assertThat(shutdownFinishedHandler.receivedMeasurementIdentifier,
                is(equalTo(startUpFinishedHandler.receivedMeasurementIdentifier)));
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
    public void testDisconnectConnect()
            throws DataCapturingException, MissingPermissionException, NoSuchMeasurementException {
        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(runningStatusCallback.wasRunning(), is(equalTo(false)));

        oocut.startSync(testListener, Vehicle.UNKNOWN);

        oocut.disconnect();
        oocut.reconnect();

        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);

        oocut.stopSync();
        assertThat(runningStatusCallback.wasRunning(), is(equalTo(true)));
    }

    /**
     * Tests that running startSync twice does not break the system. This test succeeds if no <code>Exception</code>
     * occurs.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test
    public void testDoubleStart()
            throws DataCapturingException, MissingPermissionException, NoSuchMeasurementException {
        oocut.startSync(testListener, Vehicle.UNKNOWN);
        oocut.startSync(testListener, Vehicle.UNKNOWN);

        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        oocut.stopSync();
    }

    /**
     * Tests that stopping a stopped service causes no problems.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test
    public void testDoubleStop() throws DataCapturingException, MissingPermissionException, NoSuchMeasurementException {
        oocut.startAsync(testListener, Vehicle.UNKNOWN, new StartUpFinishedHandler() {
            @Override
            public void startUpFinished(final long measurementIdentifier) {
                lock.lock();
                try {
                    condition.signal();
                } finally {
                    lock.unlock();
                }
            }
        });
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);

        assertThat(runningStatusCallback.wasRunning(), is(equalTo(true)));
        assertThat(runningStatusCallback.didTimeOut(), is(equalTo(false)));

        oocut.stopAsync(new ShutDownFinishedHandler() {
            @Override
            public void shutDownFinished(final long measurementIdentifier) {
                lock.lock();
                try {
                    condition.signal();
                } finally {
                    lock.unlock();
                }
            }
        });
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);

        assertThat(runningStatusCallback.wasRunning(), is(equalTo(false)));
        assertThat(runningStatusCallback.didTimeOut(), is(equalTo(true)));

        oocut.stopAsync(new ShutDownFinishedHandler() {
            @Override
            public void shutDownFinished(final long measurementIdentifier) {
                lock.lock();
                try {
                    condition.signal();
                } finally {
                    lock.unlock();
                }
            }
        });
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);

        assertThat(runningStatusCallback.wasRunning(), is(equalTo(false)));
        assertThat(runningStatusCallback.didTimeOut(), is(equalTo(true)));
    }

    /**
     * Tests for the correct <code>Exception</code> if you try to disconnect from a diconnected service.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test(expected = DataCapturingException.class)
    public void testDoubleDisconnect()
            throws DataCapturingException, MissingPermissionException, NoSuchMeasurementException {
        oocut.startSync(testListener, Vehicle.UNKNOWN);

        oocut.disconnect();
        oocut.disconnect();
        oocut.stopSync();
    }

    /**
     * Tests that no <code>Exception</code> occurs if you stop a disconnected service.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test
    public void testStopNonConnectedService()
            throws DataCapturingException, MissingPermissionException, NoSuchMeasurementException {
        oocut.startAsync(testListener, Vehicle.UNKNOWN, new StartUpFinishedHandler() {
            @Override
            public void startUpFinished(final long measurementIdentifier) {
                lock.lock();
                try {
                    condition.signal();
                } finally {
                    lock.unlock();
                }
            }
        });
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);

        assertThat(runningStatusCallback.wasRunning(), is(equalTo(true)));
        assertThat(runningStatusCallback.didTimeOut(), is(equalTo(false)));

        oocut.disconnect();

        oocut.stopAsync(new ShutDownFinishedHandler() {
            @Override
            public void shutDownFinished(final long measurementIdentifier) {
                lock.lock();
                try {
                    condition.signal();
                } finally {
                    lock.unlock();
                }
            }
        });
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);

        assertThat(runningStatusCallback.wasRunning(), is(equalTo(false)));
        assertThat(runningStatusCallback.didTimeOut(), is(equalTo(true)));
    }

    /**
     * Tests that no <code>Exception</code> is thrown when we try to connect to the same service twice.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test
    public void testDoubleConnect()
            throws DataCapturingException, MissingPermissionException, NoSuchMeasurementException {
        oocut.startSync(testListener, Vehicle.UNKNOWN);

        oocut.disconnect();

        oocut.reconnect();
        oocut.reconnect();

        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        oocut.stopSync();
    }

    /**
     * Tests that two correct cycles of disconnect and reconnect on a running service work fine.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test
    public void testDisconnectConnectTwice()
            throws DataCapturingException, MissingPermissionException, NoSuchMeasurementException {
        // Service should not run in the beginning!
        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat("Service seems to be running before even started!", runningStatusCallback.wasRunning(),
                is(equalTo(false)));

        // Start service and wait for it to run.
        oocut.startAsync(testListener, Vehicle.UNKNOWN, new TestStartUpFinishedHandler(lock, condition));
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat("Service seems to not have started successfully!", runningStatusCallback.wasRunning(),
                is(equalTo(true)));

        oocut.disconnect();
        oocut.reconnect();
        oocut.disconnect();
        oocut.reconnect();

        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat("Service seems not to be running anymore after two disconnect/reconnect cycles!",
                runningStatusCallback.wasRunning(), is(equalTo(true)));

        oocut.stopAsync(new TestShutdownFinishedHandler(lock, condition));
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);

        // Check whether shutdown is still successful.
        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat("Service seems to still be running after having been stopped after two disconnect/reconnect cycles!",
                runningStatusCallback.wasRunning(), is(equalTo(false)));
    }

    /**
     * Tests that starting a service twice throws no <code>Exception</code>.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test
    public void testRestart() throws DataCapturingException, MissingPermissionException, NoSuchMeasurementException {
        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(runningStatusCallback.wasRunning(), is(equalTo(false)));

        oocut.startSync(testListener, Vehicle.UNKNOWN);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(runningStatusCallback.wasRunning(), is(equalTo(true)));
        oocut.stopSync();

        oocut.startSync(testListener, Vehicle.UNKNOWN);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(runningStatusCallback.wasRunning(), is(equalTo(true)));
        oocut.stopSync();
    }

    /**
     * Tests if the service lifecycle is running with asnyc methods successfully.
     *
     * @throws DataCapturingException Happens on unexpected states during data capturing.
     * @throws MissingPermissionException Should not happen since a <code>GrantPermissionRule</code> is used.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test
    public void testRunServiceAsync()
            throws DataCapturingException, MissingPermissionException, NoSuchMeasurementException {
        StartUpFinishedHandler startUpFinishedHandler = new TestStartUpFinishedHandler(lock, condition);
        oocut.startAsync(testListener, Vehicle.UNKNOWN, startUpFinishedHandler);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(startUpFinishedHandler.receivedServiceStarted(), is(equalTo(true)));

        ShutDownFinishedHandler shutDownFinishedHandler = new TestShutdownFinishedHandler(lock, condition);
        oocut.pauseAsync(shutDownFinishedHandler);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(shutDownFinishedHandler.receivedServiceStopped(), is(equalTo(true)));

        startUpFinishedHandler = new TestStartUpFinishedHandler(lock, condition);
        oocut.resumeAsync(startUpFinishedHandler);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(startUpFinishedHandler.receivedServiceStarted(), is(equalTo(true)));

        shutDownFinishedHandler = new TestShutdownFinishedHandler(lock, condition);
        oocut.stopAsync(shutDownFinishedHandler);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(shutDownFinishedHandler.receivedServiceStopped(), is(equalTo(true)));
    }

    /**
     * Tests that calling resume two times in a row works without causing any errors. The second call to resume should
     * just do nothing.
     *
     * @throws MissingPermissionException If permission to access geo location sensor is missing.
     * @throws DataCapturingException If any unexpected error occurs during the test.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test
    public void testResumeAsyncTwice()
            throws MissingPermissionException, DataCapturingException, NoSuchMeasurementException {
        StartUpFinishedHandler startUpFinishedHandler = new TestStartUpFinishedHandler(lock, condition);
        oocut.startAsync(testListener, Vehicle.UNKNOWN, startUpFinishedHandler);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(startUpFinishedHandler.receivedServiceStarted(), is(equalTo(true)));

        ShutDownFinishedHandler shutDownFinishedHandler = new TestShutdownFinishedHandler(lock, condition);
        oocut.pauseAsync(shutDownFinishedHandler);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(shutDownFinishedHandler.receivedServiceStopped(), is(equalTo(true)));

        startUpFinishedHandler = new TestStartUpFinishedHandler(lock, condition);
        oocut.resumeAsync(startUpFinishedHandler);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(startUpFinishedHandler.receivedServiceStarted(), is(equalTo(true)));

        startUpFinishedHandler = new TestStartUpFinishedHandler(lock, condition);
        oocut.resumeAsync(startUpFinishedHandler);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(startUpFinishedHandler.receivedServiceStarted(), is(equalTo(true)));

        shutDownFinishedHandler = new TestShutdownFinishedHandler(lock, condition);
        oocut.stopAsync(shutDownFinishedHandler);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(shutDownFinishedHandler.receivedServiceStopped(), is(equalTo(true)));

        // Tests that nothing is running anymore.
        final TestCallback isRunningCallback = new TestCallback("testResumeAsyncTwice", lock, condition);
        ServiceTestUtils.callCheckForRunning(oocut, isRunningCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(isRunningCallback.isRunning, is(equalTo(false)));
        assertThat(isRunningCallback.timedOut, is(equalTo(true)));
    }

    /**
     * Tests that stopping a paused service does work successfully.
     *
     * @throws MissingPermissionException If the test is missing the permission to access the geo location sensor.
     * @throws DataCapturingException If any unexpected error occurs.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test
    public void testStartPauseStop()
            throws MissingPermissionException, DataCapturingException, NoSuchMeasurementException {
        StartUpFinishedHandler startUpFinishedHandler = new TestStartUpFinishedHandler(lock, condition);
        oocut.startAsync(testListener, Vehicle.UNKNOWN, startUpFinishedHandler);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(startUpFinishedHandler.receivedServiceStarted(), is(equalTo(true)));

        ShutDownFinishedHandler shutDownFinishedHandler = new TestShutdownFinishedHandler(lock, condition);
        oocut.pauseAsync(shutDownFinishedHandler);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(shutDownFinishedHandler.receivedServiceStopped(), is(equalTo(true)));

        shutDownFinishedHandler = new TestShutdownFinishedHandler(lock, condition);
        oocut.stopAsync(shutDownFinishedHandler);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(shutDownFinishedHandler.receivedServiceStopped(), is(equalTo(true)));
    }

    /**
     * Tests whether the {@link MovebisDataCapturingService#pauseSync()} and
     * {@link MovebisDataCapturingService#resumeSync()}
     * work correctly.
     *
     * @throws DataCapturingException If any unexpected errors occur during data capturing.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test
    public void testPauseResumeMeasurement()
            throws DataCapturingException, MissingPermissionException, NoSuchMeasurementException {
        // start
        oocut.startSync(testListener, Vehicle.UNKNOWN);
        // check is running
        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2L, TimeUnit.SECONDS, lock, condition);
        assertThat(runningStatusCallback.isRunning, is(equalTo(true)));
        assertThat(runningStatusCallback.timedOut, is(equalTo(false)));
        // get measurements
        final List<Measurement> measurements = oocut.getCachedMeasurements();
        assertThat(measurements.size() > 0, is(equalTo(true)));
        // pause
        oocut.pauseSync();
        // check is not running
        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2L, TimeUnit.SECONDS, lock, condition);
        assertThat(runningStatusCallback.wasRunning(), is(equalTo(false)));
        assertThat(runningStatusCallback.didTimeOut(), is(equalTo(true)));
        // resume
        oocut.resumeSync();
        // check is running
        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2L, TimeUnit.SECONDS, lock, condition);
        assertThat(runningStatusCallback.wasRunning(), is(equalTo(true)));
        assertThat(runningStatusCallback.didTimeOut(), is(equalTo(false)));
        // get measurements again
        final List<Measurement> newMeasurements = oocut.getCachedMeasurements();
        // check for no new measurements
        assertThat(measurements.size() == newMeasurements.size(), is(equalTo(true)));
        // stop
        oocut.stopSync();
    }

    /**
     * Tests whether actual sensor data is captured after running the method
     * {@link CyfaceDataCapturingService#startAsync(DataCapturingListener, Vehicle, StartUpFinishedHandler)} ()}.
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
     */
    @Test
    @LargeTest
    @FlakyTest
    public void testSensorDataCapturing()
            throws DataCapturingException, MissingPermissionException, NoSuchMeasurementException {
        TestStartUpFinishedHandler startUpFinishedHandler = new TestStartUpFinishedHandler(lock, condition);
        TestShutdownFinishedHandler shutdownFinishedHandler = new TestShutdownFinishedHandler(lock, condition);

        // start & check is running
        oocut.startAsync(testListener, Vehicle.UNKNOWN, startUpFinishedHandler);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(startUpFinishedHandler.receivedMeasurementIdentifier, is(not(equalTo(-1L))));
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);

        // Check sensor data
        final List<Measurement> measurements = oocut.getCachedMeasurements();
        assertThat(measurements.size() > 0, is(equalTo(true)));
        ServiceTestUtils.lockAndWait(10, TimeUnit.SECONDS, lock, condition);
        assertThat(testListener.getCapturedData().size() > 0, is(equalTo(true)));

        // stop
        oocut.stopAsync(shutdownFinishedHandler);
        // Prevents this test interacting with other tests
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
    }

    /**
     * Tests whether reconnect throws no exception when called without a running background service and leaves the
     * DataCapturingService in the correct state (<code>isRunning</code> is <code>false</code>.
     *
     * @throws DataCapturingException Fails the test if anything goes wrong.
     */
    @Test
    public void testReconnectOnNonRunningServer() throws DataCapturingException {
        oocut.reconnect();
        assertThat(oocut.getIsRunning(), is(equalTo(false)));
    }
}
