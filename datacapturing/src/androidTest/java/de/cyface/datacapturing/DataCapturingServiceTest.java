package de.cyface.datacapturing;

import android.content.Context;
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
    public GrantPermissionRule grantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.ACCESS_FINE_LOCATION);

    @Test
    public void testRunDataCapturingServiceSuccessfully() {
        Context context = InstrumentationRegistry.getContext();
        final DataCapturingService service = new DataCapturingService(context);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                service.start(new TestListener());

                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                service.stop();
            }
        });



    }

    private static class TestListener implements DataCapturingListener {
        @Override
        public void onGpsFixAcquired() {
            Log.d(TAG,"GPS fix acquired!");
        }

        @Override
        public void onGpsFixLost() {
            Log.d(TAG,"GPS fix lost!");
        }

        @Override
        public void onNewGpsPositionAcquired(GpsPosition position) {
            Log.d(TAG,String.format("New GPS position (lat:%f,lon:%f)",position.getLat(),position.getLon()));
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
