/*
 * Copyright 2022-2023 Cyface GmbH
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
package de.cyface.persistence.serialization

import android.database.Cursor
import android.os.Build.VERSION_CODES
import de.cyface.persistence.content.LocationTable
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests the inner workings of [LocationSerializer].
 *
 * @author Armin Schnabel
 * @version 1.1.1
 * @since 7.3.3
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [VERSION_CODES.P]) // >= Q needs java 9
class LocationSerializerTest {

    /**
     * Used to mock Android API objects.
     */
    @get:Rule
    var mockitoRule: MockitoRule = MockitoJUnit.rule()

    /**
     * A mocked cursor for the first call.
     */
    @Mock
    private val geoLocationsCursor1: Cursor? = null

    /**
     * A mocked cursor for the second call.
     */
    @Mock
    private val geoLocationsCursor2: Cursor? = null

    /**
     * A mocked cursor for the 3rd call.
     */
    @Mock
    private val geoLocationsCursor3: Cursor? = null
    private var oocut: LocationSerializer? = null

    @Before
    fun setUp() {

        // Mock return of two GeoLocations per `readFrom` call (i.e. DATABASE_QUERY_LIMIT = 2)
        Mockito.`when`(geoLocationsCursor1!!.moveToNext()).thenReturn(true).thenReturn(true)
            .thenReturn(false)
        Mockito.`when`(geoLocationsCursor2!!.moveToNext()).thenReturn(true).thenReturn(true)
            .thenReturn(false)
        Mockito.`when`(geoLocationsCursor3!!.moveToNext()).thenReturn(true).thenReturn(true)
            .thenReturn(false)

        // Mock load sample GeoLocation data
        val sampleColumnIndex = 0
        Mockito.`when`(
            geoLocationsCursor1.getColumnIndexOrThrow(
                ArgumentMatchers.any(
                    String::class.java
                )
            )
        ).thenReturn(sampleColumnIndex)
        Mockito.`when`(geoLocationsCursor1.getDouble(sampleColumnIndex)).thenReturn(
            SAMPLE_DOUBLE_VALUE
        )
        Mockito.`when`(geoLocationsCursor1.getLong(sampleColumnIndex)).thenReturn(SAMPLE_LONG_VALUE)
        Mockito.`when`(
            geoLocationsCursor2.getColumnIndexOrThrow(
                ArgumentMatchers.any(
                    String::class.java
                )
            )
        ).thenReturn(sampleColumnIndex)
        Mockito.`when`(geoLocationsCursor2.getDouble(sampleColumnIndex)).thenReturn(
            SAMPLE_DOUBLE_VALUE
        )
        Mockito.`when`(geoLocationsCursor2.getLong(sampleColumnIndex)).thenReturn(SAMPLE_LONG_VALUE)
        // Make accuracy return null as this needs to be handled by the serializer [STAD-481]
        Mockito.`when`(geoLocationsCursor3.getColumnIndexOrThrow(LocationTable.COLUMN_LAT))
            .thenReturn(sampleColumnIndex)
        Mockito.`when`(geoLocationsCursor3.getColumnIndexOrThrow(LocationTable.COLUMN_LON))
            .thenReturn(sampleColumnIndex)
        Mockito.`when`(geoLocationsCursor3.getColumnIndexOrThrow(LocationTable.COLUMN_SPEED))
            .thenReturn(sampleColumnIndex)
        Mockito.`when`(geoLocationsCursor3.getColumnIndexOrThrow(LocationTable.COLUMN_ACCURACY))
            .thenReturn(123)
        Mockito.`when`(
            geoLocationsCursor3.getColumnIndexOrThrow(
                ArgumentMatchers.any(
                    String::class.java
                )
            )
        ).thenReturn(sampleColumnIndex)
        Mockito.`when`(geoLocationsCursor3.getDouble(1)).thenReturn(SAMPLE_DOUBLE_VALUE)
        // when(geoLocationsCursor3.getDoubleOrNull(123)).thenReturn(null);
        Mockito.`when`(geoLocationsCursor3.getLong(sampleColumnIndex)).thenReturn(SAMPLE_LONG_VALUE)
        oocut = LocationSerializer()
    }

    /**
     * Reproducing test for bug [RFR-104].
     *
     *
     * When more than DATABASE_QUERY_LIMIT (in that case 10.000) locations where loaded from the
     * database for serialization, [LocationSerializer.readFrom] was called more
     * than once. As we did initialized `LocationOffsetter` in that function, the offsetter
     * was reset unintentionally, which lead to locations with doubled values (e.g. lat 46, 46, 92, 92).
     */
    @Test
    fun testReadFrom_multipleTimes_usesOneOffsetter() {
        // Arrange - nothing to do

        // Act
        oocut!!.readFrom(geoLocationsCursor1!!)
        oocut!!.readFrom(geoLocationsCursor2!!)
        val res = oocut!!.result()

        // Assert
        MatcherAssert.assertThat(res.timestampCount, CoreMatchers.`is`(CoreMatchers.equalTo(4)))
        // The timestamps 1, 1, 1, 1 should be converted to 1, 0, 0, 0 (offsets)
        // in case they are converted to 1, 0, 1, 0 the offsetter was initialized twice
        MatcherAssert.assertThat(
            res.timestampList,
            CoreMatchers.`is`(CoreMatchers.equalTo(listOf(1L, 0L, 0L, 0L)))
        )
    }

    /**
     * Ensures `accuracy=null` db entries are converted to `0 cm` by the serializer [STAD-481].
     */
    @Test
    fun testReadFrom_withNullAccuracy_isConvertedToZero() {
        // Arrange - nothing to do

        // Act
        oocut!!.readFrom(geoLocationsCursor3!!)
        val res = oocut!!.result()

        // Assert
        MatcherAssert.assertThat(res.accuracyCount, CoreMatchers.`is`(CoreMatchers.equalTo(2)))
        MatcherAssert.assertThat(
            res.accuracyList,
            CoreMatchers.`is`(CoreMatchers.equalTo(listOf(0, 0)))
        )
    }

    companion object {
        private const val SAMPLE_DOUBLE_VALUE = 1.0
        private const val SAMPLE_LONG_VALUE = 1L
    }
}