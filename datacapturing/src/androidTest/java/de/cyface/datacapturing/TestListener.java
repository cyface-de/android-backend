package de.cyface.datacapturing;

import static de.cyface.datacapturing.TestUtils.TAG;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.util.Log;

import androidx.annotation.NonNull;
import de.cyface.datacapturing.model.CapturedData;
import de.cyface.datacapturing.ui.Reason;
import de.cyface.persistence.model.GeoLocation;

/**
 * A listener for events from the capturing service, only used by tests.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.2.3
 * @since 2.0.0
 */
class TestListener implements DataCapturingListener {

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
     */
    TestListener() {
        capturedPositions = new ArrayList<>();
        capturedData = new ArrayList<>();
    }

    @Override
    public void onFixAcquired() {
        Log.d(TAG, "Fix acquired!");
    }

    @Override
    public void onFixLost() {
        Log.d(TAG, "GNSS fix lost!");
    }

    @Override
    public void onNewGeoLocationAcquired(final @NonNull GeoLocation position) {
        Log.d(TAG, String.format("New GNSS position (lat:%f,lon:%f)", position.getLat(), position.getLon()));
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
