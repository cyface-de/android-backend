package de.cyface.datacapturing.backend;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
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
import android.support.test.filters.FlakyTest;
import android.support.test.filters.LargeTest;
import android.support.test.rule.GrantPermissionRule;
import android.support.test.rule.ServiceTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import de.cyface.datacapturing.IsRunningCallback;
import de.cyface.datacapturing.MessageCodes;
import de.cyface.datacapturing.PongReceiver;
import de.cyface.datacapturing.model.CapturedData;
import de.cyface.datacapturing.model.Vehicle;
import de.cyface.datacapturing.persistence.MeasurementPersistence;

// TODO It is possible to simplify this test and remove the synchronization lock.
/**
 * Tests whether the service handling the data capturing works correctly. Since the test relies on external sensors and
 * GPS signal availability it is a flaky test.
 *
 * @author Klemens Muthmann
 * @version 2.0.0
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4.class)
@FlakyTest
@LargeTest
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
     * <p>
     * CAREFUL! Since the test requires a working geo location and 3 axis of freedom sensor it will only work on an
     * actual device, but not on an emulator.
     * 
     * @throws InterruptedException If test execution is interrupted externally. This should never really happen, but we
     *             need to throw the exception anyways.
     */
    @Test
    public void testStartDataCapturing() throws InterruptedException {
        final Context context = InstrumentationRegistry.getTargetContext();

        FromServiceMessageHandler fromServiceMessageHandler = createFromServiceMessengerSyncronously();
        if (fromServiceMessenger == null) {
            throw new IllegalStateException("From service messenger was not properly initialized.");
        }

        Intent startIntent = new Intent(context, DataCapturingBackgroundService.class);
        ToServiceConnection toServiceConnection = new ToServiceConnection();

        context.startService(startIntent);
        context.bindService(startIntent, toServiceConnection, 0);

        final TestCallback testCallback = new TestCallback();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                PongReceiver isRunningChecker = new PongReceiver(context);
                isRunningChecker.pongAndReceive(1, TimeUnit.MINUTES, testCallback);
            }
        });

        context.unbindService(toServiceConnection);
        Intent stopIntent = new Intent(context, DataCapturingBackgroundService.class);
        assertThat(context.stopService(stopIntent), is(equalTo(true)));
        assertThat(testCallback.isRunning, is(equalTo(true)));
        assertThat(testCallback.timedOut, is(equalTo(false)));
    }

    /**
     * This test case checks that starting the service works and that the service actually returns some data.
     * <p>
     * CAREFUL! Since the test requires a working geo location and 3 axis of freedom sensor it will only work on an
     * actual device, but not on an emulator.
     *
     * @throws InterruptedException If test execution is interrupted externally. This should never really happen, but we
     *             need to throw the exception anyways.
     */
    @Test
    public void testStartDataCapturingTwice() throws InterruptedException {
        final Context context = InstrumentationRegistry.getTargetContext();

        Intent startIntent = new Intent(context, DataCapturingBackgroundService.class);
        assertThat(context.startService(startIntent), is(notNullValue()));
        assertThat(context.startService(startIntent), is(notNullValue()));

        final TestCallback testCallback = new TestCallback();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                PongReceiver isRunningChecker = new PongReceiver(context);
                isRunningChecker.pongAndReceive(2, TimeUnit.SECONDS, testCallback);
            }
        });

        Intent stopIntent = new Intent(context, DataCapturingBackgroundService.class);
        assertThat(context.stopService(stopIntent), is(true));
        assertThat(testCallback.isRunning, is(equalTo(true)));
        assertThat(testCallback.timedOut, is(equalTo(false)));
    }

    /**
     * This method creates the <code>FromServiceMessenger</code> which is responsible to receive messages send by the
     * service. The method is necessary since creation of a new <code>Handler</code> is only possible on a properly
     * initialized Android thread.
     *
     * @return The created <code>FromServiceMessageHandler</code>, which is wrapped by the messenger.
     */
    private FromServiceMessageHandler createFromServiceMessengerSyncronously() {
        final Lock lock = new ReentrantLock();
        final Condition finishedCondition = lock.newCondition();
        FromServiceMessageHandlerFactory factory = new FromServiceMessageHandlerFactory(lock, finishedCondition);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(factory);

        lock.lock();
        try {
            finishedCondition.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }
        return factory.getFromServiceMessageHandler();
    }

    /**
     * A Runnable defering the creation of the <code>FromServiceMessageHandler</code> to another thread, which should be
     * a properly initialized Android thread (one where looper.prepare()) has been called.
     *
     * @author Klemens Muthmann
     * @version 1.0.0
     * @since 1.0.0
     */
    private class FromServiceMessageHandlerFactory implements Runnable {

        /**
         * The lock used to synchronize the call to this <code>Runnable</code> with the calling thread.
         */
        private final Lock lock;
        /**
         * The condition used to signal the calling thread that this factory has finished creation of the
         * <code>FromServiceMessageHandler</code>.
         */
        private final Condition condition;
        /**
         * The product of this factory or <code>null</code> if not called or creation was not successful.
         */
        private FromServiceMessageHandler fromServiceMessageHandler;

        /**
         * Creates a new <code>FromServiceMessageHandlerFactory</code> with the capabilty to synchronize itself with the
         * calling thread.
         *
         * @param lock The lock used to synchronize the call to this <code>Runnable</code> with the calling thread.
         * @param condition The condition used to signal the calling thread that this factory has finished creation of
         *            the <code>FromServiceMessageHandler</code>.
         */
        FromServiceMessageHandlerFactory(final Lock lock, final Condition condition) {
            this.lock = lock;
            this.condition = condition;
        }

        @Override
        public void run() {
            lock.lock();
            try {
                fromServiceMessageHandler = new FromServiceMessageHandler();
                fromServiceMessenger = new Messenger(fromServiceMessageHandler);
            } finally {
                condition.signal();
                lock.unlock();
            }
        }

        /**
         * @return The product of this factory or <code>null</code> if not called or creation was not successful.
         */
        FromServiceMessageHandler getFromServiceMessageHandler() {
            return fromServiceMessageHandler;
        }
    }

    /**
     * Connection from the test to the capturing service.
     *
     * @author Klemens Muthmann
     * @version 1.0.0
     * @since 1.0.0
     */
    private class ToServiceConnection implements ServiceConnection {

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
     * @since 1.0.0
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
            switch (msg.what) {
                case MessageCodes.DATA_CAPTURED:
                    Bundle dataBundle = msg.getData();
                    dataBundle.setClassLoader(getClass().getClassLoader());
                    CapturedData data = dataBundle.getParcelable("data");

                    if (data != null) {
                        capturedData.add(data);
                    } else {
                        throw new IllegalStateException(
                                "Test received point captured message without associated data!");
                    }

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

    private static class TestCallback implements IsRunningCallback {

        boolean isRunning = false;
        boolean timedOut = false;

        @Override
        public void isRunning() {
            isRunning = true;
        }

        @Override
        public void timedOut() {
            timedOut = true;
        }
    }
}
