/*
 * Copyright 2019 Cyface GmbH
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
package de.cyface.synchronization

import android.util.Log
import de.cyface.model.MeasurementIdentifier
import de.cyface.model.RequestMetaData
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.model.Modality
import de.cyface.persistence.serialization.MeasurementSerializer
import de.cyface.serializer.DataSerializable
import de.cyface.utils.CursorIsNullException
import java.io.File
import kotlin.math.log

/**
 * Contains utility methods and constants required by the tests within the synchronization project.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.4.2
 * @since 2.1.0
 */
object TestUtils {
    /**
     * The tag used to identify Logcat messages from this module.
     */
    const val TAG = Constants.TAG + ".test"

    /**
     * The content provider authority used during tests. This must be the same as in the manifest and the authenticator
     * configuration.
     */
    const val AUTHORITY = "de.cyface.synchronization.test.provider"

    /**
     * The account type used during testing. This must be the same as in the authenticator configuration.
     */
    const val ACCOUNT_TYPE = "de.cyface.synchronization.test"

    /**
     * For manual testing this can be replaced with an username available at [.TEST_API_URL].
     *
     * Never use actual APIs credentials in automated tests.
     */
    const val DEFAULT_USERNAME = "test@cyface.de"

    /**
     * For manual testing this can be replaced with a password available at [.TEST_API_URL].
     *
     * Never use actual APIs credentials in automated tests.
     */
    const val DEFAULT_PASSWORD = "testPassword"

    /**
     * For manual testing this can be replaced with a path to an API available for testing.
     *
     * Never use actual APIs in automated tests.
     */
    @Suppress("unused") // used in the cyface flavour
    const val TEST_API_URL = "https://replace.with/url" // never use a non-numeric port here!

    fun loadMetaData(
        persistence: DefaultPersistenceLayer<*>,
        measurement: Measurement,
        @Suppress("SameParameterValue") locationCount: Int,
        logCount: Int,
        imageCount: Int,
        videoCount: Int,
        filesSize: Long
    ): RequestMetaData {
        // Load meta data
        val tracks = persistence.loadTracks(measurement.id)
        val startLocation = tracks[0].geoLocations[0]!!
        val lastTrack = tracks[tracks.size - 1].geoLocations
        val endLocation = lastTrack[lastTrack.size - 1]!!
        val deviceId = "testDevi-ce00-42b6-a840-1b70d30094b8" // Must be a valid UUID
        val id = MeasurementIdentifier(deviceId, measurement.id)
        val startRecord = RequestMetaData.GeoLocation(
            startLocation.timestamp, startLocation.lat,
            startLocation.lon
        )
        val endRecord = RequestMetaData.GeoLocation(
            endLocation.timestamp, endLocation.lat,
            endLocation.lon
        )
        return RequestMetaData(
            deviceId, id.measurementIdentifier.toString(),
            "testOsVersion", "testDeviceType", "testAppVersion",
            measurement.distance, locationCount.toLong(), startRecord, endRecord,
            Modality.BICYCLE.databaseIdentifier, 3, logCount, imageCount, videoCount, filesSize
        )
    }

    @Throws(CursorIsNullException::class)
    suspend fun loadSerializedCompressed(
        persistence: DefaultPersistenceLayer<*>,
        measurementIdentifier: Long
    ): File {

        // Load measurement serialized compressed
        val serializer = MeasurementSerializer()
        val compressedTransferTempFile = serializer.writeSerializedCompressed(
            measurementIdentifier,
            persistence
        )
        Log.d(
            TAG, "CompressedTransferTempFile size: "
                    + DataSerializable.humanReadableSize(
                compressedTransferTempFile!!.length(),
                true
            )
        )
        return compressedTransferTempFile
    }
}