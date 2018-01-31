package de.cyface.datacapturing;

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

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import de.cyface.datacapturing.backend.DataCapturingBackgroundService;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Tests whether the service handling the data capturing works correctly.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4.class)
@FlakyTest
@LargeTest
public class BackgroundServiceTest {

    private static final String TAG = "de.cyface.test";

    @Rule
    public ServiceTestRule serviceTestRule = new ServiceTestRule();

    @Rule
    public GrantPermissionRule mRuntimePermissionRule = GrantPermissionRule
            .grant(android.Manifest.permission.ACCESS_FINE_LOCATION);

    private Messenger toServiceMessenger;
    private Messenger fromServiceMessenger;

    @Test
    public void testStartDataCapturing() throws InterruptedException {
        Context context = InstrumentationRegistry.getContext();

        createFromServiceMessengerSyncronously();
        Intent startIntent = new Intent(context, DataCapturingBackgroundService.class);
        ToServiceConnection toServiceConnection = new ToServiceConnection();

        context.startService(startIntent);
        context.bindService(startIntent, toServiceConnection, 0);

        Thread.sleep(2000L);

        context.unbindService(toServiceConnection);
        Intent stopIntent = new Intent(context, DataCapturingBackgroundService.class);
        assertThat(context.stopService(stopIntent), is(equalTo(true)));
    }

    /**
     * This method creates the <code>FromServiceMessenger</code> which is responsible to receive messages send by the
     * service. The method is necessary since creation of a new <code>Handler</code> is only possible on a properly
     * initialized Android thread.
     */
    private void createFromServiceMessengerSyncronously() {
        final Lock lock = new ReentrantLock();
        final Condition finishedCondition = lock.newCondition();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                lock.lock();
                try {
                    fromServiceMessenger = new Messenger(new FromServiceMessageHandler());
                } finally {
                    finishedCondition.signal();
                    lock.unlock();
                }
            }
        });

        lock.lock();
        try {
            finishedCondition.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
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
            toServiceMessenger = new Messenger(iBinder);

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
        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, String.format("Test received message %d.", msg.what));
            // super.handleMessage(msg);
            switch (msg.what) {
                case MessageCodes.POINT_CAPTURED:
                    Bundle dataBundle = msg.getData();
                    break;
                default:
                    throw new IllegalStateException(String.format("Test is unable to handle message %s!", msg.what));
            }
        }
    }
}
