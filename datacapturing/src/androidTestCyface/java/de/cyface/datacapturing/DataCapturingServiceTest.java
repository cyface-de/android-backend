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

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
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
import de.cyface.utils.Validate;

/**
 * Tests whether the {@link DataCapturingService} works correctly. This is a flaky test since it starts a service that
 * relies on external sensors and the availability of a GPS signal. Each tests waits a few seconds to actually capture
 * some data, but it might still fail if you are indoors (which you will usually be while running tests, right?)
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 4.2.2
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class DataCapturingServiceTest extends ProviderTestCase2<MeasuringPointsContentProvider> {

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

        // Add test account
        final Account requestAccount = new Account(ServiceTestUtils.DEFAULT_FREE_USERNAME,
                ServiceTestUtils.ACCOUNT_TYPE);
        AccountManager.get(context).addAccountExplicitly(requestAccount, ServiceTestUtils.DEFAULT_FREE_PASSWORD, null);

        // Start DataCapturingService
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                try {
                    oocut = new CyfaceDataCapturingService(context, context.getContentResolver(), AUTHORITY,
                            ACCOUNT_TYPE, "http://localhost:8080", new IgnoreEventsStrategy());
                } catch (SetupException e) {
                    throw new IllegalStateException(e);
                }
            }
        });

        // Prepare
        lock = new ReentrantLock();
        condition = lock.newCondition();
        testListener = new TestListener(lock, condition);
        runningStatusCallback = new TestCallback("Default Callback", lock, condition);

        // Making sure there is no service instance of a previous test running
        Validate.isTrue(!isDataCapturingServiceRunning());
    }

    /**
     * Tries to stop the DataCapturingService if a test failed to do so.
     */
    @After
    public void tearDown() throws Exception {
        if (isDataCapturingServiceRunning()) {
            oocut.stopAsync(new TestShutdownFinishedHandler(lock, condition));
        }
        super.tearDown();
    }

    /**
     * Makes sure a test did not forget to stop the capturing.
     */
    private boolean isDataCapturingServiceRunning() {
        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        return runningStatusCallback.wasRunning() && !runningStatusCallback.timedOut;
    }

    /**
     * Starts a {@link DataCapturingService} and checks that it's running afterwards.
     *
     * @return the measurement id of the started capturing
     */
    private long startAsyncAndCheckThatLaunched() throws MissingPermissionException, DataCapturingException {

        final TestStartUpFinishedHandler startUpFinishedHandler = new TestStartUpFinishedHandler(lock, condition);
        oocut.startAsync(testListener, Vehicle.UNKNOWN, startUpFinishedHandler);

        return checkThatLaunched(startUpFinishedHandler);
    }

    /**
     * Pauses a {@link DataCapturingService} and checks that it's not running afterwards.
     *
     * @param measurementIdentifier The if of the measurement expected to be closed.
     */
    private void pauseAsyncAndCheckThatStopped(long measurementIdentifier)
            throws NoSuchMeasurementException, DataCapturingException {

        final TestShutdownFinishedHandler shutDownFinishedHandler = new TestShutdownFinishedHandler(lock, condition);
        oocut.pauseAsync(shutDownFinishedHandler);

        checkThatStopped(shutDownFinishedHandler, measurementIdentifier);
    }

    /**
     * Resumes a {@link DataCapturingService} and checks that it's running afterwards.
     *
     * @param measurementIdentifier The id of the measurement which is expected to be resumed
     */
    private void resumeAsyncAndCheckThatLaunched(long measurementIdentifier)
            throws MissingPermissionException, DataCapturingException {

        final TestStartUpFinishedHandler startUpFinishedHandler = new TestStartUpFinishedHandler(lock, condition);
        oocut.resumeAsync(startUpFinishedHandler);

        final long resumedMeasurementId = checkThatLaunched(startUpFinishedHandler);
        assertThat(resumedMeasurementId, is(measurementIdentifier));
    }

    /**
     * Stops a {@link DataCapturingService} and checks that it's not running afterwards.
     *
     * @param measurementIdentifier The if of the measurement expected to be closed.
     */
    private void stopAsyncAndCheckThatStopped(final long measurementIdentifier)
            throws NoSuchMeasurementException, DataCapturingException {

        final TestShutdownFinishedHandler shutDownFinishedHandler = new TestShutdownFinishedHandler(lock, condition);
        oocut.stopAsync(shutDownFinishedHandler);

        checkThatStopped(shutDownFinishedHandler, measurementIdentifier);
    }

    /**
     *
     * Checks that a {@link DataCapturingService} which was just stopped is not running anymore
     * and that it closed the started measurement.
     *
     * @param startUpFinishedHandler The {@link TestShutdownFinishedHandler} used to start the service
     * @return The id of the measurement which is expected to be resumed
     */
    private long checkThatLaunched(final TestStartUpFinishedHandler startUpFinishedHandler) {

        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(startUpFinishedHandler.receivedServiceStarted(), is(equalTo(true)));

        // ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(runningStatusCallback.wasRunning(), is(equalTo(true)));
        assertThat(runningStatusCallback.didTimeOut(), is(equalTo(false)));

        assertThat(startUpFinishedHandler.receivedMeasurementIdentifier, is(not(equalTo(-1L))));

        return startUpFinishedHandler.receivedMeasurementIdentifier;
    }

    /**
     * Checks that a {@link DataCapturingService} which was just stopped is not running anymore
     * and that it closed the started measurement.
     *
     * @param shutDownFinishedHandler The {@link TestShutdownFinishedHandler} used to stop the service
     * @param measurementIdentifier The measurement which is expected to be closed
     */
    private void checkThatStopped(final TestShutdownFinishedHandler shutDownFinishedHandler,
            final long measurementIdentifier) {

        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(shutDownFinishedHandler.receivedServiceStopped(), is(equalTo(true)));
        // FIXME: why are the following two checks not working? Are they bond to the sync calls?
        // assertThat(runningStatusCallback.wasRunning(), is(equalTo(false)));
        // assertThat(runningStatusCallback.didTimeOut(), is(equalTo(true)));

        assertThat(shutDownFinishedHandler.receivedMeasurementIdentifier, is(equalTo(measurementIdentifier)));
    }

    /**
     * Tests a common service run.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test
    public void testStartStop() throws DataCapturingException, MissingPermissionException, NoSuchMeasurementException {

        final long receivedMeasurementIdentifier = startAsyncAndCheckThatLaunched();
        stopAsyncAndCheckThatStopped(receivedMeasurementIdentifier);
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
     */
    @Test
    @Ignore
    public void testMultipleStartStopWithoutDelay() throws DataCapturingException, MissingPermissionException, NoSuchMeasurementException {
        final TestStartUpFinishedHandler startUpFinishedHandler1 = new TestStartUpFinishedHandler(lock, condition);
        final TestStartUpFinishedHandler startUpFinishedHandler2 = new TestStartUpFinishedHandler(lock, condition);
        final TestStartUpFinishedHandler startUpFinishedHandler3 = new TestStartUpFinishedHandler(lock, condition);
        final TestShutdownFinishedHandler shutDownFinishedHandler1 = new TestShutdownFinishedHandler(lock, condition);
        final TestShutdownFinishedHandler shutDownFinishedHandler2 = new TestShutdownFinishedHandler(lock, condition);
        final TestShutdownFinishedHandler shutDownFinishedHandler3 = new TestShutdownFinishedHandler(lock, condition);

        // First Start/stop without waiting
        oocut.startAsync(testListener, Vehicle.UNKNOWN, startUpFinishedHandler1);
        oocut.stopAsync(shutDownFinishedHandler1);
        // Second start/stop without waiting
        oocut.startAsync(testListener, Vehicle.UNKNOWN, startUpFinishedHandler2);
        oocut.stopAsync(shutDownFinishedHandler2);
        // Second start/stop without waiting
        oocut.startAsync(testListener, Vehicle.UNKNOWN, startUpFinishedHandler3);
        oocut.stopAsync(shutDownFinishedHandler3);

        // Now let's make sure all measurements started and stopped as expected
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        final long measurementId1 = startUpFinishedHandler1.receivedMeasurementIdentifier;
        assertThat(measurementId1, is(not(equalTo(-1L))));
        assertThat(shutDownFinishedHandler1.receivedMeasurementIdentifier, is(equalTo(measurementId1)));
        final long measurementId2 = startUpFinishedHandler2.receivedMeasurementIdentifier;
        assertThat(measurementId2, is(not(equalTo(-1L))));
        assertThat(shutDownFinishedHandler2.receivedMeasurementIdentifier, is(equalTo(measurementId2)));
        final long measurementId3 = startUpFinishedHandler3.receivedMeasurementIdentifier;
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
    public void testDisconnectReconnect()
            throws DataCapturingException, MissingPermissionException, NoSuchMeasurementException {

        final long measurementIdentifier = startAsyncAndCheckThatLaunched();

        oocut.disconnect();
        oocut.reconnect();
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);

        stopAsyncAndCheckThatStopped(measurementIdentifier);
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

        final long measurementIdentifier = startAsyncAndCheckThatLaunched();

        // Second start - should not launch anything
        final TestStartUpFinishedHandler startUpFinishedHandler = new TestStartUpFinishedHandler(lock, condition);
        oocut.startAsync(testListener, Vehicle.UNKNOWN, startUpFinishedHandler);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(startUpFinishedHandler.receivedServiceStarted(), is(equalTo(false)));

        stopAsyncAndCheckThatStopped(measurementIdentifier);
    }

    /**
     * Tests that stopping a stopped service throws the expected exception.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test(expected = NoSuchMeasurementException.class)
    public void testDoubleStop() throws DataCapturingException, MissingPermissionException, NoSuchMeasurementException {

        final long measurementId = startAsyncAndCheckThatLaunched();

        stopAsyncAndCheckThatStopped(measurementId);
        // must throw NoSuchMeasurementException
        oocut.stopAsync(new TestShutdownFinishedHandler(lock, condition));
    }

    /**
     * Tests for the correct <code>Exception</code> if you try to disconnect from a disconnected service.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test(expected = DataCapturingException.class)
    public void testDoubleDisconnect()
            throws DataCapturingException, MissingPermissionException, NoSuchMeasurementException {

        final long measurementIdentifier = startAsyncAndCheckThatLaunched();

        oocut.disconnect();
        oocut.disconnect(); // must throw DataCapturingException
        stopAsyncAndCheckThatStopped(measurementIdentifier); // is called by tearDown
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

        final long measurementIdentifier = startAsyncAndCheckThatLaunched();

        oocut.disconnect();

        stopAsyncAndCheckThatStopped(measurementIdentifier);
    }

    /**
     * Tests that no <code>Exception</code> is thrown when we try to connect to the same service twice.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test
    public void testDoubleReconnect()
            throws DataCapturingException, MissingPermissionException, NoSuchMeasurementException {

        final long measurementIdentifier = startAsyncAndCheckThatLaunched();
        oocut.disconnect();

        oocut.reconnect();
        oocut.reconnect();
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);

        stopAsyncAndCheckThatStopped(measurementIdentifier);
    }

    /**
     * Tests that two correct cycles of disconnect and reconnect on a running service work fine.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test
    public void testDisconnectReconnectTwice()
            throws DataCapturingException, MissingPermissionException, NoSuchMeasurementException {

        final long measurementIdentifier = startAsyncAndCheckThatLaunched();

        oocut.disconnect();
        oocut.reconnect();
        oocut.disconnect();
        oocut.reconnect();

        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat("Service seems not to be running anymore after two disconnect/reconnect cycles!",
                runningStatusCallback.wasRunning(), is(equalTo(true)));

        stopAsyncAndCheckThatStopped(measurementIdentifier);
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

        final long measurementIdentifier = startAsyncAndCheckThatLaunched();
        stopAsyncAndCheckThatStopped(measurementIdentifier);

        final long measurementIdentifier2 = startAsyncAndCheckThatLaunched();
        assertTrue(measurementIdentifier2 != measurementIdentifier);
        stopAsyncAndCheckThatStopped(measurementIdentifier2);
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
    public void testResumeTwice()
            throws MissingPermissionException, DataCapturingException, NoSuchMeasurementException {

        final long measurementIdentifier = startAsyncAndCheckThatLaunched();
        pauseAsyncAndCheckThatStopped(measurementIdentifier);

        resumeAsyncAndCheckThatLaunched(measurementIdentifier);
        resumeAsyncAndCheckThatLaunched(measurementIdentifier);

        stopAsyncAndCheckThatStopped(measurementIdentifier);
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

        final long measurementIdentifier = startAsyncAndCheckThatLaunched();
        pauseAsyncAndCheckThatStopped(measurementIdentifier);
        stopAsyncAndCheckThatStopped(-1); // -1 because it's already stopped
    }

    /**
     * Tests if the service lifecycle is running successfully.
     * <p>
     * Makes sure the {@link MovebisDataCapturingService#pauseSync()} and
     * {@link MovebisDataCapturingService#resumeSync()}
     * work correctly.
     *
     * @throws DataCapturingException Happens on unexpected states during data capturing.
     * @throws MissingPermissionException Should not happen since a <code>GrantPermissionRule</code> is used.
     * @throws NoSuchMeasurementException Fails the test if the capturing measurement is lost somewhere.
     */
    @Test
    public void testStartPauseResumeStop()
            throws DataCapturingException, MissingPermissionException, NoSuchMeasurementException {

        final long measurementIdentifier = startAsyncAndCheckThatLaunched();

        // Check measurements
        final List<Measurement> measurements = oocut.getCachedMeasurements();
        assertThat(measurements.size() > 0, is(equalTo(true)));

        pauseAsyncAndCheckThatStopped(measurementIdentifier);

        resumeAsyncAndCheckThatLaunched(measurementIdentifier);

        // Check measurements again
        final List<Measurement> newMeasurements = oocut.getCachedMeasurements();
        assertThat(measurements.size() == newMeasurements.size(), is(equalTo(true)));

        // oocut.stopSync();
        stopAsyncAndCheckThatStopped(measurementIdentifier);
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

        final long measurementIdentifier = startAsyncAndCheckThatLaunched();

        // Check sensor data
        final List<Measurement> measurements = oocut.getCachedMeasurements();
        assertThat(measurements.size() > 0, is(equalTo(true)));
        ServiceTestUtils.lockAndWait(3, TimeUnit.SECONDS, lock, condition);
        assertThat(testListener.getCapturedData().size() > 0, is(equalTo(true)));

        stopAsyncAndCheckThatStopped(measurementIdentifier);
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
