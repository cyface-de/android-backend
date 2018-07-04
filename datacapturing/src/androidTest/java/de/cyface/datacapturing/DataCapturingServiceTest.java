package de.cyface.datacapturing;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.rule.GrantPermissionRule;
import android.support.test.rule.ServiceTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.ProviderTestCase2;
import android.util.Log;

import de.cyface.datacapturing.backend.TestCallback;
import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.exception.MissingPermissionException;
import de.cyface.datacapturing.exception.SetupException;
import de.cyface.datacapturing.model.Vehicle;
import de.cyface.persistence.MeasuringPointsContentProvider;

/**
 * Tests whether the {@link DataCapturingService} works correctly. This is a flaky test since it starts a service that
 * relies on external sensors and the availability of a GPS signal. Each tests waits a few seconds to actually capture
 * some data, but it might still fail if you are indoors (which you will usually be while running tests, right?)
 *
 * @author Klemens Muthmann
 * @version 2.1.0
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
    private IsRunningStatus runningStatusCallback;

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
        super(MeasuringPointsContentProvider.class, de.cyface.persistence.BuildConfig.provider);
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

        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                try {
                    oocut = new CyfaceDataCapturingService(context, context.getContentResolver(),
                            "http://localhost:8080");
                } catch (SetupException e) {
                    throw new IllegalStateException(e);
                }
            }
        });

        lock = new ReentrantLock();
        condition = lock.newCondition();
        testListener = new TestListener(lock, condition);
        runningStatusCallback = new IsRunningStatus(lock, condition);
    }

    /**
     * Tests a common service run. Checks that some positons have been captured.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     */
    @Test
    public void testRunDataCapturingServiceSuccessfully() throws DataCapturingException, MissingPermissionException {
        oocut.startSync(testListener, Vehicle.UNKOWN);

        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);

        oocut.stopSync();
        assertThat(runningStatusCallback.wasRunning(), is(equalTo(true)));
    }

    /**
     * Tests a common service run with an intermediate disconnect and reconnect by the application. No problems should
     * occur and some points should be captured.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     */
    @Test
    public void testDisconnectConnect() throws DataCapturingException, MissingPermissionException {
        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(runningStatusCallback.wasRunning(), is(equalTo(false)));

        oocut.startSync(testListener, Vehicle.UNKOWN);

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
     */
    @Test
    public void testDoubleStart() throws DataCapturingException, MissingPermissionException {
        oocut.startSync(testListener, Vehicle.UNKOWN);
        oocut.startSync(testListener, Vehicle.UNKOWN);

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
     */
    @Test
    public void testDoubleStop() throws DataCapturingException, MissingPermissionException {
        oocut.startAsync(testListener, Vehicle.UNKOWN, new StartUpFinishedHandler() {
            @Override
            public void startUpFinished() {
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
            public void shutDownFinished() {
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
            public void shutDownFinished() {
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
     */
    @Test(expected = DataCapturingException.class)
    public void testDoubleDisconnect() throws DataCapturingException, MissingPermissionException {
        oocut.startSync(testListener, Vehicle.UNKOWN);

        oocut.disconnect();
        oocut.disconnect();
        oocut.stopSync();
    }

    /**
     * Tests that no <code>Exception</code> occurs if you stop a disconnected service.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     */
    @Test
    public void testStopNonConnectedService() throws DataCapturingException, MissingPermissionException {
        oocut.startAsync(testListener, Vehicle.UNKOWN, new StartUpFinishedHandler() {
            @Override
            public void startUpFinished() {
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
            public void shutDownFinished() {
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
     */
    @Test
    public void testDoubleConnect() throws DataCapturingException, MissingPermissionException {
        oocut.startSync(testListener, Vehicle.UNKOWN);

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
     */
    @Test
    public void testDisconnectConnectTwice() throws DataCapturingException, MissingPermissionException {
        // Service should not run in the beginning!
        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat("Service seems to be running before even started!", runningStatusCallback.wasRunning(),
                is(equalTo(false)));

        // Start service and wait for it to run.
        oocut.startAsync(testListener, Vehicle.UNKOWN, new StartUpFinishedHandler() {
            @Override
            public void startUpFinished() {
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

        oocut.stopAsync(new ShutDownFinishedHandler() {
            @Override
            public void shutDownFinished() {
                lock.lock();
                try {
                    condition.signal();
                } finally {
                    lock.unlock();
                }
            }
        });
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
     */
    @Test
    public void testRestart() throws DataCapturingException, MissingPermissionException {
        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(runningStatusCallback.wasRunning(), is(equalTo(false)));

        oocut.startSync(testListener, Vehicle.UNKOWN);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(runningStatusCallback.wasRunning(), is(equalTo(true)));
        oocut.stopSync();

        oocut.startSync(testListener, Vehicle.UNKOWN);
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
     */
    @Test
    public void testRunServiceAsync() throws DataCapturingException, MissingPermissionException {
        StartUpFinishedHandler startUpFinishedHandler = new TestStartUpFinishedHandler();
        oocut.startAsync(testListener, Vehicle.UNKOWN, startUpFinishedHandler);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(startUpFinishedHandler.receivedServiceStarted(), is(equalTo(true)));

        ShutDownFinishedHandler shutDownFinishedHandler = new MyShutDownFinishedHandler();
        oocut.pauseAsync(shutDownFinishedHandler);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(shutDownFinishedHandler.receivedServiceStopped(), is(equalTo(true)));

        startUpFinishedHandler = new TestStartUpFinishedHandler();
        oocut.resumeAsync(startUpFinishedHandler);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(startUpFinishedHandler.receivedServiceStarted(), is(equalTo(true)));

        shutDownFinishedHandler = new MyShutDownFinishedHandler();
        oocut.stopAsync(shutDownFinishedHandler);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(shutDownFinishedHandler.receivedServiceStopped(), is(equalTo(true)));
    }

    @Test
    public void testResumeAsyncTwice() throws MissingPermissionException, DataCapturingException {
        StartUpFinishedHandler startUpFinishedHandler = new TestStartUpFinishedHandler();
        oocut.startAsync(testListener, Vehicle.UNKOWN, startUpFinishedHandler);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(startUpFinishedHandler.receivedServiceStarted(), is(equalTo(true)));

        ShutDownFinishedHandler shutDownFinishedHandler = new MyShutDownFinishedHandler();
        oocut.pauseAsync(shutDownFinishedHandler);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(shutDownFinishedHandler.receivedServiceStopped(), is(equalTo(true)));

        startUpFinishedHandler = new TestStartUpFinishedHandler();
        oocut.resumeAsync(startUpFinishedHandler);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(startUpFinishedHandler.receivedServiceStarted(), is(equalTo(true)));

        startUpFinishedHandler = new TestStartUpFinishedHandler();
        oocut.resumeAsync(startUpFinishedHandler);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(startUpFinishedHandler.receivedServiceStarted(), is(equalTo(true)));

        shutDownFinishedHandler = new MyShutDownFinishedHandler();
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

    @Test
    public void testStartPauseStop() throws MissingPermissionException, DataCapturingException {
        StartUpFinishedHandler startUpFinishedHandler = new TestStartUpFinishedHandler();
        oocut.startAsync(testListener, Vehicle.UNKOWN, startUpFinishedHandler);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(startUpFinishedHandler.receivedServiceStarted(), is(equalTo(true)));

        ShutDownFinishedHandler shutDownFinishedHandler = new MyShutDownFinishedHandler();
        oocut.pauseAsync(shutDownFinishedHandler);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(shutDownFinishedHandler.receivedServiceStopped(), is(equalTo(true)));

        shutDownFinishedHandler = new MyShutDownFinishedHandler();
        oocut.stopAsync(shutDownFinishedHandler);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(shutDownFinishedHandler.receivedServiceStopped(), is(equalTo(true)));
    }

    /**
     * Tests whether reconnect throws an exception when called without a running background service and leaves the
     * DataCapturingService in the correct state (<code>isRunning</code> is <code>false</code>.
     *
     * @throws DataCapturingException The exception that should occur.
     */
    @Test
    public void testReconnectOnNonRunningServer() throws DataCapturingException {
        try {
            oocut.reconnect();
        } catch (DataCapturingException e) {
            assertThat(oocut.getIsRunning(), is(equalTo(false)));
            return;
        }
        fail("No DataCapturingException occured when reconnecting without a running service!");
    }

    /**
     * A handler for service shutdown messages synchronized by the tests synchronization lock.
     *
     * @author Klemens Muthmann
     * @version 1.0.0
     * @since 2.0.0
     */
    private class MyShutDownFinishedHandler extends ShutDownFinishedHandler {
        @Override
        public void shutDownFinished() {
            lock.lock();
            try {
                condition.signal();
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * A handler for finished events on service start up. This implementation synchronizes the start up with the calling
     * test case.
     *
     * @author Klemens Muthmann
     * @version 1.0.0
     * @since 2.0.0
     */
    private class TestStartUpFinishedHandler extends StartUpFinishedHandler {
        @Override
        public void startUpFinished() {
            lock.lock();
            try {
                condition.signal();
            } finally {
                lock.unlock();
            }
        }
    }
}
