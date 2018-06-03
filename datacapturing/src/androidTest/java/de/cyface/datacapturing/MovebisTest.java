package de.cyface.datacapturing;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.List;
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
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.FlakyTest;
import android.support.test.filters.LargeTest;
import android.support.test.rule.GrantPermissionRule;
import android.support.test.runner.AndroidJUnit4;

import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.exception.MissingPermissionException;
import de.cyface.datacapturing.exception.SetupException;
import de.cyface.datacapturing.model.Vehicle;
import de.cyface.datacapturing.ui.UIListener;

/**
 * Tests whether the specific features required for the Movebis project work as expected.
 *
 * @author Klemens Muthmann
 * @version 1.1.1
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
     * A <code>MovebisDataCapturingService</code> as object of class under test, used for testing.
     */
    private MovebisDataCapturingService oocut;
    /**
     * A lock used to wait for asynchronous calls to the service, before continuing with the test execution.
     */
    private Lock lock;
    /**
     * A <code>Condition</code> used to wait for a singal from asynchronously called callbacks and listeners before
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
     * A listener catching events from the <code>DataCapturingService</code> during tracking.
     */
    private TestListener testDataCapturingListener;
    /**
     * A listener waiting for the service to either tell, that it is running or for a timeout to happen.
     */
    private IsRunningStatus isRunningListener;

    /**
     * Initializes the object of class under test.
     *
     * @throws SetupException If the <code>MovebisDataCapturingService</code> was not created properly.
     */
    @Before
    public void setUp() throws SetupException {
        context = InstrumentationRegistry.getTargetContext();
        lock = new ReentrantLock();
        condition = lock.newCondition();
        testUIListener = new TestUIListener(lock, condition);
        testDataCapturingListener = new TestListener(lock, condition);
        isRunningListener = new IsRunningStatus(lock, condition);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                try {
                    oocut = new MovebisDataCapturingService(context, "https://localhost:8080", testUIListener, 0L);
                } catch (SetupException e) {
                    throw new IllegalStateException(e);
                }
            }
        });

    }

    /**
     * Tests if one lifecycle of starting and stopping location updates works as expected.
     *
     * @throws SetupException Should not happen. For further details look at the documentation of
     *             {@link MovebisDataCapturingService#MovebisDataCapturingService(Context, String, UIListener, long)}.
     */
    @Test
    public void testUiLocationUpdateLifecycle() throws SetupException {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                oocut.startUILocationUpdates();
            }
        });

        ServiceTestUtils.lockAndWait(10L, TimeUnit.SECONDS, lock, condition);
        oocut.stopUILocationUpdates();

        assertThat(testUIListener.receivedUpdates.isEmpty(), is(equalTo(false)));
    }

    /**
     * Tests whether the {@link MovebisDataCapturingService#pauseSync()} and {@link MovebisDataCapturingService#resumeSync()}
     * work correctly.
     *
     * @throws DataCapturingException If any unexpected errors occur during data capturing.
     * @throws MissingPermissionException If an Android permission is missing.
     */
    @Test
    public void testPauseResumeMeasurement() throws DataCapturingException, MissingPermissionException {
        // start
        oocut.startSync(testDataCapturingListener, Vehicle.UNKOWN);
        // check is running
        ServiceTestUtils.callCheckForRunning(oocut, isRunningListener);
        ServiceTestUtils.lockAndWait(2L, TimeUnit.SECONDS, lock, condition);
        assertThat(isRunningListener.wasRunning(), is(equalTo(true)));
        assertThat(isRunningListener.didTimeOut(), is(equalTo(false)));
        // get measurements
        List<Measurement> measurements = oocut.getCachedMeasurements();
        assertThat(measurements.size() > 0, is(equalTo(true)));
        // pause
        oocut.pauseSync();
        // check is not running
        ServiceTestUtils.callCheckForRunning(oocut, isRunningListener);
        ServiceTestUtils.lockAndWait(2L, TimeUnit.SECONDS, lock, condition);
        assertThat(isRunningListener.wasRunning(), is(equalTo(false)));
        assertThat(isRunningListener.didTimeOut(), is(equalTo(true)));
        // resume
        oocut.resumeSync();
        // check is running
        ServiceTestUtils.callCheckForRunning(oocut, isRunningListener);
        ServiceTestUtils.lockAndWait(2L, TimeUnit.SECONDS, lock, condition);
        assertThat(isRunningListener.wasRunning(), is(equalTo(true)));
        assertThat(isRunningListener.didTimeOut(), is(equalTo(false)));
        // get measurements again
        List<Measurement> newMeasurements = oocut.getCachedMeasurements();
        // check for no new measurements
        assertThat(measurements.size() == newMeasurements.size(), is(equalTo(true)));
        // stop
        oocut.stopSync();
    }

}
