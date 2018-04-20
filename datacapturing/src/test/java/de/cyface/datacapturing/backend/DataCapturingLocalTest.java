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

public class DataCapturingLocalTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Spy
    DataCapturingBackgroundService oocut;

    @Mock
    MeasurementPersistence mockPersistence;

    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException {
        //oocut = new DataCapturingBackgroundService();
        Field persistenceLayer = DataCapturingBackgroundService.class.getDeclaredField("persistenceLayer");
        persistenceLayer.setAccessible(true);
        persistenceLayer.set(oocut, mockPersistence);
    }

    @Test
    public void testSplitOfLargeCapturedDataInstances() {
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
        verify(oocut, times(4)).informCaller(eq(MessageCodes.DATA_CAPTURED), captor.capture());

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
