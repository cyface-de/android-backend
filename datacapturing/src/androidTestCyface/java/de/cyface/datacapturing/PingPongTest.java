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
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import de.cyface.datacapturing.backend.TestCallback;
import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.exception.MissingPermissionException;
import de.cyface.datacapturing.exception.SetupException;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.model.Vehicle;

/**
 * This test checks that the ping pong mechanism, which is used to check if a service is running or not, works as
 * expected.
 *
 * @author Klemens Muthmann
 * @since 2.3.4
 * @version 1.0.0
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class PingPongTest {

    /**
     * The time to wait for the pong message to return and for the service to start or stop.
     */
    private static final long TIMEOUT_TIME = 10L;

    /**
     * Grants the permission required by the {@link DataCapturingService}.
     */
    @Rule
    public GrantPermissionRule mRuntimePermissionRule = GrantPermissionRule
            .grant(android.Manifest.permission.ACCESS_FINE_LOCATION);

    /**
     * An instance of the class under test (object of class under test).
     */
    private PongReceiver oocut;
    /**
     * Lock used to synchronize the asynchronous calls to the {@link DataCapturingService} with the test thread.
     */
    private Lock lock;
    /**
     * Condition used to synchronize the asynchronous calls to the {@link DataCapturingService} with the test thread.
     */
    private Condition condition;
    /**
     * The {@link DataCapturingService} instance used by the test to check whether a pong can be received.
     */
    private DataCapturingService dcs;

    /**
     * Sets up all the instances required by all tests in this test class.
     */
    @Before
    public void setUp() {
        lock = new ReentrantLock();
        condition = lock.newCondition();
        oocut = new PongReceiver(InstrumentationRegistry.getInstrumentation().getTargetContext());
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
    public void testWithRunningService()
            throws MissingPermissionException, DataCapturingException, NoSuchMeasurementException {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                try {
                    dcs = new CyfaceDataCapturingService(context, ServiceTestUtils.AUTHORITY,
                            ServiceTestUtils.ACCOUNT_TYPE, "https://fake.fake/", new IgnoreEventsStrategy());
                } catch (SetupException e) {
                    throw new IllegalStateException(e);
                }
            }
        });
        DataCapturingListener listener = new TestListener(lock, condition);
        StartUpFinishedHandler finishedHandler = new TestStartUpFinishedHandler(lock, condition);

        dcs.start(listener, Vehicle.UNKNOWN, finishedHandler);

        lock.lock();
        try {
            condition.await(TIMEOUT_TIME, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }

        TestCallback testCallback = new TestCallback("testWithRunningService", lock, condition);
        oocut.checkIsRunningAsync(TIMEOUT_TIME, TimeUnit.SECONDS, testCallback);

        lock.lock();
        try {
            condition.await(2 * TIMEOUT_TIME, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }

        assertThat(testCallback.wasRunning(), is(equalTo(true)));
        assertThat(testCallback.didTimeOut(), is(equalTo(false)));

        TestShutdownFinishedHandler shutdownHandler = new TestShutdownFinishedHandler(lock, condition);
        dcs.stop(shutdownHandler);

        lock.lock();
        try {
            condition.await(TIMEOUT_TIME, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Tests that the {@link PongReceiver} works without crashing as expected even when no service runs.
     */
    @Test
    public void testWithNonRunningService() {
        TestCallback testCallback = new TestCallback("testWithNonRunningService", lock, condition);

        oocut.checkIsRunningAsync(TIMEOUT_TIME, TimeUnit.SECONDS, testCallback);

        lock.lock();
        try {
            condition.await(2 * TIMEOUT_TIME, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }

        assertThat(testCallback.didTimeOut(), is(equalTo(true)));
        assertThat(testCallback.wasRunning(), is(equalTo(false)));
    }

}
