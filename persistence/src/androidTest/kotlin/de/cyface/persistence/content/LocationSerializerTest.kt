/*
 * Copyright 2023-2025 Cyface GmbH
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
package de.cyface.persistence.content

import android.content.Context
import android.database.Cursor
import android.os.RemoteException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.cyface.persistence.DefaultPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.PersistenceBehaviour
import de.cyface.persistence.PersistenceLayer
import de.cyface.persistence.model.GeoLocation
import de.cyface.persistence.model.Modality
import de.cyface.persistence.serialization.LocationSerializer
import de.cyface.persistence.serialization.TransferFileSerializer.getLocationCursor
import de.cyface.testutils.SharedTestUtils.clearPersistenceLayer
import de.cyface.utils.Validate
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.Double.min

/**
 * Tests that the database access of [LocationSerializer]s works.
 *
 * It's the only class which (still) uses the Cursor database interface for locations.
 *
 * @author Armin Schnabel
 * @version 1.0.2
 * @since 3.2.0
 */
@RunWith(AndroidJUnit4::class)
class LocationSerializerTest {

    private lateinit var persistence: PersistenceLayer<PersistenceBehaviour>
    private var context: Context? = null
    private var oocut: LocationSerializer? = null
    private var measurementId: Long? = null

    @Before
    fun setUp() = runBlocking {
        // Initialization
        context = InstrumentationRegistry.getInstrumentation().targetContext
        persistence = DefaultPersistenceLayer(context!!, DefaultPersistenceBehaviour())
        clearPersistenceLayer(context!!, persistence)
        oocut = LocationSerializer()

        // Insert sample data into database
        measurementId = persistence.newMeasurement(Modality.UNKNOWN).id
    }

    /**
     * Deletes all content from the database to make sure it is empty for the next test.
     */
    @After
    fun tearDown() {
        runBlocking { clearPersistenceLayer(context!!, persistence) }
    }

    /**
     * Test that reading from a content provider with a geo location returns that geo location.
     */
    @Test
    fun testReadFrom() {
        testReadFrom(2)
    }

    /**
     * This test makes sure that larger GeoLocation tracks can be loaded completely form the
     * database as there was a bug which limited the query size to 10_000 entries #MOV-248.
     */
    @Test
    fun testReadFrom_10hTrack() {
        // The Location frequency is always 1 Hz, i.e. 10h of measurement:
        testReadFrom(3600 * 10)
    }

    private fun testReadFrom(numberOfTestEntries: Int) = runBlocking {
        Validate.isTrue(numberOfTestEntries >= 2, "not supported")

        // Arrange
        val locations = arrayListOf<GeoLocation>()
        for (i in 1.rangeTo(numberOfTestEntries)) {
            val location = GeoLocation(
                0L,
                i.toLong(),
                min(i + 1.0, 90.0),
                min(i + 2.0, 180.0),
                min(i + 3.0, 10_000.0),
                i + 4.0,
                i + 5.0,
                i + 6.0,
                measurementId!!
            )
            locations.add(location)
        }
        persistence.locationDao!!.insertAll(*locations.toTypedArray())


        // Act
        var cursor: Cursor? = null
        try {
            val count = persistence.locationDao!!.countByMeasurementId(measurementId!!)
            assertThat(count, equalTo(numberOfTestEntries))
            var startIndex = 0
            while (startIndex < count) {
                cursor = getLocationCursor(
                    persistence.database!!,
                    measurementId!!,
                    startIndex,
                    AbstractCyfaceTable.DATABASE_QUERY_LIMIT,
                )
                // if (cursor == null) throw CursorIsNullException()
                oocut!!.readFrom(cursor)
                startIndex += AbstractCyfaceTable.DATABASE_QUERY_LIMIT
            }
        } catch (e: RemoteException) {
            throw java.lang.IllegalStateException(e)
        } finally {
            cursor?.close()
        }
        val result = oocut!!.result()

        // Assert
        assertThat(result.timestampCount, equalTo(numberOfTestEntries))
        assertThat(result.getTimestamp(0), equalTo(locations[0].timestamp))
        assertThat(result.getTimestamp(1), equalTo(locations[1].timestamp - locations[0].timestamp))
        assertThat(result.getLatitude(0), equalTo(2000000))
        assertThat(result.getLatitude(1), equalTo(1000000))
        assertThat(result.getLongitude(0), equalTo(3000000))
        assertThat(result.getLongitude(1), equalTo(1000000))
        assertThat(result.getSpeed(0), equalTo(500))
        assertThat(result.getSpeed(1), equalTo(100))
        assertThat(result.getAccuracy(0), equalTo(600))
        assertThat(result.getAccuracy(1), equalTo(100))
        // We currently don't serialize elevations yet
        //assertThat(result.getElevation(0), equalTo(123))
    }
}