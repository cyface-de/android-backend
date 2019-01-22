package de.cyface.datacapturing.backend;

import static de.cyface.datacapturing.BundlesExtrasCodes.EVENT_HANDLING_STRATEGY_ID;
import static de.cyface.datacapturing.TestUtils.AUTHORITY;
import static de.cyface.synchronization.TestUtils.clear;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.Context;
import android.content.Intent;
import android.os.Messenger;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.rule.ServiceTestRule;
import de.cyface.datacapturing.BundlesExtrasCodes;
import de.cyface.datacapturing.IgnoreEventsStrategy;
import de.cyface.datacapturing.PongReceiver;
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.Vehicle;
import de.cyface.utils.DataCapturingException;

/**
 * Tests whether the service handling the data capturing works correctly. Since the test relies on external sensors and
 * GPS signal availability it is a flaky test.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.1.0
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class BackgroundServiceTest {
    /**
     * Junit rule handling the service connection.
     */
    @Rule
    public ServiceTestRule serviceTestRule = new ServiceTestRule();

    /**
     * Grants the <code>ACCESS_FINE_LOCATION</code> permission while running this test.
     */
    @Rule
    public GrantPermissionRule mRuntimePermissionRule = GrantPermissionRule
            .grant(android.Manifest.permission.ACCESS_FINE_LOCATION);

    /**
     * The messenger used to receive messages from the data capturing service.
     */
    private Messenger fromServiceMessenger;

    /**
     * The identifier for the test measurement created in the <code>setUp</code> method.
     */
    private Measurement testMeasurement;

    /**
     * Lock used to synchronize the test case with the background service.
     */
    private Lock lock;
    /**
     * Condition waiting for the background service to message this service, that it is running.
     */
    private Condition condition;
    private Context context;

    @Before
    public void setUp() throws DataCapturingException {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        CapturingPersistenceBehaviour capturingBehaviour = new CapturingPersistenceBehaviour();
        // Required to create a test measurement.
        PersistenceLayer persistence = new PersistenceLayer(context, context.getContentResolver(), AUTHORITY,
                capturingBehaviour);
        // This is normally called in the <code>DataCapturingService#Constructor</code>
        persistence.restoreOrCreateDeviceId();
        testMeasurement = persistence.newMeasurement(Vehicle.BICYCLE);
        lock = new ReentrantLock();
        condition = lock.newCondition();
    }

    @After
    public void tearDown() {
        clear(context, context.getContentResolver());
        testMeasurement = null;
    }

    /**
     * This test case checks that starting the service works and that the service actually returns some data.
     */
    @Test
    public void testStartDataCapturing() throws TimeoutException {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final TestCallback testCallback = new TestCallback("testStartDataCapturing", lock, condition);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                FromServiceMessageHandler fromServiceMessageHandler = new FromServiceMessageHandler();
                fromServiceMessenger = new Messenger(fromServiceMessageHandler);
            }
        });
        final ToServiceConnection toServiceConnection = new ToServiceConnection(fromServiceMessenger);
        toServiceConnection.context = context;
        toServiceConnection.callback = testCallback;
        Intent startIntent = new Intent(context, DataCapturingBackgroundService.class);
        startIntent.putExtra(BundlesExtrasCodes.MEASUREMENT_ID, testMeasurement.getIdentifier());
        startIntent.putExtra(BundlesExtrasCodes.AUTHORITY_ID, AUTHORITY);
        startIntent.putExtra(EVENT_HANDLING_STRATEGY_ID, new IgnoreEventsStrategy());

        serviceTestRule.startService(startIntent);
        serviceTestRule.bindService(startIntent, toServiceConnection, 0);

        // This must not run on the main thread or it will produce an ANR.
        lock.lock();
        try {
            if (!testCallback.isRunning) {
                if (!condition.await(2, TimeUnit.MINUTES)) {
                    throw new IllegalStateException("Waiting for pong or timeout timed out!");
                }
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }

        serviceTestRule.unbindService();

        assertThat("It seems that service did not respond to a ping.", testCallback.isRunning, is(equalTo(true)));
        assertThat("It seems that the request to the service whether it was active timed out.", testCallback.timedOut,
                is(equalTo(false)));
    }

    /**
     * This test case checks that starting the service works and that the service actually returns some data.
     */
    @Test
    public void testStartDataCapturingTwice() throws TimeoutException {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        Intent startIntent = new Intent(context, DataCapturingBackgroundService.class);
        startIntent.putExtra(BundlesExtrasCodes.MEASUREMENT_ID, testMeasurement.getIdentifier());
        startIntent.putExtra(BundlesExtrasCodes.AUTHORITY_ID, AUTHORITY);
        startIntent.putExtra(EVENT_HANDLING_STRATEGY_ID, new IgnoreEventsStrategy());
        serviceTestRule.startService(startIntent);
        serviceTestRule.startService(startIntent);

        final TestCallback testCallback = new TestCallback("testStartDataCapturingTwice", lock, condition);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                PongReceiver isRunningChecker = new PongReceiver(context);
                isRunningChecker.checkIsRunningAsync(2, TimeUnit.SECONDS, testCallback);
            }
        });

        // This must not run on the main thread or it will produce an ANR.
        lock.lock();
        try {
            if (!testCallback.isRunning) {
                if (!condition.await(2, TimeUnit.MINUTES)) {
                    throw new IllegalStateException("Waiting for pong or timeout timed out!");
                }
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }

        assertThat("It seems that service did not respond to a ping.", testCallback.isRunning, is(equalTo(true)));
        assertThat("It seems that the request to the service whether it was active timed out.", testCallback.timedOut,
                is(equalTo(false)));
    }

}
