/*
 * Copyright 2021 Cyface GmbH
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
package de.cyface.datacapturing.backend

import android.os.Handler
import android.os.Message
import android.util.Log
import de.cyface.datacapturing.MessageCodes
import de.cyface.datacapturing.TestUtils.TAG
import de.cyface.persistence.model.ParcelableGeoLocation

/**
 * A handler for messages received from the capturing service.
 *
 * @author Klemens Muthmann
 * @version 1.0.5
 * @since 2.0.0
 */
internal class FromServiceMessageHandler : Handler() {
    /**
     * The data previously captured by the service and send to this handler.
     */
    private val capturedData: MutableList<CapturedData> = ArrayList()

    /**
     * A flag that is set to `true` if no permission to access fine location has been granted to the
     * background service.
     */
    private var accessWasNotGranted = false

    override fun handleMessage(msg: Message) {
        // super.handleMessage(msg);
        val dataBundle = msg.data
        when (msg.what) {
            MessageCodes.DATA_CAPTURED -> {
                dataBundle.classLoader = javaClass.classLoader
                val data = dataBundle.getParcelable<CapturedData>("data")
                if (data != null) {
                    capturedData.add(data)
                } else {
                    error("Test received point captured message without associated data!")
                }
                Log.d(TAG, "Test received sensor data.")
            }

            MessageCodes.LOCATION_CAPTURED -> {
                dataBundle.classLoader = javaClass.classLoader
                val location = dataBundle.getParcelable<ParcelableGeoLocation>("data")
                requireNotNull(location)
                Log.d(TAG, "Test received location ${location.lat},${location.lon}")
            }

            MessageCodes.GEOLOCATION_FIX -> Log.d(TAG, "Test received GeoLocation fix.")

            MessageCodes.ERROR_PERMISSION -> {
                Log.d(TAG, "Test was not granted permission for ACCESS_FINE_LOCATION!")
                accessWasNotGranted = true
                error("Test is unable to handle message ${msg.what}!",)
            }

            else -> error("Test is unable to handle message ${msg.what}!")
        }
    }

    /**
     * @return The data previously captured by the service and send to this handler.
     */
    fun getCapturedData(): List<CapturedData> {
        return capturedData
    }
}
