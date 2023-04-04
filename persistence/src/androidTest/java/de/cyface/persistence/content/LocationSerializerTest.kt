/*
 * Copyright 2023 Cyface GmbH
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
import de.cyface.testutils.SharedTestUtils.clearPersistenceLayer
import de.cyface.utils.CursorIsNullException
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests that the database access of [LocationSerializer]s works.
 *
 * It's the only class which (still) uses the Cursor database interface for locations.
 *
 * @author Armin Schnabel
 * @version 1.0.0
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
        // Arrange
        val location1 = GeoLocation(
            1L,
            2.0,
            3.0,
            4.0,
            5.0,
            6.0,
            7.0,
            measurementId!!
        )
        val location2 = GeoLocation(
            2L,
            3.0,
            4.0,
            5.0,
            6.0,
            7.0,
            8.0,
            measurementId!!
        )
        persistence.locationDao!!.insertAll(location1, location2)

        // Act
        var cursor: Cursor? = null
        try {
            val count = persistence.locationDao!!.countByMeasurementId(measurementId!!)
            var startIndex = 0
            while (startIndex < count) {
                cursor =
                    persistence.locationDao!!.selectAllByMeasurementId(
                        measurementId!!,
                        startIndex,
                        AbstractCyfaceTable.DATABASE_QUERY_LIMIT
                    )
                if (cursor == null) throw CursorIsNullException()
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
        assertThat(result.timestampCount, equalTo(2))
        assertThat(result.getTimestamp(0), equalTo(location1.timestamp))
        assertThat(result.getTimestamp(1), equalTo(location2.timestamp - location1.timestamp))
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