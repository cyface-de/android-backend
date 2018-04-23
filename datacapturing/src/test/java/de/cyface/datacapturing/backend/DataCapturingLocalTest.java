package de.cyface.datacapturing.backend;

import android.graphics.Point;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import de.cyface.datacapturing.MessageCodes;
import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.model.CapturedData;
import de.cyface.datacapturing.model.Point3D;
import de.cyface.datacapturing.persistence.MeasurementPersistence;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the inner workings of the data capturing without any calls to the Android system. Uses fake data.
 *
 * @author Klemens Muthmann
 * @version 2.0.0
 * @since 2.0.0
 */
public class DataCapturingLocalTest {

    /**
     * We require Mockito to avoid calling Android system functions. This rule is responsible for the initialization of the Spys and Mocks.
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
    @Mock
    MeasurementPersistence mockPersistence;

    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException {
        //oocut = new DataCapturingBackgroundService();
        Field persistenceLayer = DataCapturingBackgroundService.class.getDeclaredField("persistenceLayer");
        persistenceLayer.setAccessible(true);
        persistenceLayer.set(oocut, mockPersistence);
    }

    /**
     * Tests if splitting large data sets works as intended. This is required to avoid the infamous <code>TransactionTooLargeException</code>.
     *
     * @throws DataCapturingException Should not happen, since the relevant methods are mocked.
     */
    @Test
    public void testSplitOfLargeCapturedDataInstances() throws DataCapturingException {
        int someLargeOddNumber = 1247;
        List<Point3D> accerlerations = new ArrayList<>(someLargeOddNumber);
        List<Point3D> rotations = new ArrayList<>(someLargeOddNumber);
        List<Point3D> directions = new ArrayList<>();
        Random random = new Random();

        // Create some random test data.
        for(int i=0;i<someLargeOddNumber;i++) {
            accerlerations.add(new Point3D(random.nextFloat(), random.nextFloat(), random.nextFloat(), Math.abs(random.nextLong())));
            rotations.add(new Point3D(random.nextFloat(), random.nextFloat(), random.nextFloat(), Math.abs(random.nextLong())));
            directions.add(new Point3D(random.nextFloat(), random.nextFloat(), random.nextFloat(), Math.abs(random.nextLong())));
        }
        CapturedData data = new CapturedData(accerlerations, rotations, directions);
        ArgumentCaptor<CapturedData> captor = ArgumentCaptor.forClass(CapturedData.class);

        // Hide call to actual Android message service methods.
        doNothing().when(oocut).informCaller(eq(MessageCodes.DATA_CAPTURED), any(CapturedData.class));
        // Call test method.
        oocut.onDataCaptured(data);
        // 1247 / 400 = 3,1 --> 4
         int times = someLargeOddNumber / DataCapturingBackgroundService.MAXIMUM_CAPTURED_DATA_MESSAGE_SIZE;
         int remainder = someLargeOddNumber % DataCapturingBackgroundService.MAXIMUM_CAPTURED_DATA_MESSAGE_SIZE;
         times = remainder>0 ? ++times : times;
        verify(oocut, times(times)).informCaller(eq(MessageCodes.DATA_CAPTURED), captor.capture());

        int receivedAccelerations = 0;
        int receivedRotations = 0;
        int receivedDirections = 0;
        for(CapturedData dataFromCall : captor.getAllValues()) {
            receivedAccelerations += dataFromCall.getAccelerations().size();
            receivedRotations += dataFromCall.getRotations().size();
            receivedDirections += dataFromCall.getDirections().size();
        }
        assertThat(receivedAccelerations, is(equalTo(someLargeOddNumber)));
        assertThat(receivedRotations, is(equalTo(someLargeOddNumber)));
        assertThat(receivedDirections, is(equalTo(someLargeOddNumber)));
    }
}
