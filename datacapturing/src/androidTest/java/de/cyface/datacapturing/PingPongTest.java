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
import android.support.test.runner.AndroidJUnit4;

import de.cyface.datacapturing.backend.TestCallback;
import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.exception.MissingPermissionException;
import de.cyface.datacapturing.exception.SetupException;
import de.cyface.datacapturing.model.Vehicle;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class PingPongTest {

    private static final long TIMEOUT_TIME = 10L;

    @Rule
    public GrantPermissionRule mRuntimePermissionRule = GrantPermissionRule
            .grant(android.Manifest.permission.ACCESS_FINE_LOCATION);

    private PongReceiver oocut;
    private Lock lock;
    private Condition condition;
    private DataCapturingService dcs;

    @Before
    public void setUp() {
        lock = new ReentrantLock();
        condition = lock.newCondition();
        oocut = new PongReceiver(InstrumentationRegistry.getTargetContext());
    }

    @Test
    public void testWithRunningService() throws MissingPermissionException, DataCapturingException {
        final Context context = InstrumentationRegistry.getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                try {
                    dcs = new CyfaceDataCapturingService(context, context.getContentResolver(), ServiceTestUtils.AUTHORITY, ServiceTestUtils.ACCOUNT_TYPE, "https://fake.fake/");
                } catch (SetupException e) {
                    throw new IllegalStateException(e);
                }
            }
        });
        DataCapturingListener listener = new TestListener(lock, condition);
        StartUpFinishedHandler finishedHandler = new TestStartUpFinishedHandler(lock, condition);

        dcs.startAsync(listener, Vehicle.UNKOWN, finishedHandler);

        lock.lock();
        try {
            condition.await(TIMEOUT_TIME, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }

        TestCallback testCallback = new TestCallback("testWithRunningService", lock, condition);
        oocut.pongAndReceive(TIMEOUT_TIME, TimeUnit.SECONDS, testCallback);

        lock.lock();
        try {
            condition.await(2*TIMEOUT_TIME, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }

        assertThat(testCallback.wasRunning(), is(equalTo(true)));
        assertThat(testCallback.didTimeOut(), is(equalTo(false)));

        TestShutdownFinishedHandler shutdownHandler = new TestShutdownFinishedHandler(lock, condition);
        dcs.stopAsync(shutdownHandler);

        lock.lock();
        try {
            condition.await(TIMEOUT_TIME, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }
    }

    @Test
    public void testWithNonRunningService() {
        TestCallback testCallback = new TestCallback("testWithNonRunningService", lock, condition);

        oocut.pongAndReceive(TIMEOUT_TIME, TimeUnit.SECONDS, testCallback);

        lock.lock();
        try {
            condition.await(2*TIMEOUT_TIME, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }

        assertThat(testCallback.didTimeOut(), is(equalTo(true)));
        assertThat(testCallback.wasRunning(), is(equalTo(false)));
    }

}
