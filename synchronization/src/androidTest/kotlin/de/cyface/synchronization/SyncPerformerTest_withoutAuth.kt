/*
 * Copyright 2018-2025 Cyface GmbH
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import de.cyface.persistence.DefaultPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.exception.NoSuchMeasurementException
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.model.MeasurementStatus
import de.cyface.synchronization.TestUtils.loadMetaData
import de.cyface.synchronization.TestUtils.loadSerializedCompressed
import de.cyface.testutils.SharedTestUtils.clearPersistenceLayer
import de.cyface.testutils.SharedTestUtils.insertSampleMeasurementWithData
import de.cyface.uploader.DefaultUploader
import de.cyface.uploader.Result
import de.cyface.uploader.UploadProgressListener
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
import de.cyface.uploader.exception.UploadSessionExpired
import de.cyface.utils.CursorIsNullException
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

/**
 * Tests the actual data transmission code. Since this test requires a running Movebis API server, and communicates with
 * that server, it is a flaky test and a large test.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.7.0
 * @see [Testing documentation](http://d.android.com/tools/testing)
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class SyncPerformerTestWithoutAuth {
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

        oocut = SyncPerformer(context, true)
    }

    @After
    fun tearDown() {
        runBlocking { clearPersistenceLayer(context, persistence) }
    }

    @Test
    @Ignore("Uses an actual API on purpose (to test upload against that API)")
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
                requireNotNull(client) {
                    "Unable to acquire client for content provider ${TestUtils.AUTHORITY}"
                }
                file = loadSerializedCompressed(persistence, measurementIdentifier)
                val metaData =
                    loadMetaData(persistence, measurement, locationCount, 0, 0, 0, 0)
                val url = "$TEST_API_URL/api/v3/measurements"

                // Act
                val result = DefaultUploader(url).uploadMeasurement(
                    TEST_TOKEN, metaData, file!!, object : UploadProgressListener {
                        override fun updatedProgress(percent: Float) {
                            // Nothing to do
                        }
                    })

                // Assert
                assertThat(
                    result,
                    CoreMatchers.`is`(equalTo(Result.UPLOAD_SUCCESSFUL))
                )
            }
        } finally {
            if (file != null && file!!.exists()) {
                require(file!!.delete())
            }
        }
    }

    companion object {
        // ATTENTION: Depending on the API you test against, you might also need to replace the res/raw/truststore.jks
        private const val TEST_API_URL =
            "http://localhost:8080" // never use a non-numeric port here!
        private const val TEST_TOKEN = "ey*****"
    }
}
