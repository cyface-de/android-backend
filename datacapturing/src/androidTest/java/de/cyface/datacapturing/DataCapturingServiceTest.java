package de.cyface.datacapturing;

import android.content.Context;
import android.os.RemoteException;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.FlakyTest;
import android.support.test.filters.LargeTest;
import android.support.test.rule.GrantPermissionRule;
import android.support.test.rule.ServiceTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import de.cyface.datacapturing.model.Vehicle;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * Tests whether the {@link DataCapturingService} works correctly. This is a flaky test since it starts a service that
 * relies on external sensors and the availability of a GPS signal.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4.class)
@FlakyTest
@LargeTest
public class DataCapturingServiceTest {

    private static final String TAG = "de.cyface.test";

    @Rule
    public ServiceTestRule serviceTestRule = new ServiceTestRule();

    @Rule
    public GrantPermissionRule grantPermissionRule = GrantPermissionRule
            .grant(android.Manifest.permission.ACCESS_FINE_LOCATION);

    private Context context;
    private DataCapturingService oocut;
    private TestListener testListener;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getContext();
        oocut = new DataCapturingService(context,"http://localhost:8080");
        testListener = new TestListener();
    }

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

    @Test
    public void testDisconnectConnect() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                oocut.start(testListener,Vehicle.UNKOWN);
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

    @Test
    public void testDoubleStart() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                oocut.start(testListener,Vehicle.UNKOWN);
            }
        });

        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                oocut.start(testListener,Vehicle.UNKOWN);
            }
        });

        oocut.stop();
    }

    @Test
    public void testDoubleStop() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                oocut.start(testListener,Vehicle.UNKOWN);
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

    @Test
    public void testDoubleDisconnect() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                oocut.start(testListener,Vehicle.UNKOWN);
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

    @Test
    public void testStopNonConnectedService() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                oocut.start(testListener,Vehicle.UNKOWN);
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

    @Test
    public void testDoubleConnect() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                oocut.start(testListener,Vehicle.UNKOWN);
            }
        });

        oocut.disconnect();

        oocut.reconnect();
        oocut.reconnect();
        oocut.stop();
    }

    @Test
    public void testDisconnectConnectTwice() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                oocut.start(testListener,Vehicle.UNKOWN);
            }
        });

        oocut.disconnect();
        oocut.reconnect();
        oocut.disconnect();
        oocut.reconnect();
        oocut.stop();
    }

    @Test
    public void testRestart() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                oocut.start(testListener,Vehicle.UNKOWN);
            }
        });
        oocut.stop();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                oocut.start(testListener,Vehicle.UNKOWN);
            }
        });
        oocut.stop();
    }

    private static class TestListener implements DataCapturingListener {
        final List<GpsPosition> capturedPositions = new ArrayList<>();

        @Override
        public void onGpsFixAcquired() {
            Log.d(TAG, "GPS fix acquired!");
        }

        @Override
        public void onGpsFixLost() {
            Log.d(TAG, "GPS fix lost!");
        }

        @Override
        public void onNewGpsPositionAcquired(GpsPosition position) {
            Log.d(TAG, String.format("New GPS position (lat:%f,lon:%f)", position.getLat(), position.getLon()));
            capturedPositions.add(position);
        }

        @Override
        public void onLowDiskSpace(DiskConsumption allocation) {

        }

        @Override
        public boolean onRequirePermission(String permission, Reason reason) {
            return false;
        }

        @Override
        public void onSynchronizationSuccessful() {

        }
    }
}
