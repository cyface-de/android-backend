package de.cyface.datacapturing.backend;

import static de.cyface.datacapturing.MessageCodes.DATA_CAPTURED;
import static de.cyface.persistence.TestUtils.generateGeoLocation;
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

import android.os.Parcelable;

import de.cyface.datacapturing.EventHandlingStrategy;
import de.cyface.datacapturing.model.CapturedData;
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour;
import de.cyface.persistence.DefaultDistanceCalculationStrategy;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Point3d;
import de.cyface.persistence.model.PointMetaData;
import de.cyface.utils.CursorIsNullException;

/**
 * Tests the inner workings of the {@link DataCapturingBackgroundService} without any calls to the Android system. Uses
 * fake data.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.2.1
 * @since 2.0.0
 */
@RunWith(RobolectricTestRunner.class)
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

    /**
     * Mocking the point meta data to avoid calling Android system functions.
     */
    @Mock
    PointMetaData mockPointMetaData;

    @Mock
    DefaultDistanceCalculationStrategy distanceCalculationStrategy;

    @Mock
    EventHandlingStrategy mockEventHandlingStrategy;

    @Before
    public void setUp() {

        // Replace attributes of DataCapturingBackgroundService with mocked objects
        oocut.persistenceLayer = mockPersistence;
        oocut.capturingBehaviour = mockBehaviour;
        oocut.pointMetaData = mockPointMetaData;
        oocut.eventHandlingStrategy = mockEventHandlingStrategy;
        oocut.distanceCalculationStrategy = distanceCalculationStrategy;
    }

    /**
     * This test case checks the internal workings of the onLocationCaptured method.
     *
     * @throws CursorIsNullException when the content provider is not accessible
     */
    @Test
    public void testOnLocationCapturedDistanceCalculation() throws CursorIsNullException, NoSuchMeasurementException {

        // Arrange
        final int base = 0;
        final int expectedDistance = 2;
        GeoLocation location1 = generateGeoLocation(base);
        GeoLocation location2 = generateGeoLocation(base + expectedDistance);
        GeoLocation location3 = generateGeoLocation(base + 2 * expectedDistance);

        // Mock
        when(distanceCalculationStrategy.calculateDistance(location1, location2))
                .thenReturn(Double.valueOf(expectedDistance));
        when(distanceCalculationStrategy.calculateDistance(location2, location3))
                .thenReturn(Double.valueOf(expectedDistance));
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
        List<Point3d> accelerations = new ArrayList<>(accelerationsSize);
        List<Point3d> rotations = new ArrayList<>(rotationsSize);
        List<Point3d> directions = new ArrayList<>(directionsSize);

        // Create some random test data.
        for (int i = 0; i < accelerationsSize; i++) {
            accelerations.add(new Point3d(random.nextFloat(), random.nextFloat(), random.nextFloat(),
                    Math.abs(random.nextLong())));
        }
        for (int i = 0; i < rotationsSize; i++) {
            rotations.add(new Point3d(random.nextFloat(), random.nextFloat(), random.nextFloat(),
                    Math.abs(random.nextLong())));
        }
        for (int i = 0; i < directionsSize; i++) {
            directions.add(new Point3d(random.nextFloat(), random.nextFloat(), random.nextFloat(),
                    Math.abs(random.nextLong())));
        }
        CapturedData data = new CapturedData(accelerations, rotations, directions);
        ArgumentCaptor<CapturedData> captor = ArgumentCaptor.forClass(CapturedData.class);

        // Hide call to actual Android message service methods.
        doNothing().when(oocut).informCaller(eq(DATA_CAPTURED), any(CapturedData.class));

        // Call test method.
        oocut.onDataCaptured(data);

        // 1247*2 / 800 = 3,1 --> 4
        int times = Math.max(accelerationsSize, Math.max(rotationsSize, directionsSize))
                / DataCapturingBackgroundService.MAXIMUM_CAPTURED_DATA_MESSAGE_SIZE;
        int remainder = Math.max(accelerationsSize, Math.max(rotationsSize, directionsSize))
                % DataCapturingBackgroundService.MAXIMUM_CAPTURED_DATA_MESSAGE_SIZE;
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
