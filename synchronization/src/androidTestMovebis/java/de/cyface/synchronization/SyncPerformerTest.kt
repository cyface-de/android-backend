/*
 * Copyright 2018-2023 Cyface GmbH
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

import android.content.ContentResolver
import android.content.Context
import android.content.SyncResult
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import de.cyface.model.MeasurementIdentifier
import de.cyface.model.RequestMetaData
import de.cyface.persistence.DefaultPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.exception.NoSuchMeasurementException
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.model.MeasurementStatus
import de.cyface.persistence.model.Modality
import de.cyface.persistence.serialization.MeasurementSerializer
import de.cyface.serializer.DataSerializable
import de.cyface.synchronization.exception.AccountNotActivated
import de.cyface.synchronization.exception.BadRequestException
import de.cyface.synchronization.exception.ConflictException
import de.cyface.synchronization.exception.EntityNotParsableException
import de.cyface.synchronization.exception.ForbiddenException
import de.cyface.synchronization.exception.HostUnresolvable
import de.cyface.synchronization.exception.InternalServerErrorException
import de.cyface.synchronization.exception.MeasurementTooLarge
import de.cyface.synchronization.exception.NetworkUnavailableException
import de.cyface.synchronization.exception.ServerUnavailableException
import de.cyface.synchronization.exception.SynchronisationException
import de.cyface.synchronization.exception.SynchronizationInterruptedException
import de.cyface.synchronization.exception.TooManyRequestsException
import de.cyface.synchronization.exception.UnauthorizedException
import de.cyface.synchronization.exception.UnexpectedResponseCode
import de.cyface.synchronization.exception.UploadSessionExpired
import de.cyface.testutils.SharedTestUtils.clearPersistenceLayer
import de.cyface.testutils.SharedTestUtils.insertSampleMeasurementWithData
import de.cyface.utils.CursorIsNullException
import de.cyface.utils.Validate
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.io.File
import java.io.IOException
import java.net.URL

/**
 * Tests the actual data transmission code. Since this test requires a running Movebis API server, and communicates with
 * that server, it is a flaky test and a large test.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.1.2
 * @see [Testing documentation](http://d.android.com/tools/testing)
 *
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class SyncPerformerTest {

    private lateinit var persistence: DefaultPersistenceLayer<*>
    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var oocut: SyncPerformer

    @Before
    fun setUp() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        contentResolver = context.contentResolver
        persistence = DefaultPersistenceLayer(context, DefaultPersistenceBehaviour())
        clearPersistenceLayer(context, persistence)

        oocut = SyncPerformer(context)
    }

    @After
    fun tearDown() {
        runBlocking { clearPersistenceLayer(context, persistence) }
    }

    /**
     * Tests that a [Measurement] is marked as sync when the server returns
     * `java.net.HttpURLConnection#HTTP_CONFLICT`.
     */
    @Test
    @Throws(
        CursorIsNullException::class,
        NoSuchMeasurementException::class,
        ServerUnavailableException::class,
        ForbiddenException::class,
        BadRequestException::class,
        ConflictException::class,
        UnauthorizedException::class,
        InternalServerErrorException::class,
        EntityNotParsableException::class,
        SynchronisationException::class,
        NetworkUnavailableException::class,
        SynchronizationInterruptedException::class,
        TooManyRequestsException::class,
        HostUnresolvable::class,
        MeasurementTooLarge::class,
        UploadSessionExpired::class,
        UnexpectedResponseCode::class,
        AccountNotActivated::class
    )
    fun testSendData_returnsSuccessWhenServerReturns409() = runBlocking {

        // Arrange
        // Insert data to be synced
        val locationCount = 1
        val (measurementIdentifier, _, _, _, distance) = insertSampleMeasurementWithData(
            context, MeasurementStatus.FINISHED,
            persistence, 1, locationCount
        )
        val loadedStatus: MeasurementStatus =
            persistence.loadMeasurementStatus(measurementIdentifier)
        assertThat(
            loadedStatus, CoreMatchers.`is`(
                equalTo(MeasurementStatus.FINISHED)
            )
        )
        contentResolver.acquireContentProviderClient(TestUtils.AUTHORITY).use { client ->
            checkNotNull(client) {
                String.format(
                    "Unable to acquire client for content provider %s",
                    TestUtils.AUTHORITY
                )
            }

            // Load measurement serialized compressed
            val serializer = MeasurementSerializer()
            val compressedTransferTempFile = serializer.writeSerializedCompressed(
                measurementIdentifier, persistence
            )
            Log.d(
                TestUtils.TAG, "CompressedTransferTempFile size: "
                        + DataSerializable.humanReadableSize(
                    compressedTransferTempFile!!.length(),
                    true
                )
            )

            // Prepare transmission
            val syncResult = SyncResult()

            // Load meta data
            val tracks = persistence.loadTracks(measurementIdentifier)
            val startLocation = tracks[0].geoLocations[0]!!
            val lastTrack = tracks[tracks.size - 1].geoLocations
            val endLocation = lastTrack[lastTrack.size - 1]!!
            val deviceId = "testDevi-ce00-42b6-a840-1b70d30094b8" // Must be a valid UUID
            val startRecord = RequestMetaData.GeoLocation(
                startLocation.timestamp,
                startLocation.lat,
                startLocation.lon
            )
            val endRecord = RequestMetaData.GeoLocation(
                endLocation.timestamp, endLocation.lat,
                endLocation.lon
            )
            val metaData = RequestMetaData(
                deviceId, measurementIdentifier.toString(),
                "testOsVersion", "testDeviceType", "testAppVersion",
                distance, locationCount.toLong(), startRecord, endRecord,
                Modality.BICYCLE.databaseIdentifier, 3
            )

            // Mock the actual post request
            val mockedHttp = mock<Http> {
                on { upload(any(), any(), any(), any(), any()) } doAnswer {
                    throw ConflictException(
                        "Test ConflictException"
                    )
                }
            }

            // Act
            try {
                // In the mock settings above we faked a ConflictException from the server
                val result = oocut.sendData(
                    mockedHttp,
                    syncResult,
                    TEST_API_URL,
                    metaData,
                    compressedTransferTempFile,
                    { percent: Float ->
                        Log.d(
                            TestUtils.TAG,
                            String.format("Upload Progress %f", percent)
                        )
                    },
                    TEST_TOKEN
                )

                // Assert:
                verify(mockedHttp, times(1)).upload(any(), any(), any(), any(), any())
                // because of the ConflictException true should be returned
                assertThat(
                    result,
                    CoreMatchers.`is`(equalTo(HttpConnection.Result.UPLOAD_SUCCESSFUL))
                )
                // Make sure the ConflictException is actually called (instead of no exception because of mock)
                assertThat(
                    syncResult.stats.numSkippedEntries, CoreMatchers.`is`(
                        equalTo(1L)
                    )
                )

                // Cleanup
            } finally {
                if (compressedTransferTempFile.exists()) {
                    assertThat(compressedTransferTempFile.delete(), equalTo(true))
                }
            }
        }
    }

    @Test
    @Ignore("Still uses an actual API")
    @Throws(
        IOException::class,
        CursorIsNullException::class,
        NoSuchMeasurementException::class,
        BadRequestException::class,
        EntityNotParsableException::class,
        ForbiddenException::class,
        ConflictException::class,
        NetworkUnavailableException::class,
        SynchronizationInterruptedException::class,
        InternalServerErrorException::class,
        SynchronisationException::class,
        UnauthorizedException::class,
        TooManyRequestsException::class,
        HostUnresolvable::class,
        ServerUnavailableException::class,
        MeasurementTooLarge::class,
        UploadSessionExpired::class,
        UnexpectedResponseCode::class,
        AccountNotActivated::class
    )
    fun testUpload_toActualApi() = runBlocking {

        // Arrange
        // 24 hours test data ~ 108 MB which is more then the currently supported upload size (100)
        val hours = 1
        val locationCount = hours * 3600
        val measurement: Measurement = insertSampleMeasurementWithData(
            context, MeasurementStatus.FINISHED,
            persistence, locationCount * 100, locationCount
        )
        val measurementIdentifier = measurement.id
        val loadedStatus: MeasurementStatus =
            persistence.loadMeasurementStatus(measurementIdentifier)
        assertThat(loadedStatus, equalTo(MeasurementStatus.FINISHED))
        var file: File? = null
        try {
            contentResolver.acquireContentProviderClient(TestUtils.AUTHORITY).use { client ->
                Validate.notNull(
                    client,
                    String.format(
                        "Unable to acquire client for content provider %s",
                        TestUtils.AUTHORITY
                    )
                )
                file = loadSerializedCompressed(measurementIdentifier)
                val metaData: RequestMetaData = loadMetaData(measurement, locationCount)
                val url = URL(String.format("%s%s/measurements", TEST_API_URL, "/api/v3"))

                // Act
                val result = HttpConnection().upload(
                    url, TEST_TOKEN, metaData, file
                ) { }

                // Assert
                assertThat(
                    result,
                    CoreMatchers.`is`(equalTo(HttpConnection.Result.UPLOAD_SUCCESSFUL))
                )
            }
        } finally {
            if (file != null && file!!.exists()) {
                Validate.isTrue(file!!.delete())
            }
        }
    }

    /**
     * Tests the basic transmission code to a actual Cyface API.
     *
     *
     * Can be used to reproduce bugs in the interaction between an actual API and our client.
     *
     *
     * **Attention:** for this you need to adjust [.TEST_API_URL] and [.TEST_TOKEN].
     */
    @Test
    @Ignore("Still uses an actual API")
    @Throws(
        CursorIsNullException::class, NoSuchMeasurementException::class
    )
    fun testSendData_toActualApi() = runBlocking {

        // Arrange
        // Adjust depending on your test case: (600k, 3k) ~ 27 MB compressed data ~ 5 min test execution
        // noinspection PointlessArithmeticExpression
        val point3DCount = 600 * 1000
        // noinspection PointlessArithmeticExpression
        val locationCount = 3 * 1000

        // Insert data to be synced
        val measurement = insertSampleMeasurementWithData(
            context, MeasurementStatus.FINISHED,
            persistence, point3DCount, locationCount
        )
        val measurementIdentifier = measurement.id
        val loadedStatus = persistence.loadMeasurementStatus(measurementIdentifier)
        assertThat(loadedStatus, equalTo(MeasurementStatus.FINISHED))
        contentResolver.acquireContentProviderClient(TestUtils.AUTHORITY).use { client ->
            Validate.notNull(
                client,
                String.format(
                    "Unable to acquire client for content provider %s",
                    TestUtils.AUTHORITY
                )
            )
            val compressedTransferTempFile = loadSerializedCompressed(measurementIdentifier)
            val metaData: RequestMetaData = loadMetaData(measurement, locationCount)

            // Prepare transmission
            val syncResult = SyncResult()

            // Act
            val result = oocut.sendData(
                HttpConnection(),
                syncResult,
                "$TEST_API_URL/api/v3/",
                metaData,
                compressedTransferTempFile,
                { percent: Float ->
                    Log.d(
                        TestUtils.TAG,
                        String.format("Upload Progress %f", percent)
                    )
                },
                TEST_TOKEN
            )

            // Assert
            assertThat(
                result,
                CoreMatchers.`is`(equalTo(HttpConnection.Result.UPLOAD_SUCCESSFUL))
            )
        }
    }

    private fun loadMetaData(
        measurement: Measurement,
        locationCount: Int
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
            Modality.BICYCLE.databaseIdentifier, 3
        )
    }

    @Throws(CursorIsNullException::class)
    private suspend fun loadSerializedCompressed(
        measurementIdentifier: Long
    ): File {

        // Load measurement serialized compressed
        val serializer = MeasurementSerializer()
        val compressedTransferTempFile = serializer.writeSerializedCompressed(
            measurementIdentifier,
            persistence
        )
        Log.d(
            TestUtils.TAG, "CompressedTransferTempFile size: "
                    + DataSerializable.humanReadableSize(
                compressedTransferTempFile!!.length(),
                true
            )
        )
        return compressedTransferTempFile
    }

    companion object {
        // ATTENTION: Depending on the API you test against, you might also need to replace the res/raw/truststore.jks
        private const val TEST_API_URL =
            "http://localhost:8080" // never use a non-numeric port here!
        private const val TEST_TOKEN = "ey*****"
    }
}