package de.cyface.datacapturing;

import static de.cyface.datacapturing.TestUtils.ACCOUNT_TYPE;
import static de.cyface.datacapturing.TestUtils.AUTHORITY;
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

import android.Manifest;
import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import de.cyface.datacapturing.exception.SetupException;
import de.cyface.utils.CursorIsNullException;

/**
 * Tests whether the specific features required for the Movebis project work as expected.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.2.1
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4.class)
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
     * A <code>MovebisDataCapturingService</code> as object of class under test, used for testing.
     */
    private MovebisDataCapturingService oocut;
    /**
     * A lock used to wait for asynchronous calls to the service, before continuing with the test execution.
     */
    private Lock lock;
    /**
     * A <code>Condition</code> used to wait for a signal from asynchronously called callbacks and listeners before
     * continuing with the test execution.
     */
    private Condition condition;
    /**
     * A listener catching messages send to the UI in real applications.
     */
    private TestUIListener testUIListener;
    /**
     * The context of the test installation.
     */
    private Context context;

    /**
     * Initializes the object of class under test.
     */
    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        lock = new ReentrantLock();
        condition = lock.newCondition();
        testUIListener = new TestUIListener(lock, condition);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                try {
                    oocut = new MovebisDataCapturingService(context, AUTHORITY, ACCOUNT_TYPE, "https://localhost:8080",
                            testUIListener, 0L, new IgnoreEventsStrategy());
                } catch (SetupException | CursorIsNullException e) {
                    throw new IllegalStateException(e);
                }
            }
        });

    }

    /**
     * Tests if one lifecycle of starting and stopping location updates works as expected.
     * FlakyTest: This integration test may be dependent on position / location updates on real devices.
     */
    @Test
    @SdkSuppress(minSdkVersion = 28) // Only succeeded on (Pixel 2) API 28 emulators (only on the CI)
    public void testUiLocationUpdateLifecycle() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                oocut.startUILocationUpdates();
            }
        });

        TestUtils.lockAndWait(10L, TimeUnit.SECONDS, lock, condition);
        oocut.stopUILocationUpdates();

        assertThat(testUIListener.receivedUpdates.isEmpty(), is(equalTo(false)));
    }
}
