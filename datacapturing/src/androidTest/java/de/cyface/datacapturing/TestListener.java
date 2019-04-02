/*
 * Copyright 2017 Cyface GmbH
 * This file is part of the Cyface SDK for Android.
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
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
 * @version 1.2.1
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
