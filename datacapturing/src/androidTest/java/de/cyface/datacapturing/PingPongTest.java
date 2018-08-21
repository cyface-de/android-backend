package de.cyface.datacapturing;

import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import de.cyface.datacapturing.backend.TestCallback;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class PingPongTest {

    private static final long TIMEOUT_TIME = 10L;

    @Test
    public void testWithRunningService() {
        fail();
    }

    @Test
    public void testWithNonRunningService() {
        PongReceiver pongReceiver = new PongReceiver(InstrumentationRegistry.getTargetContext());
        Lock lock = new ReentrantLock();
        Condition condition = lock.newCondition();
        TestCallback testCallback = new TestCallback("testWithNonRunningService", lock, condition);

        pongReceiver.pongAndReceive(TIMEOUT_TIME, TimeUnit.SECONDS, testCallback);

        assertThat(testCallback.didTimeOut(), is(equalTo(true)));
        assertThat(testCallback.wasRunning(), is(equalTo(false)));
    }

}
