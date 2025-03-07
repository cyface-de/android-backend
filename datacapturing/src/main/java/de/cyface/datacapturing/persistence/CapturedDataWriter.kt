/*
 * Copyright 2017-2023 Cyface GmbH
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
 *
 * This is a throw away runnable. You should never run this twice. Doing so will cause duplicates inside the
 * persistence layer. Instead create a new instance per `CapturedData` to save.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 4.0.2
 * @since 1.0.0
 */
class CapturedDataWriter
/**
 * Creates a new completely initialized writer for captured data.
 *
 * @param data The data to write.
 * @param accelerationsFile The file to write the data to.
 * @param rotationsFile The file to write the data to.
 * @param directionsFile The file to write the data to.
 * @param callback Callback which is called after writing data has finished.
 */ internal constructor(
    /**
     * The data to write.
     */
    private val data: CapturedData,
    /**
     * The [Point3DFile] to write acceleration points to.
     */
    private val accelerationsFile: Point3DFile,
    /**
     * The [Point3DFile] to write rotation points to.
     */
    private val rotationsFile: Point3DFile,
    /**
     * The [Point3DFile] to write direction points to.
     */
    private val directionsFile: Point3DFile,
    /**
     * Callback which is called after writing data has finished.
     */
    private val callback: WritingDataCompletedCallback
) : Runnable {
    private fun writeCapturedData() {
        Log.d(
            TAG, "appending " + data.accelerations.size + "/" + data.rotations.size + "/"
                    + data.directions.size + " A/R/MPs on: " + Thread.currentThread().name
        )
        accelerationsFile.append(data.accelerations)
        rotationsFile.append(data.rotations)
        directionsFile.append(data.directions)
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
