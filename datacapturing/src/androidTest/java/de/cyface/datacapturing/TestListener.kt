/*
 * Copyright 2017 Cyface GmbH
 *
 * This file is part of the Cyface SDK for Android.
 *
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.datacapturing

import android.util.Log
import de.cyface.datacapturing.ui.Reason
import de.cyface.persistence.model.ParcelableGeoLocation
import de.cyface.utils.DiskConsumption
import java.util.Collections

/**
 * A listener for events from the capturing service, only used by tests.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.2.3
 * @since 2.0.0
 */
internal class TestListener : DataCapturingListener {
    /**
     * Geo locations captured during the test run.
     */
    private val capturedPositions: MutableList<ParcelableGeoLocation> = mutableListOf()

    /**
     * Sensor data captured during the test run.
     */
    private val capturedData: MutableList<CapturedData> = mutableListOf()

    override fun onFixAcquired() {
        Log.d(TestUtils.TAG, "Fix acquired!")
    }

    override fun onFixLost() {
        Log.d(TestUtils.TAG, "GNSS fix lost!")
    }

    override fun onNewGeoLocationAcquired(position: ParcelableGeoLocation) {
        Log.d(
            TestUtils.TAG,
            "New GNSS position (lat:${position.lat},lon:${position.lon})"
        )
        capturedPositions.add(position)
    }

    override fun onNewSensorDataAcquired(data: CapturedData) {
        Log.d(TestUtils.TAG, "New Sensor data.")
        capturedData.add(data)
    }

    override fun onLowDiskSpace(allocation: DiskConsumption) {
        // Nothing to do here
    }

    override fun onSynchronizationSuccessful() {
        // Nothing to do here
    }

    override fun onErrorState(e: Exception) {
        // Nothing to do here
    }

    override fun onRequiresPermission(permission: String, reason: Reason): Boolean {
        return false
    }

    override fun onCapturingStopped() {
        // Nothing to do here
    }

    /**
     * @return The captured positions received during the test run.
     */
    fun getCapturedPositions(): List<ParcelableGeoLocation> {
        return Collections.unmodifiableList(capturedPositions)
    }

    /**
     *
     * @return The captured sensor data received during the test run.
     */
    fun getCapturedData(): List<CapturedData> {
        return Collections.unmodifiableList(capturedData)
    }
}
