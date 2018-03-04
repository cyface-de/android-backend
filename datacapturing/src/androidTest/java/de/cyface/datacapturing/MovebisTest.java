package de.cyface.datacapturing;

import android.Manifest;
import android.content.Context;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.FlakyTest;
import android.support.test.filters.LargeTest;
import android.support.test.rule.GrantPermissionRule;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import de.cyface.datacapturing.exception.SetupException;
import de.cyface.datacapturing.ui.Reason;
import de.cyface.datacapturing.ui.UIListener;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Tests whether the specific features required for the Movebis project work as expected.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4.class)
@FlakyTest
@LargeTest
public final class MovebisTest {

    /**
     * Grants the access fine location permission to this test.
     */
    @Rule
    public GrantPermissionRule grantFineLocationPermissionRule = GrantPermissionRule
            .grant(android.Manifest.permission.ACCESS_FINE_LOCATION);
    /**
     * Grants the access coarse location permission to this test.
     */
    @Rule
    public GrantPermissionRule grantCoarseLocationPermissionRule = GrantPermissionRule
            .grant(Manifest.permission.ACCESS_COARSE_LOCATION);

    /**
     * Tests if one lifecycle of starting and stopping location updates works as expected.
     *
     * @throws SetupException Should not happen. For further details look at the documentation of {@link MovebisDataCapturingService#MovebisDataCapturingService(Context, String, UIListener, long)}.
     */
    public void testUiLocationUpdateLifecycle() throws SetupException {
        Context context = InstrumentationRegistry.getTargetContext();
        Lock lock = new ReentrantLock();
        Condition condition = lock.newCondition();
        TestUIListener listener = new TestUIListener(lock, condition);
        MovebisDataCapturingService oocut = new MovebisDataCapturingService(context, "https://localhost:8080", listener, 0L);

        oocut.startUILocationUpdates();
        lock.lock();
        try {
            condition.await(10L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }
        oocut.stopUILocationUpdates();

        assertThat(listener.receivedUpdates.isEmpty(),is(equalTo(false)));
    }

    /**
     * A test listener receiving values to test agains.
     *
     * @author Klemens Muthmann
     * @version 1.0.0
     * @since 2.0.0
     */
    private static class TestUIListener implements UIListener {

        /**
         * Synchronization lock with the main test.
         */
        private final Lock lock;
        /**
         * Synchronization condition with the main test.
         */
        private final Condition condition;
        /**
         * A list of the received locations during one test run.
         */
        final List<Location> receivedUpdates;

        /**
         * Creates a new completely initialized <code>TestUIListener</code>.
         *
         * @param lock Synchronization lock with the main test.
         * @param condition Synchronization condition with the main test.
         */
        TestUIListener(final @NonNull Lock lock, final @NonNull Condition condition) {
            this.lock = lock;
            this.condition = condition;
            receivedUpdates = new ArrayList<>();
        }

        @Override
        public void onLocationUpdate(Location location) {
            receivedUpdates.add(location);

            lock.lock();
            try {
                condition.signal();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public boolean onRequirePermission(String permission, Reason reason) {
            return true;
        }
    }
}
