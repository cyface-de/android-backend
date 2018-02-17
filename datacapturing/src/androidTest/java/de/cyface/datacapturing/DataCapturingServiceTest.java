package de.cyface.datacapturing;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.FlakyTest;
import android.support.test.filters.LargeTest;
import android.support.test.rule.GrantPermissionRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import de.cyface.datacapturing.model.Vehicle;

/**
 * Tests whether the {@link DataCapturingService} works correctly. This is a flaky test since it starts a service that
 * relies on external sensors and the availability of a GPS signal. Each tests waits a few seconds to actually capture
 * some data, but it might still fail if you are indoors (which you will usually be while running tests, right?)
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4.class)
@FlakyTest
@LargeTest
public class DataCapturingServiceTest {

    /**
     * The tag used to identify log messages.
     */
    private static final String TAG = "de.cyface.test";

    // /**
    // * Rule used to run
    // */
    // @Rule
    // public ServiceTestRule serviceTestRule = new ServiceTestRule();

    /**
     * Grants the access location permission to this test.
     */
    @Rule
    public GrantPermissionRule grantPermissionRule = GrantPermissionRule
            .grant(android.Manifest.permission.ACCESS_FINE_LOCATION);

    /**
     * The object of class under test.
     */
    private DataCapturingService oocut;
    /**
     * Listener for messages from the service. This is used to assert correct service startup and shutdown.
     */
    private TestListener testListener;

    @Before
    public void setUp() {
        // TODO Maybe use getTargetContext here? What is the difference anyways?
        Context context = InstrumentationRegistry.getContext();
        oocut = new DataCapturingService(context, "http://localhost:8080");
        testListener = new TestListener();
    }

    /**
     * Tests a common service run. Checks that some positons have been captured.
     */
    @Test
    public void testRunDataCapturingServiceSuccessfully() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                oocut.start(testListener, Vehicle.UNKOWN);
            }
        });

        try {
            Thread.sleep(10000L);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }

        oocut.stop();
        assertThat(testListener.capturedPositions.isEmpty(), is(equalTo(false)));
    }

    /**
     * Tests a common service run with an intermidiate disconnect and reconnect by the application. No problems shour
     * occur and some points should be captured.
     */
    @Test
    public void testDisconnectConnect() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                oocut.start(testListener, Vehicle.UNKOWN);
            }
        });

        oocut.disconnect();
        oocut.reconnect();

        try {
            Thread.sleep(20000L);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }

        oocut.stop();
        assertThat(testListener.capturedPositions.isEmpty(), is(equalTo(false)));
    }

    /**
     * Tests that running start twice does not break the system. This test succeeds if no <code>Exception</code> occurs.
     */
    @Test
    public void testDoubleStart() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                oocut.start(testListener, Vehicle.UNKOWN);
            }
        });

        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                oocut.start(testListener, Vehicle.UNKOWN);
            }
        });

        oocut.stop();
    }

    /**
     * Tests for the correct <code>Exception</code> when you try to stop a stopped service.
     */
    @Test
    public void testDoubleStop() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                oocut.start(testListener, Vehicle.UNKOWN);
            }
        });

        oocut.stop();

        try {
            oocut.stop();
        } catch (IllegalStateException e) {
            return;
        }

        // No Exception? FAIL!
        fail();
    }

    /**
     * Tests for the correct <code>Exception</code> if you try to disconnect from a diconnected service.
     */
    @Test
    public void testDoubleDisconnect() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                oocut.start(testListener, Vehicle.UNKOWN);
            }
        });

        oocut.disconnect();
        try {
            oocut.disconnect();
        } catch (IllegalStateException e) {
            return;
        } finally {
            try {
                oocut.stop();
            } catch (IllegalStateException e) {
                // That is the same exception as already catched when calling oocut.disconnect.
                // Yeah we know, the service is not bound but we choose to silently ignore that fact.
            }
        }
        // No Exception? FAIL!
        fail();
    }

    // TODO Stopping a disconnected service is actually necessary.
    // Do we really want to throw an Exception here?
    /**
     * Tests for the correct <code>Exception</code> if you try to stop a disconnected service.
     */
    @Test
    public void testStopNonConnectedService() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                oocut.start(testListener, Vehicle.UNKOWN);
            }
        });

        oocut.disconnect();

        try {
            oocut.stop();
        } catch (IllegalStateException e) {
            return;
        }

        // No Exception? FAIL!
        fail();
    }

    /**
     * Tests that no <code>Exception</code> is thrown when we try to connect to the same service twice.
     */
    @Test
    public void testDoubleConnect() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                oocut.start(testListener, Vehicle.UNKOWN);
            }
        });

        oocut.disconnect();

        oocut.reconnect();
        oocut.reconnect();
        oocut.stop();
    }

    /**
     * Tests that two correct cycles of disconnect and reconnect on a running service work fine.
     */
    @Test
    public void testDisconnectConnectTwice() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                oocut.start(testListener, Vehicle.UNKOWN);
            }
        });

        oocut.disconnect();
        oocut.reconnect();
        oocut.disconnect();
        oocut.reconnect();
        oocut.stop();
    }

    /**
     * Tests that starting a service twice throws no <code>Exception</code>.
     */
    @Test
    public void testRestart() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                oocut.start(testListener, Vehicle.UNKOWN);
            }
        });
        oocut.stop();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                oocut.start(testListener, Vehicle.UNKOWN);
            }
        });
        oocut.stop();
    }

    /**
     * A listener for events from the capturing service, only used by tests.
     */
    private static class TestListener implements DataCapturingListener {
        /**
         * Geo locations captured during the test run.
         */
        final List<GeoLocation> capturedPositions = new ArrayList<>();

        @Override
        public void onFixAcquired() {
            Log.d(TAG, "Fix acquired!");
        }

        @Override
        public void onFixLost() {
            Log.d(TAG, "GPS fix lost!");
        }

        @Override
        public void onNewGeoLocationAcquired(final @NonNull GeoLocation position) {
            Log.d(TAG, String.format("New GPS position (lat:%f,lon:%f)", position.getLat(), position.getLon()));
            capturedPositions.add(position);
        }

        @Override
        public void onLowDiskSpace(final @NonNull DiskConsumption allocation) {

        }

        @Override
        public boolean onRequirePermission(final @NonNull String permission, final @NonNull Reason reason) {
            return false;
        }

        @Override
        public void onSynchronizationSuccessful() {

        }
    }
}
