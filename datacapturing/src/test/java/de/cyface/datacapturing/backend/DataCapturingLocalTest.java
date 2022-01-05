/*
 * Copyright 2017 Cyface GmbH
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
package de.cyface.datacapturing.backend;

import static de.cyface.datacapturing.MessageCodes.DATA_CAPTURED;
import static de.cyface.datacapturing.backend.DataCapturingBackgroundService.MAXIMUM_CAPTURED_DATA_MESSAGE_SIZE;
import static de.cyface.testutils.SharedTestUtils.generateGeoLocation;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import android.os.Build;
import android.os.Parcelable;

import de.cyface.datacapturing.EventHandlingStrategy;
import de.cyface.datacapturing.model.CapturedData;
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour;
import de.cyface.persistence.DefaultDistanceCalculationStrategy;
import de.cyface.persistence.DefaultLocationCleaningStrategy;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.exception.NoSuchMeasurementException;
import de.cyface.persistence.model.ParcelableGeoLocation;
import de.cyface.persistence.model.ParcelablePoint3D;
import de.cyface.utils.CursorIsNullException;

/**
 * Tests the inner workings of the {@link DataCapturingBackgroundService} without any calls to the Android system. Uses
 * fake data.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.3.1
 * @since 2.0.0
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.O_MR1) // To be able to execute tests with Java 8 (instead of 9)
public class DataCapturingLocalTest {

    /**
     * We require Mockito to avoid calling Android system functions. This rule is responsible for the initialization of
     * the Spies and Mocks.
     */
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();
    /**
     * The object of the class under test
     */
    @Spy
    DataCapturingBackgroundService oocut;
    /**
     * Mocking the persistence layer to avoid calling Android system functions.
     */
    @Spy
    PersistenceLayer<CapturingPersistenceBehaviour> mockPersistence;
    /**
     * Mocking the persistence behaviour to avoid calling Android system functions.
     */
    @Mock
    CapturingPersistenceBehaviour mockBehaviour;
    @Mock
    DefaultDistanceCalculationStrategy distanceCalculationStrategy;
    @Mock
    DefaultLocationCleaningStrategy locationCleaningStrategy;
    @Mock
    EventHandlingStrategy mockEventHandlingStrategy;
    private final int base = 0;
    private final ParcelableGeoLocation location1 = generateGeoLocation(base);

    @Before
    public void setUp() {

        // Replace attributes of DataCapturingBackgroundService with mocked objects
        oocut.persistenceLayer = mockPersistence;
        oocut.capturingBehaviour = mockBehaviour;
        oocut.eventHandlingStrategy = mockEventHandlingStrategy;
        oocut.distanceCalculationStrategy = distanceCalculationStrategy;
        oocut.locationCleaningStrategy = locationCleaningStrategy;
        oocut.startupTime = location1.getTimestamp(); // locations with a smaller timestamp are filtered
    }

    /**
     * This test case checks the internal workings of the onLocationCaptured method.
     *
     * @throws CursorIsNullException when the content provider is not accessible
     */
    @Test
    public void testOnLocationCapturedDistanceCalculation() throws CursorIsNullException, NoSuchMeasurementException {

        // Arrange
        final int expectedDistance = 2;
        ParcelableGeoLocation location2 = generateGeoLocation(base + expectedDistance);
        ParcelableGeoLocation location3 = generateGeoLocation(base + 2 * expectedDistance);

        // Mock
        when(distanceCalculationStrategy.calculateDistance(location1, location2))
                .thenReturn(Double.valueOf(expectedDistance));
        when(distanceCalculationStrategy.calculateDistance(location2, location3))
                .thenReturn(Double.valueOf(expectedDistance));
        when(locationCleaningStrategy.isClean(any(ParcelableGeoLocation.class))).thenReturn(true);
        doNothing().when(oocut).informCaller(anyInt(), any(Parcelable.class));

        // Act
        oocut.onLocationCaptured(location1);
        oocut.onLocationCaptured(location2); // On second call a distance should be calculated
        oocut.onLocationCaptured(location3); // Now the two distances should be added

        // Assert
        verify(mockBehaviour, times(1)).updateDistance(expectedDistance);
        verify(mockBehaviour, times(1)).updateDistance(2 * expectedDistance);
    }

    /**
     * This test case checks the internal workings of the onLocationCaptured method in the special case
     * where a cached location with a timestamp smaller than the start time of the background service is returned.
     * <p>
     * Those "cached" locations are filtered by the background service (STAD-140).
     *
     * @throws CursorIsNullException when the content provider is not accessible
     */
    @Test
    public void testOnLocationCapturedDistanceCalculation_withCachedLocation()
            throws CursorIsNullException, NoSuchMeasurementException {

        // Arrange
        final int expectedDistance = 2;
        ParcelableGeoLocation cachedLocation = generateGeoLocation(base - expectedDistance);
        ParcelableGeoLocation location2 = generateGeoLocation(base + expectedDistance);
        ParcelableGeoLocation location3 = generateGeoLocation(base + 2 * expectedDistance);

        // Mock
        // When the onLocationCaptured implementation is correct, this method is never called.
        // But we need to keep this mock or else this test won't fail when the startupTime filter is missing
        when(distanceCalculationStrategy.calculateDistance(cachedLocation, location1))
                .thenReturn(Double.valueOf(expectedDistance));
        when(distanceCalculationStrategy.calculateDistance(location1, location2))
                .thenReturn(Double.valueOf(expectedDistance));
        when(distanceCalculationStrategy.calculateDistance(location2, location3))
                .thenReturn(Double.valueOf(expectedDistance));
        when(locationCleaningStrategy.isClean(any(ParcelableGeoLocation.class))).thenReturn(true);
        doNothing().when(oocut).informCaller(anyInt(), any(Parcelable.class));

        // Act
        oocut.onLocationCaptured(cachedLocation);
        oocut.onLocationCaptured(location1);
        oocut.onLocationCaptured(location2); // On second call a distance should be calculated
        oocut.onLocationCaptured(location3); // Now the two distances should be added

        // Assert
        verify(mockBehaviour, times(1)).updateDistance(expectedDistance);
        verify(mockBehaviour, times(1)).updateDistance(2 * expectedDistance);
        verify(mockBehaviour, times(0)).updateDistance(3 * expectedDistance);
    }

    /**
     * Tests if splitting large data sets works as intended. This is required to avoid the infamous
     * <code>TransactionTooLargeException</code>.
     */
    @Test
    public void testSplitOfLargeCapturedDataInstances() {
        int someLargeOddNumber = 1247;
        Random random = new Random();
        int accelerationsSize = someLargeOddNumber * 2;
        // noinspection UnnecessaryLocalVariable - because this is better readable
        int rotationsSize = someLargeOddNumber;
        int directionsSize = someLargeOddNumber / 2;
        List<ParcelablePoint3D> accelerations = new ArrayList<>(accelerationsSize);
        List<ParcelablePoint3D> rotations = new ArrayList<>(rotationsSize);
        List<ParcelablePoint3D> directions = new ArrayList<>(directionsSize);

        // Create some random test data.
        for (int i = 0; i < accelerationsSize; i++) {
            accelerations.add(new ParcelablePoint3D(random.nextFloat(), random.nextFloat(), random.nextFloat(),
                    Math.abs(random.nextLong())));
        }
        for (int i = 0; i < rotationsSize; i++) {
            rotations.add(new ParcelablePoint3D(random.nextFloat(), random.nextFloat(), random.nextFloat(),
                    Math.abs(random.nextLong())));
        }
        for (int i = 0; i < directionsSize; i++) {
            directions.add(new ParcelablePoint3D(random.nextFloat(), random.nextFloat(), random.nextFloat(),
                    Math.abs(random.nextLong())));
        }
        CapturedData data = new CapturedData(accelerations, rotations, directions);
        ArgumentCaptor<CapturedData> captor = ArgumentCaptor.forClass(CapturedData.class);

        // Hide call to actual Android message service methods.
        doNothing().when(oocut).informCaller(eq(DATA_CAPTURED), any(CapturedData.class));

        // Call test method.
        oocut.onDataCaptured(data);

        // 1247*2 / 800 = 3,1 --> 4
        // noinspection ConstantConditions
        final var maxSensorSize = Math.max(accelerationsSize, Math.max(rotationsSize, directionsSize));
        int times = maxSensorSize / MAXIMUM_CAPTURED_DATA_MESSAGE_SIZE;
        int remainder = maxSensorSize % MAXIMUM_CAPTURED_DATA_MESSAGE_SIZE;
        // noinspection ConstantConditions
        times = remainder > 0 ? ++times : times;
        verify(oocut, times(times)).informCaller(eq(DATA_CAPTURED), captor.capture());

        int receivedAccelerations = 0;
        int receivedRotations = 0;
        int receivedDirections = 0;
        for (CapturedData dataFromCall : captor.getAllValues()) {
            receivedAccelerations += dataFromCall.getAccelerations().size();
            receivedRotations += dataFromCall.getRotations().size();
            receivedDirections += dataFromCall.getDirections().size();
        }
        assertThat(receivedAccelerations, is(equalTo(accelerationsSize)));
        assertThat(receivedRotations, is(equalTo(rotationsSize)));
        assertThat(receivedDirections, is(equalTo(directionsSize)));
    }
}
