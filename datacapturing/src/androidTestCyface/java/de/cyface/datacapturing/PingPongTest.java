package de.cyface.datacapturing;

import static de.cyface.datacapturing.TestUtils.AUTHORITY;
import static de.cyface.testutils.SharedTestUtils.clearPersistenceLayer;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.accounts.AccountAuthenticatorActivity;
import android.content.ContentProvider;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import de.cyface.datacapturing.backend.DataCapturingBackgroundService;
import de.cyface.datacapturing.backend.TestCallback;
import de.cyface.datacapturing.exception.CorruptedMeasurementException;
import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.exception.MissingPermissionException;
import de.cyface.datacapturing.exception.SetupException;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.model.Vehicle;
import de.cyface.synchronization.CyfaceAuthenticator;
import de.cyface.utils.CursorIsNullException;

/**
 * This test checks that the ping pong mechanism works as expected. This mechanism ist used to check if a service, in
 * this case the {@link DataCapturingBackgroundService}, is running or not.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.2.2
 * @since 2.3.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class PingPongTest {

    /**
     * The time to wait for the pong message to return and for the service to start or stop.
     */
    public static final long TIMEOUT_TIME = 1L; // FIXME
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
     * The {@link Context} required to send unique broadcasts and to start the capturing service.
     */
    private Context context;

    /**
     * Sets up all the instances required by all tests in this test class.
     *
     */
    @Before
    public void setUp() {
        lock = new ReentrantLock();
        condition = lock.newCondition();
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        oocut = new PongReceiver(context, context.getPackageName());
    }

    @After
    public void tearDown() {
        clearPersistenceLayer(context, context.getContentResolver(), AUTHORITY);
    }

    /**
     * Tests the ping pong with a running service. In that case it should successfully finish one round of ping/pong
     * with that service.
     *
     * @throws MissingPermissionException Should not happen, since there is a JUnit rule to prevent it.
     * @throws DataCapturingException If data capturing was not possible after starting the service.
     * @throws NoSuchMeasurementException If the service lost track of the measurement.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test
    public void testWithRunningService() throws MissingPermissionException, DataCapturingException,
            NoSuchMeasurementException, CursorIsNullException, CorruptedMeasurementException {

        // Arrange
        // Instantiate DataCapturingService
        final DataCapturingListener testListener = new TestListener(lock, condition);
        // The LOGIN_ACTIVITY is normally set to the LoginActivity of the SDK implementing app
        CyfaceAuthenticator.LOGIN_ACTIVITY = AccountAuthenticatorActivity.class;
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                try {
                    dcs = new CyfaceDataCapturingService(context, context.getContentResolver(), TestUtils.AUTHORITY,
                            TestUtils.ACCOUNT_TYPE, "https://fake.fake/", new IgnoreEventsStrategy(), testListener);
                } catch (SetupException | CursorIsNullException e) {
                    throw new IllegalStateException(e);
                }
            }
        });

        // Start Capturing
        StartUpFinishedHandler finishedHandler = new TestStartUpFinishedHandler(lock, condition,
                context.getPackageName());
        dcs.start(Vehicle.UNKNOWN, finishedHandler);

        // Give the async start some time to start the DataCapturingBackgroundService
        lock.lock();
        try {
            condition.await(TIMEOUT_TIME, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }

        // Act
        // Check if DataCapturingBackgroundService is running
        TestCallback testCallback = new TestCallback("testWithRunningService", lock, condition);
        oocut.checkIsRunningAsync(TIMEOUT_TIME, TimeUnit.SECONDS, testCallback);

        // Give the async call some time
        lock.lock();
        try {
            condition.await(2 * TIMEOUT_TIME, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }

        // Assert
        // Ensure DataCapturingBackgroundService was running during the async check
        assertThat(testCallback.wasRunning(), is(equalTo(true)));
        assertThat(testCallback.didTimeOut(), is(equalTo(false)));

        // Cleanup
        // Stop Capturing
        TestShutdownFinishedHandler shutdownHandler = new TestShutdownFinishedHandler(lock, condition);
        dcs.stop(shutdownHandler);

        // Give the async stop some time to stop gracefully
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

        // Act
        // Check if DataCapturingBackgroundService is running
        TestCallback testCallback = new TestCallback("testWithNonRunningService", lock, condition);
        oocut.checkIsRunningAsync(TIMEOUT_TIME, TimeUnit.SECONDS, testCallback);

        // Give the async call some time
        lock.lock();
        try {
            condition.await(2 * TIMEOUT_TIME, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }

        // Assert
        // Ensure DataCapturingBackgroundService was running during the async check
        assertThat(testCallback.didTimeOut(), is(equalTo(true)));
        assertThat(testCallback.wasRunning(), is(equalTo(false)));
    }

}
