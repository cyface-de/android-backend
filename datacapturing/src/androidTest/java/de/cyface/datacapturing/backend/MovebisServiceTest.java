package de.cyface.datacapturing.backend;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.Manifest;
import android.content.Context;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.FlakyTest;
import android.support.test.filters.LargeTest;
import android.support.test.rule.GrantPermissionRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import de.cyface.datacapturing.MovebisDataCapturingService;
import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.ui.Reason;
import de.cyface.datacapturing.ui.UIListener;

/**
 * Tests the {@link MovebisDataCapturingService} for if it receives updates successfully. Since this requires access to
 * a running location sensor it is a large and flaky test.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
@FlakyTest
public final class MovebisServiceTest {

    /**
     * Rule that initialy grants all required permissions.
     */
    @Rule
    public GrantPermissionRule permissionRule = GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION);

    /**
     * Tests the correct initialisation of the <code>MovebisDataCapturingService</code>.
     */
    @Test
    public void testMovebisServiceInitialisation() throws InterruptedException {
        final Context context = InstrumentationRegistry.getTargetContext();

        Lock lock = new ReentrantLock();
        Condition condition = lock.newCondition();
        final TestUIListener testListener = new TestUIListener(lock, condition);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                try {
                    MovebisDataCapturingService oocut = new MovebisDataCapturingService(context, "garbage",
                            testListener, 1L);
                } catch (DataCapturingException e) {
                    throw new IllegalStateException(e);
                }
            }
        });

        lock.lock();
        try {
            condition.await(10L, TimeUnit.SECONDS);
        } finally {
            lock.unlock();
        }

        // Thread.sleep(10000L);

        assertThat(testListener.updatesReceived.size() > 0, is(equalTo(true)));
    }
}

/**
 * A test listener used to assert that data has been captured.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
class TestUIListener implements UIListener {

    /**
     * A list of all the location updates this listener has received.
     */
    List<Location> updatesReceived = new ArrayList<>();

    /**
     * Lock shared with the main thread to wait for this listener.
     */
    private final Lock lock;
    /**
     * The condition used to wake up the main thread if some location has been captured.
     */
    private final Condition condition;

    /**
     * Creates a new completely initialized <code>TestUIListener</code> that is capable of synchronizing with the main
     * thread.
     *
     * @param lock Lock shared with the main thread to wait for this listener.
     * @param condition The condition used to wake up the main thread if some location has been captured.
     */
    public TestUIListener(final @NonNull Lock lock, final @NonNull Condition condition) {
        this.lock = lock;
        this.condition = condition;
    }

    @Override
    public void onLocationUpdate(final @NonNull Location location) {
        Log.d("tag", "blah");
        updatesReceived.add(location);
        lock.lock();
        try {
            condition.signal();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean onRequirePermission(final @NonNull String permission, final @NonNull Reason reason) {
        return true;
    }
}
