package de.cyface.datacapturing.persistence;

import static de.cyface.datacapturing.TestUtils.AUTHORITY;
import static de.cyface.persistence.model.MeasurementStatus.FINISHED;
import static de.cyface.persistence.model.MeasurementStatus.OPEN;
import static de.cyface.persistence.model.MeasurementStatus.SYNCED;
import static de.cyface.testutils.SharedTestUtils.clearPersistenceLayer;
import static de.cyface.testutils.SharedTestUtils.insertSampleMeasurementWithData;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.ContentResolver;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import de.cyface.persistence.DefaultPersistenceBehaviour;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.PersistenceBehaviour;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.Vehicle;
import de.cyface.persistence.serialization.Point3dFile;
import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;

/**
 * Tests the correct workings of the <code>PersistenceLayer</code> class.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.5.1
 * @since 2.0.3
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class PersistenceLayerTest {

    /**
     * An object of the class under test. It is setup prior to each test execution.
     */
    private PersistenceLayer<CapturingPersistenceBehaviour> oocut;
    /**
     * {@link Context} used to access the persistence layer
     */
    private Context context;
    /**
     * {@link ContentResolver} to access the database.
     */
    private ContentResolver resolver;
    /**
     * This {@link PersistenceBehaviour} is used to capture a {@link Measurement}s with when a {@link PersistenceLayer}.
     */
    private CapturingPersistenceBehaviour capturingBehaviour;

    /**
     * Initializes the <code>oocut</code> with the Android persistence stack.
     */
    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        resolver = context.getContentResolver();
        this.capturingBehaviour = new CapturingPersistenceBehaviour();
        oocut = new PersistenceLayer<>(context, resolver, AUTHORITY, capturingBehaviour);
    }

    /**
     * Deletes all content from the content provider, to leave the next test with a clean test environment.
     */
    @After
    public void tearDown() {
        clearPersistenceLayer(context, resolver, AUTHORITY);
        oocut.shutdown();
    }

    /**
     * Inserts two measurements into the database; one finished and one still running and checks, that the
     * <code>loadFinishedMeasurements</code> method returns a list of size 1.
     *
     * @throws NoSuchMeasurementException When there was no currently captured {@code Measurement}.
     * @throws CursorIsNullException – If ContentProvider was inaccessible
     */
    @Test
    public void testLoadFinishedMeasurements_oneFinishedOneRunning()
            throws NoSuchMeasurementException, CursorIsNullException {
        oocut.newMeasurement(Vehicle.UNKNOWN);
        assertThat(oocut.hasMeasurement(MeasurementStatus.OPEN), is(equalTo(true)));
        capturingBehaviour.updateRecentMeasurement(FINISHED);
        assertThat(oocut.hasMeasurement(MeasurementStatus.OPEN), is(equalTo(false)));
        oocut.newMeasurement(Vehicle.UNKNOWN);
        assertThat(oocut.hasMeasurement(MeasurementStatus.OPEN), is(equalTo(true)));
        assertThat(oocut.loadMeasurements(MeasurementStatus.FINISHED).size(), is(equalTo(1)));
    }

    /**
     * Checks that calling {@link PersistenceLayer#loadMeasurements(MeasurementStatus)} on an empty database
     * returns an empty list.
     *
     * @throws CursorIsNullException – If ContentProvider was inaccessible
     */
    @Test
    public void testLoadFinishedMeasurements_noMeasurements() throws CursorIsNullException {
        assertThat(oocut.loadMeasurements(MeasurementStatus.FINISHED).isEmpty(), is(equalTo(true)));
    }

    /**
     * Test that loading a {@link MeasurementStatus#FINISHED} {@link Measurement} works as expected.
     *
     * We don't create a FINISHED measurement because this will never happen like this in the code.
     * As a consequence we create an {@link MeasurementStatus#OPEN} as it would happen in the code,
     * then finish this measurement and then load it as FINISHED measurement as we usually do to synchronize them.
     *
     * @throws NoSuchMeasurementException – if there was no measurement with the id .
     * @throws CursorIsNullException – If ContentProvider was inaccessible
     */
    @Test
    public void testLoadMeasurementSuccessfully() throws NoSuchMeasurementException, CursorIsNullException {
        final Measurement measurement = oocut.newMeasurement(Vehicle.UNKNOWN);
        Measurement loadedOpenMeasurement = oocut.loadMeasurement(measurement.getIdentifier());
        assertThat(loadedOpenMeasurement, is(equalTo(measurement)));

        capturingBehaviour.updateRecentMeasurement(FINISHED);
        List<Measurement> finishedMeasurements = oocut.loadMeasurements(FINISHED);
        assertThat(finishedMeasurements.size(), is(equalTo(1)));
        assertThat(finishedMeasurements.get(0).getIdentifier(), is(equalTo(measurement.getIdentifier())));
    }

    /**
     * Test that marking a measurement as synced works as expected.
     *
     * @throws NoSuchMeasurementException – if there was no measurement with the id .
     * @throws CursorIsNullException – If ContentProvider was inaccessible
     */
    @Test
    public void testMarkMeasurementAsSynced() throws NoSuchMeasurementException, CursorIsNullException {
        final Measurement measurement = oocut.newMeasurement(Vehicle.UNKNOWN);
        capturingBehaviour.updateRecentMeasurement(FINISHED);
        oocut.markAsSynchronized(measurement);

        // Check that measurement was marked as synced
        List<Measurement> syncedMeasurements = oocut.loadMeasurements(SYNCED);
        assertThat(syncedMeasurements.size(), is(equalTo(1)));
        assertThat(syncedMeasurements.get(0).getIdentifier(), is(equalTo(measurement.getIdentifier())));

        // Check that sensor data was deleted
        final File accelerationFile = oocut.getFileAccessLayer().getFilePath(context, measurement.getIdentifier(),
                Point3dFile.ACCELERATIONS_FOLDER_NAME, Point3dFile.ACCELERATIONS_FILE_EXTENSION);
        Validate.isTrue(!accelerationFile.exists());
        final File rotationFile = oocut.getFileAccessLayer().getFilePath(context, measurement.getIdentifier(),
                Point3dFile.ROTATIONS_FOLDER_NAME, Point3dFile.ROTATION_FILE_EXTENSION);
        Validate.isTrue(!rotationFile.exists());
        final File directionFile = oocut.getFileAccessLayer().getFilePath(context, measurement.getIdentifier(),
                Point3dFile.DIRECTIONS_FOLDER_NAME, Point3dFile.DIRECTION_FILE_EXTENSION);
        Validate.isTrue(!directionFile.exists());
    }

    /**
     * Tests whether the sync adapter loads the correct measurements for synchronization.
     *
     * @throws NoSuchMeasurementException – if there was no measurement with the id .
     * @throws CursorIsNullException – If ContentProvider was inaccessible
     */
    @Test
    public void testGetSyncableMeasurement() throws NoSuchMeasurementException, CursorIsNullException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PersistenceLayer<DefaultPersistenceBehaviour> persistenceLayer = new PersistenceLayer<>(context,
                context.getContentResolver(), AUTHORITY, new DefaultPersistenceBehaviour());

        // Create a synchronized measurement
        insertSampleMeasurementWithData(context, AUTHORITY, SYNCED, persistenceLayer, 1, 1);

        // Create a finished measurement
        Measurement finishedMeasurement = insertSampleMeasurementWithData(context, AUTHORITY, FINISHED,
                persistenceLayer, 1, 1);

        // Create an open measurement - must be created at last (life-cycle checks in PersistenceLayer.setStatus)
        insertSampleMeasurementWithData(context, AUTHORITY, OPEN, persistenceLayer, 1, 1);

        // Check that syncable measurements = finishedMeasurement
        final List<Measurement> loadedMeasurements = persistenceLayer.loadMeasurements(FINISHED);
        assertThat(loadedMeasurements.size(), is(1));
        assertThat(loadedMeasurements.get(0).getIdentifier(), is(equalTo(finishedMeasurement.getIdentifier())));
    }

    /**
     * Test that updating the distance in the {@link PersistenceLayer} during capturing works as expected.
     *
     * @throws NoSuchMeasurementException – if there was no measurement with the id .
     * @throws CursorIsNullException – If ContentProvider was inaccessible
     */
    @Test
    public void testUpdateDistanceDuringCapturing() throws NoSuchMeasurementException, CursorIsNullException {

        // Arrange
        final Measurement measurement = oocut.newMeasurement(Vehicle.UNKNOWN);

        // Act
        oocut.setDistance(measurement.getIdentifier(), 2.0);

        // Assert
        Measurement loadedMeasurement = oocut.loadCurrentlyCapturedMeasurement();
        assertThat(loadedMeasurement.getDistance(), is(equalTo(2.0)));

        // Ensure a second distance update works as well
        oocut.setDistance(measurement.getIdentifier(), 4.0);
        loadedMeasurement = oocut.loadCurrentlyCapturedMeasurement();
        assertThat(loadedMeasurement.getDistance(), is(equalTo(4.0)));
    }
}
