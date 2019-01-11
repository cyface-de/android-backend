package de.cyface.datacapturing.model;

import static de.cyface.datacapturing.ServiceTestUtils.AUTHORITY;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.ContentResolver;
import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.provider.ProviderTestRule;
import de.cyface.datacapturing.persistence.MeasurementPersistence;
import de.cyface.datacapturing.persistence.WritingDataCompletedCallback;
import de.cyface.persistence.MeasuringPointsContentProvider;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.Point3D;
import de.cyface.persistence.model.Vehicle;
import de.cyface.persistence.serialization.AccelerationsFile;
import de.cyface.persistence.serialization.DirectionsFile;
import de.cyface.persistence.serialization.FileCorruptedException;
import de.cyface.persistence.serialization.GeoLocationsFile;
import de.cyface.persistence.serialization.MetaFile;
import de.cyface.persistence.serialization.RotationsFile;

/**
 * Tests whether captured data is correctly saved to the underlying content provider. This test uses
 * <code>ProviderTestRule</code> to get a mocked content provider. Implementation details are explained in the
 * <a href="https://developer.android.com/reference/android/support/test/rule/provider/ProviderTestRule">Android
 * documentation</a>.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 5.0.3
 * @since 1.0.0
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class CapturedDataWriterTest {
    /**
     * Test rule that provides a mock connection to a <code>ContentProvider</code> to test against.
     */
    @Rule
    public ProviderTestRule providerRule = new ProviderTestRule.Builder(MeasuringPointsContentProvider.class, AUTHORITY)
            .build();
    /**
     * The object of the class under test.
     */
    private MeasurementPersistence oocut;
    /**
     * {@link Context} used to access the persistence layer
     */
    private Context context;

    /**
     * Initializes the test case as explained in the <a href=
     * "https://developer.android.com/training/testing/integration-testing/content-provider-testing.html#build">Android
     * documentation</a>.
     */
    @Before
    public void setUp() {
        ContentResolver mockResolver = providerRule.getResolver();
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        oocut = new MeasurementPersistence(context, mockResolver, AUTHORITY);
        // This is normally called in the <code>DataCapturingService#Constructor</code>
        oocut.restoreOrCreateDeviceId();
    }

    /**
     * Deletes all content from the database to make sure it is empty for the next test.
     */
    @After
    public void tearDown() {
        oocut.clear();
    }

    /**
     * Tests whether creating and closing a measurement works as expected.
     */
    @Test
    public void testCreateNewMeasurement() throws FileCorruptedException, NoSuchMeasurementException {
        // Create a measurement
        Measurement measurement = oocut.newMeasurement(Vehicle.UNKNOWN);
        assertThat(measurement.getIdentifier() >= 0L, is(equalTo(true)));

        // Try to load the created measurement and check its properties
        List<Measurement> openMeasurements = oocut.loadOpenMeasurements();
        Measurement loadedOpenMeasurement = oocut.loadCurrentlyCapturedMeasurement();
        assertThat(loadedOpenMeasurement, notNullValue());
        assertThat(loadedOpenMeasurement.getIdentifier(), is(measurement.getIdentifier()));
        assertThat(openMeasurements.size(), is(equalTo(1)));

        // Write point counters to MetaFile
        MetaFile.append(context, measurement.getIdentifier(), new MetaFile.PointMetaData(1, 0, 0, 0));

        // Close the measurement, load the closed measurement and check its properties
        oocut.closeRecentMeasurement();
        Measurement closedMeasurement = oocut.loadMeasurement(measurement.getIdentifier());
        if (closedMeasurement == null) {
            throw new IllegalStateException("Test failed because it was unable to load data from content provider.");
        }

        MetaFile.MetaData metaData = MetaFile.deserialize(context, measurement.getIdentifier());
        assertThat(metaData.getVehicle().name(), is(equalTo(Vehicle.UNKNOWN.name())));

        openMeasurements = oocut.loadOpenMeasurements();
        List<Measurement> finishedMeasurements = oocut.loadFinishedMeasurements();
        assertThat(finishedMeasurements.size(), is(equalTo(1)));
        assertThat(openMeasurements.size(), is(equalTo(0)));
        metaData = MetaFile.deserialize(context, measurement.getIdentifier());
        assertThat(metaData.getVehicle().name(), is(equalTo(Vehicle.UNKNOWN.name())));
    }

    /**
     * Tests whether data is stored correctly via the <code>MeasurementPersistence</code>.
     */
    @Test
    public void testStoreData() throws FileCorruptedException {
        // Manually trigger data capturing (new measurement with sensor data and a location)
        Measurement measurement = oocut.newMeasurement(Vehicle.UNKNOWN);

        final Lock lock = new ReentrantLock();
        final Condition condition = lock.newCondition();
        WritingDataCompletedCallback callback = new WritingDataCompletedCallback() {
            @Override
            public void writingDataCompleted() {
                lock.lock();
                try {
                    condition.signal();
                } finally {
                    lock.unlock();
                }
            }
        };

        oocut.storeData(testData(), measurement.getIdentifier(), callback);

        lock.lock();
        try {
            condition.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }

        oocut.storeLocation(testLocation(), measurement.getIdentifier());

        // Write point counters to MetaFile
        MetaFile.append(context, measurement.getIdentifier(), new MetaFile.PointMetaData(1, 3, 3, 3));

        // Check if the captured data was persisted
        List<GeoLocation> geoLocations = GeoLocationsFile.deserialize(context, measurement.getIdentifier());
        List<Point3D> accelerations = AccelerationsFile.deserialize(context, measurement.getIdentifier());
        List<Point3D> rotations = RotationsFile.deserialize(context, measurement.getIdentifier());
        List<Point3D> directions = DirectionsFile.deserialize(context, measurement.getIdentifier());

        assertThat(geoLocations.size(), is(equalTo(1)));
        assertThat(accelerations.size(), is(equalTo(3)));
        assertThat(rotations.size(), is(equalTo(3)));
        assertThat(directions.size(), is(equalTo(3)));
    }

    /**
     * Tests whether cascading deletion of measurements together with all data is working correctly.
     */
    @Test
    public void testCascadingClearMeasurements() {
        // Insert some test data
        oocut.newMeasurement(Vehicle.UNKNOWN);
        Measurement measurement = oocut.newMeasurement(Vehicle.CAR);

        final Lock lock = new ReentrantLock();
        final Condition condition = lock.newCondition();
        WritingDataCompletedCallback finishedCallback = new WritingDataCompletedCallback() {
            @Override
            public void writingDataCompleted() {
                lock.lock();
                try {
                    condition.signal();
                } finally {
                    lock.unlock();
                }
            }
        };

        oocut.storeData(testData(), measurement.getIdentifier(), finishedCallback);
        oocut.storeLocation(testLocation(), measurement.getIdentifier());

        lock.lock();
        try {
            condition.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }

        // clear the test data
        int removedRows = oocut.clear();
        assertThat(removedRows, is(equalTo(2)));

        // make sure nothing is left.
        assertThat(oocut.loadMeasurements().size(), is(equalTo(0)));
    }

    /**
     * Tests whether loading measurements from the data storage via <code>MeasurementPersistence</code> is working as
     * expected.
     *
     * @throws NoSuchMeasurementException If the test measurement was null for some reason. This should only happen if
     *             there was a very serious database error.
     */
    @Test
    public void testLoadMeasurements() throws NoSuchMeasurementException {
        Measurement measurement1 = oocut.newMeasurement(Vehicle.UNKNOWN);
        MetaFile.append(context, measurement1.getIdentifier(), new MetaFile.PointMetaData(0, 0, 0, 0));
        oocut.closeRecentMeasurement();

        Measurement measurement2 = oocut.newMeasurement(Vehicle.CAR);
        MetaFile.append(context, measurement2.getIdentifier(), new MetaFile.PointMetaData(0, 0, 0, 0));
        oocut.closeRecentMeasurement();

        List<Measurement> loadedMeasurements = oocut.loadMeasurements();
        assertThat(loadedMeasurements.size(), is(equalTo(2)));

        for (Measurement measurement : loadedMeasurements) {
            oocut.delete(measurement);
        }
    }

    /**
     * Tests whether deleting a measurement actually remove that measurement together with all corresponding data.
     *
     * @throws NoSuchMeasurementException If the test measurement was null for some reason. This should only happen if
     *             there was a very serious database error.
     */
    @Test
    public void testDeleteMeasurement() throws NoSuchMeasurementException {
        Measurement measurement = oocut.newMeasurement(Vehicle.UNKNOWN);

        final Lock lock = new ReentrantLock();
        final Condition condition = lock.newCondition();

        WritingDataCompletedCallback callback = new WritingDataCompletedCallback() {
            @Override
            public void writingDataCompleted() {
                lock.lock();
                try {
                    condition.signal();
                } finally {
                    lock.unlock();
                }
            }
        };
        oocut.storeData(testData(), measurement.getIdentifier(), callback);

        lock.lock();
        try {
            condition.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }

        oocut.storeLocation(testLocation(), measurement.getIdentifier());

        MetaFile.append(context, measurement.getIdentifier(), new MetaFile.PointMetaData(1, 3, 3, 3));
        oocut.closeRecentMeasurement();

        oocut.delete(measurement);

        assertThat(oocut.loadMeasurements().size(), is(equalTo(0)));
    }

    /**
     * Tests whether loading a track of geo locations is possible via the {@link MeasurementPersistence} object.
     *
     * @throws NoSuchMeasurementException if the created measurement is null for some unexpected reason.
     */
    @Test
    public void testLoadTrack() throws NoSuchMeasurementException {
        Measurement measurement = oocut.newMeasurement(Vehicle.UNKNOWN);
        oocut.storeLocation(testLocation(), measurement.getIdentifier());
        MetaFile.append(context, measurement.getIdentifier(), new MetaFile.PointMetaData(1, 0, 0, 0));
        oocut.closeRecentMeasurement();
        List<Measurement> measurements = oocut.loadMeasurements();
        assertThat(measurements.size(), is(equalTo(1)));
        for (Measurement loadedMeasurement : measurements) {
            assertThat(oocut.loadTrack(loadedMeasurement).size(), is(equalTo(1)));
        }
    }

    @Test
    public void testProvokeAnr() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                if (!oocut.hasOpenMeasurement()) {
                    oocut.newMeasurement(Vehicle.BICYCLE);
                }
                if (!oocut.hasOpenMeasurement()) {
                    oocut.newMeasurement(Vehicle.BICYCLE);
                }

                if (oocut.hasOpenMeasurement()) {
                    try {
                        oocut.closeRecentMeasurement();
                    } catch (NoSuchMeasurementException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
        });
    }

    /**
     * @return An initialized {@link GeoLocation} object with garbage data for testing.
     */
    private GeoLocation testLocation() {
        return new GeoLocation(1.0, 1.0, 1L, 1.0, 1);
    }

    /**
     * @return An initialized {@link CapturedData} object with garbage data for testing.
     */
    private CapturedData testData() {
        List<Point3D> accelerations = new ArrayList<>();
        accelerations.add(new Point3D(1.0f, 1.0f, 1.0f, 1L));
        accelerations.add(new Point3D(2.0f, 2.0f, 2.0f, 2L));
        accelerations.add(new Point3D(3.0f, 3.0f, 3.0f, 3L));
        List<Point3D> directions = new ArrayList<>();
        directions.add(new Point3D(4.0f, 4.0f, 4.0f, 4L));
        directions.add(new Point3D(5.0f, 5.0f, 5.0f, 5L));
        directions.add(new Point3D(6.0f, 6.0f, 6.0f, 6L));
        List<Point3D> rotations = new ArrayList<>();
        rotations.add(new Point3D(7.0f, 7.0f, 7.0f, 7L));
        rotations.add(new Point3D(8.0f, 8.0f, 8.0f, 8L));
        rotations.add(new Point3D(9.0f, 9.0f, 9.0f, 9L));
        return new CapturedData(accelerations, rotations, directions);
    }
}
