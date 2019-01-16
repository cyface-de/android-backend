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

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.provider.ProviderTestRule;
import de.cyface.datacapturing.persistence.MeasurementPersistence;
import de.cyface.datacapturing.persistence.WritingDataCompletedCallback;
import de.cyface.persistence.FileUtils;
import de.cyface.persistence.MeasuringPointsContentProvider;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.Point3D;
import de.cyface.persistence.model.Vehicle;
import de.cyface.persistence.serialization.FileCorruptedException;
import de.cyface.persistence.serialization.GeoLocationsFile;
import de.cyface.persistence.serialization.MetaFile;
import de.cyface.persistence.serialization.Point3dFile;

/**
 * Tests whether captured data is correctly saved to the underlying content provider. This test uses
 * <code>ProviderTestRule</code> to get a mocked content provider. Implementation details are explained in the
 * <a href="https://developer.android.com/reference/android/support/test/rule/provider/ProviderTestRule">Android
 * documentation</a>.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 5.2.0
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
     * Initializes the test case as explained in the <a href=
     * "https://developer.android.com/training/testing/integration-testing/content-provider-testing.html#build">Android
     * documentation</a>.
     */
    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        oocut = new MeasurementPersistence(context, AUTHORITY);
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
        assertThat(measurement.getIdentifier() > 0L, is(equalTo(true)));

        // Try to load the created measurement and check its properties
        Measurement currentlyCapturedMeasurement = oocut.loadCurrentlyCapturedMeasurement();
        assertThat(currentlyCapturedMeasurement, notNullValue());
        assertThat(currentlyCapturedMeasurement.getIdentifier(), is(measurement.getIdentifier()));
        assertThat(oocut.loadMeasurements(Measurement.MeasurementStatus.OPEN).size(), is(equalTo(1)));

        // Write point counters to MetaFile
        measurement.getMetaFile().append(new MetaFile.PointMetaData(1, 0, 0, 0));

        // Finish the measurement (TODO: the status of the previously loaded measurement is then outdated!)
        oocut.finishRecentMeasurement(measurement);

        // Load the finished measurement
        assertThat(oocut.loadMeasurements(Measurement.MeasurementStatus.FINISHED).size(), is(equalTo(1)));
        assertThat(oocut.loadMeasurements(Measurement.MeasurementStatus.OPEN).size(), is(equalTo(0)));
        Measurement loadedFinishedMeasurement = oocut.loadMeasurement(measurement.getIdentifier(),
                Measurement.MeasurementStatus.FINISHED);
        if (loadedFinishedMeasurement == null) {
            throw new IllegalStateException("Test failed because it was unable to load data from persistence layer.");
        }

        // Check that the vehicle id was stored correctly
        //loadedFinishedMeasurement.loadMetaFile(); // TODO: this should maybe happen automatically?
        MetaFile.MetaData metaData = loadedFinishedMeasurement.getMetaFile().deserialize();
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

        oocut.storeData(testData(), measurement, callback);

        lock.lock();
        try {
            condition.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }

        oocut.storeLocation(testLocation(), measurement);

        // Write point counters to MetaFile
        measurement.getMetaFile().append(new MetaFile.PointMetaData(1, 3, 3, 3));

        // Check if the captured data was persisted
        List<GeoLocation> geoLocations = GeoLocationsFile.loadFile(measurement).deserialize();
        List<Point3D> accelerations = Point3dFile
                .loadFile(measurement, FileUtils.ACCELERATIONS_FILE_NAME, FileUtils.ACCELERATIONS_FILE_EXTENSION)
                .deserialize();
        List<Point3D> rotations = Point3dFile
                .loadFile(measurement, FileUtils.ROTATIONS_FILE_NAME, FileUtils.ROTATION_FILE_EXTENSION).deserialize();
        List<Point3D> directions = Point3dFile
                .loadFile(measurement, FileUtils.DIRECTION_FILE_NAME, FileUtils.DIRECTION_FILE_EXTENSION).deserialize();

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

        oocut.storeData(testData(), measurement, finishedCallback);
        oocut.storeLocation(testLocation(), measurement);

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
     */
    @Test
    public void testLoadMeasurements() {
        Measurement measurement1 = oocut.newMeasurement(Vehicle.UNKNOWN);
        measurement1.getMetaFile().append(new MetaFile.PointMetaData(0, 0, 0, 0));
        oocut.finishRecentMeasurement(measurement1);

        Measurement measurement2 = oocut.newMeasurement(Vehicle.CAR);
        measurement2.getMetaFile().append(new MetaFile.PointMetaData(0, 0, 0, 0));
        oocut.finishRecentMeasurement(measurement2);

        List<Measurement> loadedMeasurements = oocut.loadMeasurements();
        assertThat(loadedMeasurements.size(), is(equalTo(2)));

        for (Measurement measurement : loadedMeasurements) {
            oocut.delete(measurement);
        }
    }

    /**
     * Tests whether deleting a measurement actually remove that measurement together with all corresponding data.
     *
     */
    @Test
    public void testDeleteMeasurement() {
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
        oocut.storeData(testData(), measurement, callback);

        lock.lock();
        try {
            condition.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }

        oocut.storeLocation(testLocation(), measurement);

        measurement.getMetaFile().append(new MetaFile.PointMetaData(1, 3, 3, 3));
        oocut.finishRecentMeasurement(measurement);

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
        oocut.storeLocation(testLocation(), measurement);
        measurement.getMetaFile().append(new MetaFile.PointMetaData(1, 0, 0, 0));
        oocut.finishRecentMeasurement(measurement);
        List<Measurement> measurements = oocut.loadMeasurements();
        assertThat(measurements.size(), is(equalTo(1)));
        assertThat(oocut.loadTrack(measurements.get(0)).size(), is(equalTo(1)));
    }

    @Test
    public void testProvokeAnr() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                if (!oocut.hasMeasurement(Measurement.MeasurementStatus.OPEN)) {
                    oocut.newMeasurement(Vehicle.BICYCLE);
                }
                if (!oocut.hasMeasurement(Measurement.MeasurementStatus.OPEN)) {
                    oocut.newMeasurement(Vehicle.BICYCLE);
                }

                if (oocut.hasMeasurement(Measurement.MeasurementStatus.OPEN)) {
                    try {
                        final Measurement measurement = oocut.loadCurrentlyCapturedMeasurement();
                        if (measurement != null) {
                            oocut.finishRecentMeasurement(measurement);
                        }
                    } catch (final NoSuchMeasurementException e) {
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
