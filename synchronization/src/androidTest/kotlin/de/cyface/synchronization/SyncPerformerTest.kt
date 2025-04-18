/*
 * Copyright 2018-2024 Cyface GmbH
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

import android.content.Context
import android.content.SyncResult
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import de.cyface.persistence.DefaultPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.exception.NoSuchMeasurementException
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.model.MeasurementStatus
import de.cyface.persistence.model.Modality
import de.cyface.persistence.serialization.MeasurementSerializer
import de.cyface.serializer.DataSerializable
import de.cyface.synchronization.TestUtils.loadMetaData
import de.cyface.synchronization.TestUtils.loadSerializedCompressed
import de.cyface.testutils.SharedTestUtils
import de.cyface.uploader.Result
import de.cyface.uploader.UploadProgressListener
import de.cyface.uploader.Uploader
import de.cyface.uploader.exception.AccountNotActivated
import de.cyface.uploader.exception.BadRequestException
import de.cyface.uploader.exception.ConflictException
import de.cyface.uploader.exception.EntityNotParsableException
import de.cyface.uploader.exception.ForbiddenException
import de.cyface.uploader.exception.HostUnresolvable
import de.cyface.uploader.exception.InternalServerErrorException
import de.cyface.uploader.exception.MeasurementTooLarge
import de.cyface.uploader.exception.NetworkUnavailableException
import de.cyface.uploader.exception.ServerUnavailableException
import de.cyface.uploader.exception.SynchronisationException
import de.cyface.uploader.exception.SynchronizationInterruptedException
import de.cyface.uploader.exception.TooManyRequestsException
import de.cyface.uploader.exception.UnauthorizedException
import de.cyface.uploader.exception.UnexpectedResponseCode
import de.cyface.uploader.exception.UploadFailed
import de.cyface.uploader.exception.UploadSessionExpired
import de.cyface.uploader.model.Attachment
import de.cyface.uploader.model.MeasurementIdentifier
import de.cyface.uploader.model.Uploadable
import de.cyface.uploader.model.metadata.ApplicationMetaData
import de.cyface.uploader.model.metadata.AttachmentMetaData
import de.cyface.uploader.model.metadata.DeviceMetaData
import de.cyface.uploader.model.metadata.GeoLocation
import de.cyface.uploader.model.metadata.MeasurementMetaData
import de.cyface.utils.CursorIsNullException
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * Tests the data transmission code.
 *
 * This test does not call an actual API but uses [MockedUploader]
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class SyncPerformerTest {

    private lateinit var persistence: DefaultPersistenceLayer<*>
    private lateinit var context: Context
    private lateinit var oocut: SyncPerformer
    private val tempFiles = mutableListOf<Path>()

    @Before
    fun setUp() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        persistence = DefaultPersistenceLayer(context, DefaultPersistenceBehaviour())
        SharedTestUtils.clearPersistenceLayer(context, persistence)

        oocut = SyncPerformer(context, true)
    }

    @After
    fun tearDown() {
        runBlocking { SharedTestUtils.clearPersistenceLayer(context, persistence) }
        // Cleanup temp files
        tempFiles.forEach { file ->
            try {
                Files.deleteIfExists(file)
            } catch (e: Exception) {
                println("Could not delete file: $file")
                e.printStackTrace()
            }
        }
        tempFiles.clear()
    }

    /**
     * Assembles a test for the basic transmission code without contacting an actual API.
     *
     * Can be used to reproduce bugs in the interaction between an actual API and our client.
     *
     * **Attention:** for this you need to adjust [.TEST_API_URL] and [.TEST_TOKEN].
     */
    private fun performSendDataTest(
        point3DCount: Int,
        @Suppress("SameParameterValue") locationCount: Int,
        logCount: Int,
        imageCount: Int,
        @Suppress("SameParameterValue") videoCount: Int,
        filesSize: Long
    ) = runBlocking {

        // Arrange
        val sampleFiles = SharedTestUtils.randomFiles(logCount + imageCount + videoCount)
        tempFiles.addAll(sampleFiles) // Add to the auto-cleanup list for `tearDown`
        val measurement = SharedTestUtils.insertSampleMeasurementWithData(
            context, MeasurementStatus.FINISHED,
            persistence, point3DCount, locationCount, logCount, imageCount, videoCount, sampleFiles
        )
        val measurementId = measurement.id
        val loadedStatus = persistence.loadMeasurementStatus(measurementId)
        MatcherAssert.assertThat(loadedStatus, CoreMatchers.equalTo(MeasurementStatus.FINISHED))
        val compressedTransferTempFile = loadSerializedCompressed(persistence, measurementId)
        val metaData =
            loadMetaData(
                persistence,
                measurement,
                locationCount,
                logCount,
                imageCount,
                videoCount,
                filesSize
            )
        val progressListener = object : UploadProgressListener {
            override fun updatedProgress(percent: Float) {
                Log.d(TAG, String.format("Upload Progress %f", percent))
            }
        }
        val fileName =
            "${metaData.identifier.deviceIdentifier}_${metaData.identifier.measurementIdentifier}.${SyncAdapter.COMPRESSED_TRANSFER_FILE_EXTENSION}"
        val uploader = MockedUploader()

        // Prepare transmission
        val syncResult = SyncResult()

        // Act
        val result = oocut.sendData(
            uploader,
            syncResult,
            metaData,
            compressedTransferTempFile,
            progressListener,
            "testToken",
            fileName,
            UploadType.MEASUREMENT
        )

        // Assert
        MatcherAssert.assertThat(
            result,
            CoreMatchers.`is`(CoreMatchers.equalTo(Result.UPLOAD_SUCCESSFUL))
        )
    }

    /**
     * Tests the basic transmission with a measurement without attached files.
     */
    @Test
    @Throws(CursorIsNullException::class, NoSuchMeasurementException::class)
    fun testSendData() = runBlocking {
        performSendDataTest(
            point3DCount = 600 * 1000,
            locationCount = 3 * 1000,
            0, 0, 0, 0
        )
    }

    /**
     * Tests the basic transmission with a measurement with attached files.
     */
    @Test
    @Throws(CursorIsNullException::class, NoSuchMeasurementException::class)
    fun testSendData_withAttachments() = runBlocking {
        performSendDataTest(
            point3DCount = 0,
            locationCount = 3 * 1000,
            1, 2, 0, 123L
        )
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
        val sampleFiles = SharedTestUtils.randomFiles(0)
        tempFiles.addAll(sampleFiles) // Add to the auto-cleanup list for `tearDown`
        val (measurementIdentifier, _, _, _, distance) = SharedTestUtils.insertSampleMeasurementWithData(
            context, MeasurementStatus.FINISHED,
            persistence, 1, locationCount, 0, 0, 0, sampleFiles
        )
        val loadedStatus: MeasurementStatus =
            persistence.loadMeasurementStatus(measurementIdentifier)
        MatcherAssert.assertThat(
            loadedStatus, CoreMatchers.`is`(
                CoreMatchers.equalTo(MeasurementStatus.FINISHED)
            )
        )

        // Load measurement serialized compressed
        val serializer = MeasurementSerializer()
        val compressedTransferTempFile = serializer.writeSerializedCompressed(
            measurementIdentifier, persistence
        )
        Log.d(
            TAG, "CompressedTransferTempFile size: "
                    + DataSerializable.humanReadableSize(
                compressedTransferTempFile!!.length(),
                true
            )
        )

        // Prepare transmission
        val syncResult = SyncResult()

        // Load meta data
        val tracks = persistence.loadTracks(measurementIdentifier)
        val startLocation = tracks[0].geoLocations[0]
        val lastTrack = tracks[tracks.size - 1].geoLocations
        val endLocation = lastTrack[lastTrack.size - 1]
        val deviceId = UUID.randomUUID()
        val startRecord = GeoLocation(
            startLocation.timestamp,
            startLocation.lat,
            startLocation.lon
        )
        val endRecord = GeoLocation(
            endLocation.timestamp, endLocation.lat,
            endLocation.lon
        )
        val metaData = de.cyface.uploader.model.Measurement(
            MeasurementIdentifier(deviceId, measurementIdentifier),
            DeviceMetaData("testOsVersion", "testDeviceType"),
            ApplicationMetaData("testAppVersion", 3),
            MeasurementMetaData(
                distance,
                locationCount.toLong(),
                startRecord,
                endRecord,
                Modality.BICYCLE.databaseIdentifier,
            ),
            AttachmentMetaData(0, 0, 0, 0),
        )
        val progressListener = object : UploadProgressListener {
            override fun updatedProgress(percent: Float) {
                Log.d(TAG, String.format("Upload Progress %f", percent))
            }
        }
        val fileName =
            "${metaData.identifier.deviceIdentifier}_${metaData.identifier.measurementIdentifier}.${SyncAdapter.COMPRESSED_TRANSFER_FILE_EXTENSION}"

        // Mock the actual post request
        val mockedUploader = object : Uploader {
            override fun measurementsEndpoint(uploadable: Uploadable): URL {
                return URL("https://mocked.cyface.de/api/v123/measurements")
            }

            override fun onUploadFinished(uploadable: Uploadable) {
                // Nothing to do
            }

            override fun attachmentsEndpoint(uploadable: Uploadable): URL {
                return URL("https://mocked.cyface.de/api/v123/measurements/${uploadable.deviceId()}/${uploadable.measurementId()}/attachments")
            }

            override fun uploadMeasurement(
                jwtToken: String,
                uploadable: de.cyface.uploader.model.Measurement,
                file: File,
                progressListener: UploadProgressListener
            ): Result {
                throw UploadFailed(ConflictException("Test ConflictException"))
            }

            override fun uploadAttachment(
                jwtToken: String,
                uploadable: Attachment,
                file: File,
                fileName: String,
                progressListener: UploadProgressListener
            ): Result {
                throw UploadFailed(ConflictException("Test ConflictException"))
            }
        }

        // Act
        try {
            // In the mock settings above we faked a ConflictException from the server
            val result = oocut.sendData(
                mockedUploader,
                syncResult,
                metaData,
                compressedTransferTempFile,
                progressListener,
                "testToken",
                fileName,
                UploadType.MEASUREMENT
            )

            // Assert:
            // because of the ConflictException true should be returned
            MatcherAssert.assertThat(
                result,
                CoreMatchers.`is`(CoreMatchers.equalTo(Result.UPLOAD_SUCCESSFUL))
            )
            // Make sure the ConflictException is actually called (instead of no exception because of mock)
            MatcherAssert.assertThat(
                syncResult.stats.numSkippedEntries, CoreMatchers.`is`(
                    CoreMatchers.equalTo(1L)
                )
            )

            // Cleanup
        } finally {
            if (compressedTransferTempFile.exists()) {
                MatcherAssert.assertThat(
                    compressedTransferTempFile.delete(),
                    CoreMatchers.equalTo(true)
                )
            }
        }
    }

    companion object {
        private const val TAG = "de.cyface.syncPerformerTest"
    }
}