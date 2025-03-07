/*
 * Copyright 2017-2025 Cyface GmbH
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
package de.cyface.datacapturing.persistence

import android.util.Log
import de.cyface.datacapturing.Constants
import de.cyface.datacapturing.model.CapturedData
import de.cyface.persistence.serialization.Point3DFile

/**
 * A class responsible for writing captured sensor data to the underlying persistence layer.
 *
 * This is a throw away runnable. You should never run this twice. Doing so will cause duplicates inside the
 * persistence layer. Instead create a new instance per `CapturedData` to save.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 5.0.0
 * @since 1.0.0
 * @property data The data to write.
 * @property accelerationsFile The file to write the data to or `null` if `SensorCaptureDisabled`.
 * @property rotationsFile The file to write the data to or `null` if `SensorCaptureDisabled`.
 * @property directionsFile The file to write the data to or `null` if `SensorCaptureDisabled`.
 * @property callback Callback which is called after writing data has finished.
 */
class CapturedDataWriter internal constructor(
    private val data: CapturedData,
    private val accelerationsFile: Point3DFile?,
    private val rotationsFile: Point3DFile?,
    private val directionsFile: Point3DFile?,
    private val callback: WritingDataCompletedCallback
) : Runnable {
    private fun writeCapturedData() {
        Log.d(
            TAG, "appending " + data.accelerations.size + "/" + data.rotations.size + "/"
                    + data.directions.size + " A/R/MPs on: " + Thread.currentThread().name
        )
        if (data.accelerations.isNotEmpty()) {
            accelerationsFile!!.append(data.accelerations)
        }
        if (data.rotations.isNotEmpty()) {
            rotationsFile!!.append(data.rotations)
        }
        if (data.directions.isNotEmpty()) {
            directionsFile!!.append(data.directions)
        }
    }

    override fun run() {
        try {
            writeCapturedData()
        } finally {
            callback.writingDataCompleted()
        }
    }

    companion object {
        /**
         * The tag used to identify Logcat messages from this class.
         */
        private const val TAG = Constants.BACKGROUND_TAG
    }
}
