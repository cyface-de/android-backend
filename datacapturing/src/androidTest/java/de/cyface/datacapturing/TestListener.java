package de.cyface.datacapturing;

import static de.cyface.datacapturing.ServiceTestUtils.TAG;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import androidx.annotation.NonNull;
import android.util.Log;

import de.cyface.datacapturing.model.CapturedData;
import de.cyface.datacapturing.model.GeoLocation;
import de.cyface.datacapturing.ui.Reason;

/**
 * A listener for events from the capturing service, only used by tests.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.2.2
 * @since 2.0.0
 */
class TestListener implements DataCapturingListener {

    /**
     * Lock used to secure the <code>Condition</code>, prior to sending a signal.
     */
    private final Lock lock;
    /**
     * <code>Condition</code> used to send a signal to the creating thread.
     */
    private final Condition condition;
    /**
     * Geo locations captured during the test run.
     */
    private final List<GeoLocation> capturedPositions;
    /**
     * Sensor data captured during the test run.
     */
    private final List<CapturedData> capturedData;

    /**
     * Creates a new completely initialized <code>TestListener</code> signaling the creating thread via the provided
     * lock and condition of message reception from the <code>DataCapturingService</code>.
     *
     * @param lock Lock used to secure the <code>Condition</code>, prior to sending a signal.
     * @param condition <code>Condition</code> used to send a signal to the creating thread.
     */
    TestListener(final @NonNull Lock lock, final @NonNull Condition condition) {
        capturedPositions = new ArrayList<>();
        capturedData = new ArrayList<>();
        this.lock = lock;
        this.condition = condition;
    }

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
    public void onNewSensorDataAcquired(final @NonNull CapturedData data) {
        Log.d(TAG, "New Sensor data.");
        capturedData.add(data);
    }

    @Override
    public void onLowDiskSpace(final @NonNull DiskConsumption allocation) {
        // Nothing to do here
    }

    @Override
    public void onSynchronizationSuccessful() {
        // Nothing to do here
    }

    @Override
    public void onErrorState(Exception e) {
        // Nothing to do here
    }

    @Override
    public boolean onRequiresPermission(String permission, Reason reason) {
        return false;
    }

    @Override
    public void onCapturingStopped() {
        // Nothing to do here
    }

    /**
     * @return The captured positions received during the test run.
     */
    public List<GeoLocation> getCapturedPositions() {
        return Collections.unmodifiableList(capturedPositions);

    }

    /**
     *
     * @return The captured sensor data received during the test run.
     */
    public List<CapturedData> getCapturedData() {
        return Collections.unmodifiableList(capturedData);
    }
}
