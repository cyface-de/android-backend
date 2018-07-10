package de.cyface.datacapturing.backend;

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
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.rule.GrantPermissionRule;
import android.support.test.rule.ServiceTestRule;
import android.support.test.runner.AndroidJUnit4;

import de.cyface.datacapturing.BundlesExtrasCodes;
import de.cyface.datacapturing.PongReceiver;
import de.cyface.datacapturing.model.Vehicle;
import de.cyface.datacapturing.persistence.MeasurementPersistence;

/**
 * Tests whether the service handling the data capturing works correctly. Since the test relies on external sensors and
 * GPS signal availability it is a flaky test.
 *
 * @author Klemens Muthmann
 * @version 2.0.1
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class BackgroundServiceTest {

    /**
     * The tag used to identify log messages send to logcat.
     */
    private static final String TAG = "de.cyface.test";

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
     * Required to create a test measurement.
     */
    private MeasurementPersistence persistence;

    /**
     * The identifier for the test measurement created in the <code>setUp</code> method.
     */
    private long testMeasurementIdentifier;

    /**
     * Lock used to synchronize the test case with the background service.
     */
    private Lock lock;
    /**
     * Condition waiting for the background service to message this service, that it is running.
     */
    private Condition condition;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getTargetContext();
        persistence = new MeasurementPersistence(context.getContentResolver());
        testMeasurementIdentifier = persistence.newMeasurement(Vehicle.BICYCLE);
        lock = new ReentrantLock();
        condition = lock.newCondition();
    }

    @After
    public void tearDown() {
        persistence.clear();
        testMeasurementIdentifier = -1;
    }

    /**
     * This test case checks that starting the service works and that the service actually returns some data.
     * 
     * @throws InterruptedException If test execution is interrupted externally. This should never really happen, but we
     *             need to throw the exception anyways.
     */
    @Test
    public void testStartDataCapturing() throws InterruptedException, TimeoutException {
        final Context context = InstrumentationRegistry.getTargetContext();
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
        startIntent.putExtra(BundlesExtrasCodes.START_WITH_MEASUREMENT_ID, testMeasurementIdentifier);

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
     *
     * @throws InterruptedException If test execution is interrupted externally. This should never really happen, but we
     *             need to throw the exception anyways.
     */
    @Test
    public void testStartDataCapturingTwice() throws InterruptedException, TimeoutException {
        final Context context = InstrumentationRegistry.getTargetContext();

        Intent startIntent = new Intent(context, DataCapturingBackgroundService.class);
        startIntent.putExtra(BundlesExtrasCodes.START_WITH_MEASUREMENT_ID, testMeasurementIdentifier);
        serviceTestRule.startService(startIntent);
        serviceTestRule.startService(startIntent);

        final TestCallback testCallback = new TestCallback("testStartDataCapturingTwice", lock, condition);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                PongReceiver isRunningChecker = new PongReceiver(context);
                isRunningChecker.pongAndReceive(2, TimeUnit.SECONDS, testCallback);
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
