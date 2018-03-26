package de.cyface.datacapturing;

import android.support.annotation.NonNull;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import de.cyface.datacapturing.model.CapturedData;
import de.cyface.datacapturing.model.GeoLocation;

/**
 * A listener for events from the capturing service, only used by tests.
 *
 * @author Klemens Muthmann
 * @version 1.1.0
 * @since 2.0.0
 */
class TestListener implements DataCapturingListener {
    private final static String TAG = "de.cyface.test";

    private final Lock lock;
    private final Condition condition;
    /**
     * Geo locations captured during the test run.
     */
    private final List<GeoLocation> capturedPositions;

    TestListener(final @NonNull Lock lock, final @NonNull Condition condition) {
        capturedPositions = new ArrayList<>();
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
    public void onNewSensorDataAcquired(CapturedData data) {
        Log.d(TAG, "New Sensor data.");
    }

    @Override
    public void onLowDiskSpace(final @NonNull DiskConsumption allocation) {

    }

    @Override
    public void onSynchronizationSuccessful() {

    }

    @Override
    public void onErrorState(Exception e) {

    }

    public List<GeoLocation> getCapturedPositions() {
        return Collections.unmodifiableList(capturedPositions);

    }
}
