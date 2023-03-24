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
package de.cyface.persistence.serialization;

import static android.os.Build.VERSION_CODES.P;
import static de.cyface.persistence.TestUtils.AUTHORITY;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import android.database.Cursor;
import android.os.RemoteException;

import de.cyface.persistence.content.LocationTable;
import de.cyface.persistence.content.MeasurementProviderClient;
import de.cyface.serializer.LocationOffsetter;

/**
 * Tests the inner workings of {@link LocationSerializer}.
 *
 * @author Armin Schnabel
 * @version 1.1.0
 * @since 7.3.3
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = P) // >= Q needs java 9
public class LocationSerializerTest {

    /**
     * Used to mock Android API objects.
     */
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();
    /**
     * A mock loader, not accessing any database
     */
    @Mock
    private MeasurementProviderClient loader;
    /**
     * A mocked cursor for the first call.
     */
    @Mock
    private Cursor geoLocationsCursor1;
    /**
     * A mocked cursor for the second call.
     */
    @Mock
    private Cursor geoLocationsCursor2;
    /**
     * A mocked cursor for the 3rd call.
     */
    @Mock
    private Cursor geoLocationsCursor3;
    private LocationSerializer oocut;
    private final static double SAMPLE_DOUBLE_VALUE = 1.0;
    private final static long SAMPLE_LONG_VALUE = 1L;

    @Before
    public void setUp() throws RemoteException {

        // Mock GeoLocation database access
        var geoLocationUri = LocationTable.Companion.getUri(AUTHORITY);
        when(loader.createLocationTableUri()).thenReturn(geoLocationUri);

        // Mock return of two GeoLocations per `readFrom` call (i.e. DATABASE_QUERY_LIMIT = 2)
        when(geoLocationsCursor1.moveToNext()).thenReturn(true).thenReturn(true).thenReturn(false);
        when(geoLocationsCursor2.moveToNext()).thenReturn(true).thenReturn(true).thenReturn(false);
        when(geoLocationsCursor3.moveToNext()).thenReturn(true).thenReturn(true).thenReturn(false);

        // Mock load sample GeoLocation data
        int sampleColumnIndex = 0;
        when(geoLocationsCursor1.getColumnIndexOrThrow(any(String.class))).thenReturn(sampleColumnIndex);
        when(geoLocationsCursor1.getDouble(sampleColumnIndex)).thenReturn(SAMPLE_DOUBLE_VALUE);
        when(geoLocationsCursor1.getLong(sampleColumnIndex)).thenReturn(SAMPLE_LONG_VALUE);
        when(geoLocationsCursor2.getColumnIndexOrThrow(any(String.class))).thenReturn(sampleColumnIndex);
        when(geoLocationsCursor2.getDouble(sampleColumnIndex)).thenReturn(SAMPLE_DOUBLE_VALUE);
        when(geoLocationsCursor2.getLong(sampleColumnIndex)).thenReturn(SAMPLE_LONG_VALUE);
        // Make accuracy return null as this needs to be handled by the serializer [STAD-481]
        when(geoLocationsCursor3.getColumnIndexOrThrow(LocationTable.COLUMN_LAT)).thenReturn(sampleColumnIndex);
        when(geoLocationsCursor3.getColumnIndexOrThrow(LocationTable.COLUMN_LON)).thenReturn(sampleColumnIndex);
        when(geoLocationsCursor3.getColumnIndexOrThrow(LocationTable.COLUMN_SPEED)).thenReturn(sampleColumnIndex);
        when(geoLocationsCursor3.getColumnIndexOrThrow(LocationTable.COLUMN_ACCURACY)).thenReturn(123);
        when(geoLocationsCursor3.getColumnIndexOrThrow(any(String.class))).thenReturn(sampleColumnIndex);
        when(geoLocationsCursor3.getDouble(1)).thenReturn(SAMPLE_DOUBLE_VALUE);
        //when(geoLocationsCursor3.getDoubleOrNull(123)).thenReturn(null);
        when(geoLocationsCursor3.getLong(sampleColumnIndex)).thenReturn(SAMPLE_LONG_VALUE);

        oocut = new LocationSerializer();
    }

    /**
     * Reproducing test for bug [RFR-104].
     * <p>
     * When more than DATABASE_QUERY_LIMIT (in that case 10.000) locations where loaded from the
     * database for serialization, {@link LocationSerializer#readFrom(Cursor)} was called more
     * than once. As we did initialized {@link LocationOffsetter} in that function, the offsetter
     * was reset unintentionally, which lead to locations with doubled values (e.g. lat 46, 46, 92, 92).
     */
    @Test
    public void testReadFrom_multipleTimes_usesOneOffsetter() throws RemoteException {
        // Arrange - nothing to do
        when(loader.loadGeoLocations(anyInt(), anyInt())).thenReturn(geoLocationsCursor1)
                .thenReturn(geoLocationsCursor2);

        // Act
        oocut.readFrom(geoLocationsCursor1);
        oocut.readFrom(geoLocationsCursor2);
        final var res = oocut.result();

        // Assert
        assertThat(res.getTimestampCount(), is(equalTo(4)));
        // The timestamps 1, 1, 1, 1 should be converted to 1, 0, 0, 0 (offsets)
        // in case they are converted to 1, 0, 1, 0 the offsetter was initialized twice
        assertThat(res.getTimestampList(), is(equalTo(Arrays.asList(1L, 0L, 0L, 0L))));
    }

    /**
     * Ensures `accuracy=null` db entries are converted to `0 cm` by the serializer [STAD-481].
     */
    @Test
    public void testReadFrom_withNullAccuracy_isConvertedToZero() throws RemoteException {
        // Arrange - nothing to do
        when(loader.loadGeoLocations(anyInt(), anyInt())).thenReturn(geoLocationsCursor3);

        // Act
        oocut.readFrom(geoLocationsCursor3);
        final var res = oocut.result();

        // Assert
        assertThat(res.getAccuracyCount(), is(equalTo(2)));
        assertThat(res.getAccuracyList(), is(equalTo(Arrays.asList(0, 0))));
    }
}
