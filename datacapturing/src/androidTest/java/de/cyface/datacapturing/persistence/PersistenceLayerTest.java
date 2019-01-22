package de.cyface.datacapturing.persistence;

import static de.cyface.datacapturing.TestUtils.AUTHORITY;
import static de.cyface.persistence.model.MeasurementStatus.FINISHED;
import static de.cyface.synchronization.TestUtils.clear;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

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
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.PersistenceBehaviour;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.Vehicle;
import de.cyface.utils.DataCapturingException;

/**
 * Tests the correct workings of the <code>PersistenceLayer</code> class.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.4.0
 * @since 2.0.3
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class PersistenceLayerTest {

    /**
     * An object of the class under test. It is setup prior to each test execution.
     */
    private PersistenceLayer oocut;
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
        oocut = new PersistenceLayer(context, resolver, AUTHORITY, capturingBehaviour);
    }

    /**
     * Deletes all content from the content provider, to leave the next test with a clean test environment.
     */
    @After
    public void tearDown() {
        clear(context, resolver, AUTHORITY);
        oocut.shutdown();
    }

    /**
     * Inserts two measurements into the database; one finished and one still running and checks, that the
     * <code>loadFinishedMeasurements</code> method returns a list of size 1.
     *
     * @throws NoSuchMeasurementException Fails the test if anything unexpected happens.
     */
    @Test
    public void testLoadFinishedMeasurements_oneFinishedOneRunning()
            throws NoSuchMeasurementException, DataCapturingException {
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
     */
    @Test
    public void testLoadFinishedMeasurements_noMeasurements() throws DataCapturingException {
        assertThat(oocut.loadMeasurements(MeasurementStatus.FINISHED).isEmpty(), is(equalTo(true)));
    }

    /**
     * Test that loading an open and a closed measurement works as expected.
     *
     * @throws DataCapturingException Fails the test if anything unexpected happens.
     */
    @Test
    public void testLoadMeasurementSuccessfully() throws DataCapturingException, NoSuchMeasurementException {
        final Measurement measurement = oocut.newMeasurement(Vehicle.UNKNOWN);
        Measurement loadedOpenMeasurement = oocut.loadMeasurement(measurement.getIdentifier());
        assertThat(loadedOpenMeasurement, is(equalTo(measurement)));

        capturingBehaviour.updateRecentMeasurement(FINISHED);
        List<Measurement> finishedMeasurements = oocut.loadMeasurements(FINISHED);
        assertThat(finishedMeasurements.size(), is(equalTo(1)));
        assertThat(finishedMeasurements.get(0).getIdentifier(), is(equalTo(measurement.getIdentifier())));
    }
}
