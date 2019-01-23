package de.cyface.datacapturing.backend;

import static de.cyface.datacapturing.MessageCodes.DATA_CAPTURED;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import de.cyface.datacapturing.model.CapturedData;
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.Point3d;
import de.cyface.persistence.model.PointMetaData;

/**
 * Tests the inner workings of the data capturing without any calls to the Android system. Uses fake data.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.1.0
 * @since 2.0.0
 */
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
    PersistenceLayer mockPersistence;

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

    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException {

        // Replace attributes of DataCapturingBackgroundService with mocked objects
        Field persistenceLayer = DataCapturingBackgroundService.class.getDeclaredField("persistenceLayer");
        Field capturingBehaviour = DataCapturingBackgroundService.class.getDeclaredField("capturingBehaviour");
        Field pointMetaData = DataCapturingBackgroundService.class.getDeclaredField("pointMetaData");
        persistenceLayer.setAccessible(true);
        persistenceLayer.set(oocut, mockPersistence);
        capturingBehaviour.setAccessible(true);
        capturingBehaviour.set(oocut, mockBehaviour);
        pointMetaData.setAccessible(true);
        pointMetaData.set(oocut, mockPointMetaData);
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
