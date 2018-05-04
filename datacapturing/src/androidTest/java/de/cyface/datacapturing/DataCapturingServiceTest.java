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

import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.exception.MissingPermissionException;
import de.cyface.datacapturing.exception.SetupException;
import de.cyface.datacapturing.model.Vehicle;
import de.cyface.persistence.MeasuringPointsContentProvider;
import de.cyface.synchronization.SynchronisationException;

/**
 * Tests whether the {@link DataCapturingService} works correctly. This is a flaky test since it starts a service that
 * relies on external sensors and the availability of a GPS signal. Each tests waits a few seconds to actually capture
 * some data, but it might still fail if you are indoors (which you will usually be while running tests, right?)
 *
 * @author Klemens Muthmann
 * @version 2.0.0
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
                                                                               oocut = new CyfaceDataCapturingService(context, context.getContentResolver(), "http://localhost:8080");
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
        oocut.start(testListener, Vehicle.UNKOWN);

        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);

        oocut.stop();
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

        oocut.start(testListener, Vehicle.UNKOWN);

        oocut.disconnect();
        oocut.reconnect();

        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);

        oocut.stop();
        assertThat(runningStatusCallback.wasRunning(), is(equalTo(true)));
    }

    /**
     * Tests that running start twice does not break the system. This test succeeds if no <code>Exception</code> occurs.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     */
    @Test
    public void testDoubleStart() throws DataCapturingException, MissingPermissionException {
        oocut.start(testListener, Vehicle.UNKOWN);
        oocut.start(testListener, Vehicle.UNKOWN);

        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        oocut.stop();
    }

    /**
     * Tests for the correct <code>Exception</code> when you try to stop a stopped service.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     */
    @Test(expected = DataCapturingException.class)
    public void testDoubleStop() throws DataCapturingException, MissingPermissionException {
        oocut.start(testListener, Vehicle.UNKOWN);

        oocut.stop();

        oocut.stop();
    }

    /**
     * Tests for the correct <code>Exception</code> if you try to disconnect from a diconnected service.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     */
    @Test(expected = DataCapturingException.class)
    public void testDoubleDisconnect() throws DataCapturingException, MissingPermissionException {
        oocut.start(testListener, Vehicle.UNKOWN);

        oocut.disconnect();
        oocut.disconnect();
        oocut.stop();
    }

    // TODO Stopping a disconnected service is actually necessary.
    // Do we really want to throw an Exception here?
    /**
     * Tests for the correct <code>Exception</code> if you try to stop a disconnected service.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     */
    @Test(expected = DataCapturingException.class)
    public void testStopNonConnectedService() throws DataCapturingException, MissingPermissionException {
        oocut.start(testListener, Vehicle.UNKOWN);

        oocut.disconnect();
        oocut.stop();
    }

    /**
     * Tests that no <code>Exception</code> is thrown when we try to connect to the same service twice.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     */
    @Test
    public void testDoubleConnect() throws DataCapturingException, MissingPermissionException {
        oocut.start(testListener, Vehicle.UNKOWN);

        oocut.disconnect();

        oocut.reconnect();
        oocut.reconnect();

        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        ServiceTestUtils.callCheckForRunning(oocut, runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        oocut.stop();
    }

    /**
     * Tests that two correct cycles of disconnect and reconnect on a running service work fine.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     */
    @Test
    public void testDisconnectConnectTwice() throws DataCapturingException, MissingPermissionException {
        ServiceTestUtils.callCheckForRunning(oocut,runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(runningStatusCallback.wasRunning(), is(equalTo(false)));
        oocut.start(testListener, Vehicle.UNKOWN);

        oocut.disconnect();
        oocut.reconnect();
        oocut.disconnect();
        oocut.reconnect();
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        ServiceTestUtils.callCheckForRunning(oocut,runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        oocut.stop();
        assertThat(runningStatusCallback.wasRunning(), is(equalTo(true)));
    }

    /**
     * Tests that starting a service twice throws no <code>Exception</code>.
     *
     * @throws DataCapturingException On any error during running the capturing process.
     * @throws MissingPermissionException If an Android permission is missing.
     */
    @Test
    public void testRestart() throws DataCapturingException, MissingPermissionException {
        ServiceTestUtils.callCheckForRunning(oocut,runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);

        oocut.start(testListener, Vehicle.UNKOWN);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        ServiceTestUtils.callCheckForRunning(oocut,runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(runningStatusCallback.wasRunning(), is(equalTo(true)));
        oocut.stop();

        oocut.start(testListener, Vehicle.UNKOWN);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        ServiceTestUtils.callCheckForRunning(oocut,runningStatusCallback);
        ServiceTestUtils.lockAndWait(2, TimeUnit.SECONDS, lock, condition);
        assertThat(runningStatusCallback.wasRunning(), is(equalTo(true)));
        oocut.stop();
    }


}
