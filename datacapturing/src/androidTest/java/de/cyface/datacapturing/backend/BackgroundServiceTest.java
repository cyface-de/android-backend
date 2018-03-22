package de.cyface.datacapturing.backend;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.List;
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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.rule.GrantPermissionRule;
import android.support.test.rule.ServiceTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import de.cyface.datacapturing.IsRunningCallback;
import de.cyface.datacapturing.MessageCodes;
import de.cyface.datacapturing.PongReceiver;
import de.cyface.datacapturing.model.CapturedData;
import de.cyface.datacapturing.model.GeoLocation;
import de.cyface.datacapturing.model.Vehicle;
import de.cyface.datacapturing.persistence.MeasurementPersistence;

/**
 * Tests whether the service handling the data capturing works correctly. Since the test relies on external sensors and
 * GPS signal availability it is a flaky test.
 *
 * @author Klemens Muthmann
 * @version 2.0.0
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
     * Grants the ACCESS_FINE_LOCATION permission while running this test.
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

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getTargetContext();
        persistence = new MeasurementPersistence(context.getContentResolver());
        persistence.newMeasurement(Vehicle.BICYCLE);
    }

    @After
    public void tearDown() {
        persistence.clear();
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
        final TestCallback testCallback = new TestCallback();
        final Lock lock = new ReentrantLock();
        final Condition condition = lock.newCondition();
        testCallback.lock = lock;
        testCallback.condition = condition;
        final ToServiceConnection toServiceConnection = new ToServiceConnection();
        toServiceConnection.context = context;
        toServiceConnection.callback = testCallback;

        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                FromServiceMessageHandler fromServiceMessageHandler = new FromServiceMessageHandler();
                fromServiceMessenger = new Messenger(fromServiceMessageHandler);
            }
        });
        Intent startIntent = new Intent(context, DataCapturingBackgroundService.class);

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
        Lock lock = new ReentrantLock();
        Condition condition = lock.newCondition();

        Intent startIntent = new Intent(context, DataCapturingBackgroundService.class);
        serviceTestRule.startService(startIntent);
        serviceTestRule.startService(startIntent);

        final TestCallback testCallback = new TestCallback();
        testCallback.lock = lock;
        testCallback.condition = condition;

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

    /**
     * Connection from the test to the capturing service.
     *
     * @author Klemens Muthmann
     * @version 1.1.0
     * @since 2.0.0
     */
    private class ToServiceConnection implements ServiceConnection {

        /**
         * The context this <code>ServiceConnection</code> runs with.
         */
        Context context;
        /**
         * Callback used to check the success or non success of the service startup.
         */
        TestCallback callback;

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d(TAG, "onServiceConnected");
            /*
             * The messenger used to send messages to the data capturing service.
             */
            Messenger toServiceMessenger = new Messenger(iBinder);

            try {
                Message msg = Message.obtain(null, MessageCodes.REGISTER_CLIENT);
                msg.replyTo = fromServiceMessenger;
                toServiceMessenger.send(msg);
            } catch (RemoteException e) {
                throw new IllegalStateException(e);
            }

            PongReceiver isRunningChecker = new PongReceiver(context);
            isRunningChecker.pongAndReceive(1, TimeUnit.MINUTES, callback);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(TAG, "onServiceDisonnected");
        }

        @Override
        public void onBindingDied(ComponentName name) {
            Log.d(TAG, "bindingDied");
        }
    }

    /**
     * A handler for messages received from the capturing service.
     *
     * @author Klemens Muthmann
     * @version 1.0.0
     * @since 2.0.0
     */
    private class FromServiceMessageHandler extends Handler {

        /**
         * The data previously captured by the service and send to this handler.
         */
        private List<CapturedData> capturedData = new ArrayList<>();

        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, String.format("Test received message %d.", msg.what));
            // super.handleMessage(msg);
            Bundle dataBundle = msg.getData();
            switch (msg.what) {
                case MessageCodes.DATA_CAPTURED:
                    dataBundle.setClassLoader(getClass().getClassLoader());
                    CapturedData data = dataBundle.getParcelable("data");

                    if (data != null) {
                        capturedData.add(data);
                    } else {
                        throw new IllegalStateException(
                                "Test received point captured message without associated data!");
                    }

                    break;
                case MessageCodes.LOCATION_CAPTURED:
                    dataBundle.setClassLoader(getClass().getClassLoader());
                    GeoLocation location = dataBundle.getParcelable("data");

                    Log.d(TAG, String.format("Test received location %f,%f", location.getLat(), location.getLon()));
                    break;
                case MessageCodes.GPS_FIX:
                    Log.d(TAG, String.format("Test received geo location fix."));
                    break;
                default:
                    throw new IllegalStateException(String.format("Test is unable to handle message %s!", msg.what));
            }
        }

        /**
         * @return The data previously captured by the service and send to this handler.
         */
        List<CapturedData> getCapturedData() {
            return capturedData;
        }
    }

    /**
     * A callback used to check whether the service has successfully started or not.
     *
     * @author Klemens Muthmann
     * @since 2.0.0
     * @version 1.0.0
     */
    private static class TestCallback implements IsRunningCallback {

        /**
         * Flag indicating a successful startup if <code>true</code>.
         */
        boolean isRunning = false;
        /**
         * Flag indicating an unsuccessful startup if <code>true</code>.
         */
        boolean timedOut = false;
        /**
         * <code>Lock</code> used to synchronize the callback with the test case using it.
         */
        Lock lock;
        /**
         * <code>Condition</code> used to signal the test case to continue processing.
         */
        Condition condition;

        @Override
        public void isRunning() {
            lock.lock();
            try {
                isRunning = true;
                condition.signal();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void timedOut() {
            lock.lock();
            try {
                timedOut = true;
                condition.signal();
            } finally {
                lock.unlock();
            }
        }
    }
}
