package de.cyface.datacapturing;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.FlakyTest;
import android.support.test.filters.LargeTest;
import android.support.test.rule.GrantPermissionRule;
import android.support.test.rule.ServiceTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.ProviderTestCase2;
import android.util.Log;

import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.model.CapturedData;
import de.cyface.datacapturing.model.GeoLocation;
import de.cyface.datacapturing.model.Vehicle;
import de.cyface.persistence.MeasuringPointsContentProvider;
import de.cyface.synchronization.SynchronisationException;

/**
 * Tests whether the {@link DataCapturingService} works correctly. This is a flaky test since it starts a service that
 * relies on external sensors and the availability of a GPS signal. Each tests waits a few seconds to actually capture
 * some data, but it might still fail if you are indoors (which you will usually be while running tests, right?)
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4.class)
@FlakyTest
@LargeTest
@Ignore
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

    private IsRunningStatus runningStatusCallback;

    private Lock lock;

    private Condition condition;

    /**
     * Required constructor.
     */
    public DataCapturingServiceTest() {
        super(MeasuringPointsContentProvider.class, de.cyface.persistence.BuildConfig.provider);
    }

    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        // WARNING: Never change the order of the following two lines, even though the Google documentation tells you
        // something different!
        setContext(context);
        super.setUp();

        oocut = new CyfaceDataCapturingService(context, context.getContentResolver(), "http://localhost:8080");
        testListener = new TestListener(lock, condition);
        lock = new ReentrantLock();
        condition = lock.newCondition();
        runningStatusCallback = new IsRunningStatus(lock, condition);
    }

    /**
     * Tests a common service run. Checks that some positons have been captured.
     */
    @Test
    public void testRunDataCapturingServiceSuccessfully() throws DataCapturingException {
        callStartOnMainThread();

        lockAndWait(2, TimeUnit.SECONDS);
        callCheckForRunning();
        lockAndWait(2, TimeUnit.SECONDS);

        oocut.stop();
        assertThat(runningStatusCallback.wasRunning(), is(equalTo(true)));
    }

    /**
     * Tests a common service run with an intermidiate disconnect and reconnect by the application. No problems should
     * occur and some points should be captured.
     */
    @Test
    public void testDisconnectConnect() throws DataCapturingException {
        callCheckForRunning();
        lockAndWait(2, TimeUnit.SECONDS);
        assertThat(runningStatusCallback.wasRunning(), is(equalTo(false)));

        callStartOnMainThread();

        oocut.disconnect();
        oocut.reconnect();

        lockAndWait(2, TimeUnit.SECONDS);
        callCheckForRunning();
        lockAndWait(2, TimeUnit.SECONDS);

        oocut.stop();
        assertThat(runningStatusCallback.wasRunning(), is(equalTo(false)));
    }

    /**
     * Tests that running start twice does not break the system. This test succeeds if no <code>Exception</code> occurs.
     */
    @Test
    public void testDoubleStart() throws SynchronisationException, DataCapturingException {
        callStartOnMainThread();
        callStartOnMainThread();

        lockAndWait(2, TimeUnit.SECONDS);
        callCheckForRunning();
        lockAndWait(2, TimeUnit.SECONDS);
        oocut.stop();
    }

    /**
     * Tests for the correct <code>Exception</code> when you try to stop a stopped service.
     */
    @Test(expected = DataCapturingException.class)
    public void testDoubleStop() throws SynchronisationException, DataCapturingException {
        callStartOnMainThread();

        oocut.stop();

        oocut.stop();
    }

    /**
     * Tests for the correct <code>Exception</code> if you try to disconnect from a diconnected service.
     */
    @Test(expected = DataCapturingException.class)
    public void testDoubleDisconnect() throws DataCapturingException, SynchronisationException {
        callStartOnMainThread();

        oocut.disconnect();
        oocut.disconnect();
        oocut.stop();
    }

    // TODO Stopping a disconnected service is actually necessary.
    // Do we really want to throw an Exception here?
    /**
     * Tests for the correct <code>Exception</code> if you try to stop a disconnected service.
     */
    @Test(expected = DataCapturingException.class)
    public void testStopNonConnectedService() throws DataCapturingException, SynchronisationException {
        callStartOnMainThread();

        oocut.disconnect();
        oocut.stop();
    }

    /**
     * Tests that no <code>Exception</code> is thrown when we try to connect to the same service twice.
     */
    @Test
    public void testDoubleConnect() throws DataCapturingException, SynchronisationException {
        callStartOnMainThread();

        oocut.disconnect();

        oocut.reconnect();
        oocut.reconnect();

        lockAndWait(2, TimeUnit.SECONDS);
        callCheckForRunning();
        lockAndWait(2, TimeUnit.SECONDS);
        oocut.stop();
    }

    /**
     * Tests that two correct cycles of disconnect and reconnect on a running service work fine.
     */
    @Test
    public void testDisconnectConnectTwice() throws DataCapturingException, SynchronisationException {
        callCheckForRunning();
        lockAndWait(2, TimeUnit.SECONDS);
        assertThat(runningStatusCallback.wasRunning(), is(equalTo(false)));
        callStartOnMainThread();

        oocut.disconnect();
        oocut.reconnect();
        oocut.disconnect();
        oocut.reconnect();
        lockAndWait(2, TimeUnit.SECONDS);
        callCheckForRunning();
        lockAndWait(2, TimeUnit.SECONDS);
        oocut.stop();
        assertThat(runningStatusCallback.wasRunning(), is(equalTo(true)));
    }

    /**
     * Tests that starting a service twice throws no <code>Exception</code>.
     */
    @Test
    public void testRestart() throws SynchronisationException, DataCapturingException {
        callCheckForRunning();
        lockAndWait(2, TimeUnit.SECONDS);

        callStartOnMainThread();
        lockAndWait(2, TimeUnit.SECONDS);
        callCheckForRunning();
        lockAndWait(2, TimeUnit.SECONDS);
        assertThat(runningStatusCallback.wasRunning(), is(equalTo(true)));
        oocut.stop();

        callStartOnMainThread();
        lockAndWait(2, TimeUnit.SECONDS);
        callCheckForRunning();
        lockAndWait(2, TimeUnit.SECONDS);
        assertThat(runningStatusCallback.wasRunning(), is(equalTo(true)));
        oocut.stop();
    }

    private void lockAndWait(final long time, final @NonNull TimeUnit unit) {
        lock.lock();
        try {
            condition.await(time, unit);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }
    }

    private void callCheckForRunning() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                oocut.isRunning(1, TimeUnit.SECONDS, runningStatusCallback);
            }
        });
    }

    private void callStartOnMainThread() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                try {
                    oocut.start(testListener, Vehicle.UNKOWN);
                } catch (DataCapturingException e) {
                    throw new IllegalStateException(e);
                }
            }
        });
    }

    /**
     * A listener for events from the capturing service, only used by tests.
     *
     * @author Klemens Muthmann
     * @version 1.1.0
     * @since 2.0.0
     */
    private static class TestListener implements DataCapturingListener {

        private final Lock lock;
        private final Condition condition;
        /**
         * Geo locations captured during the test run.
         */
        final List<GeoLocation> capturedPositions;

        private TestListener(final @NonNull Lock lock, final @NonNull Condition condition) {
            capturedPositions = new ArrayList<>();
            this.lock = lock;
            this.condition = condition;
        }

        @Override
        public void onFixAcquired() {
            Log.d(TAG, "Fix acquired!");
        }

        @Override
        public void onFixLost() {
            Log.d(TAG, "GPS fix lost!");
        }

        @Override
        public void onNewGeoLocationAcquired(final @NonNull GeoLocation position) {
            Log.d(TAG, String.format("New GPS position (lat:%f,lon:%f)", position.getLat(), position.getLon()));
            capturedPositions.add(position);
        }

        @Override
        public void onNewSensorDataAcquired(CapturedData data) {
            Log.d(TAG, "New Sensor data.");
        }

        @Override
        public void onLowDiskSpace(final @NonNull DiskConsumption allocation) {

        }

        @Override
        public void onSynchronizationSuccessful() {

        }

        @Override
        public void onErrorState(Exception e) {

        }
    }

    private static class IsRunningStatus implements IsRunningCallback {

        private boolean wasRunning;
        private boolean didTimeOut;
        private Lock lock;
        private Condition condition;

        public IsRunningStatus(final @NonNull Lock lock, final @NonNull Condition condition) {
            this.wasRunning = false;
            this.didTimeOut = false;
            this.lock = lock;
            this.condition = condition;
        }

        @Override
        public void isRunning() {
            lock.lock();
            try {
                wasRunning = true;
                condition.signal();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void timedOut() {
            lock.lock();
            try {
                didTimeOut = true;
                condition.signal();
            } finally {
                lock.unlock();
            }
        }

        public boolean wasRunning() {
            return wasRunning;
        }

        public boolean didTimeOut() {
            return didTimeOut;
        }
    }
}
