package de.cyface.datacapturing.persistence;

import static de.cyface.datacapturing.ServiceTestUtils.AUTHORITY;
import static de.cyface.persistence.model.MeasurementStatus.FINISHED;
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
import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import de.cyface.utils.DataCapturingException;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.Vehicle;

/**
 * Tests the correct workings of the <code>MeasurementPersistence</code> class.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.0
 * @since 2.0.3
 *
 *        FIXME: pull selected PersitenceTest changes from declined FILES branch/PR into this branch
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class MeasurementPersistenceTest {

    /**
     * An object of the class under test. It is setup prior to each test execution.
     */
    private MeasurementPersistence oocut;

    /**
     * Initializes the <code>oocut</code> with the Android persistence stack.
     */
    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getTargetContext();
        ContentResolver resolver = context.getContentResolver();
        oocut = new MeasurementPersistence(context, resolver, AUTHORITY);
    }

    /**
     * Deletes all content from the content provider, to leave the next test with a clean test environment.
     */
    @After
    public void tearDown() {
        oocut.clear();
        oocut.shutdown();
    }

    /**
     * Inserts two measurements into the database; one finished and one still running and checks, that the
     * <code>loadFinishedMeasurements</code> method returns a list of size 1.
     *
     * @throws NoSuchMeasurementException Fails the test if anything unexpected happens.
     */
    @Test
    public void testLoadFinishedMeasurements_oneFinishedOneRunning() throws NoSuchMeasurementException {
        oocut.newMeasurement(Vehicle.UNKNOWN);
        assertThat(oocut.hasMeasurement(MeasurementStatus.OPEN), is(equalTo(true)));
        oocut.updateRecentMeasurement(FINISHED);
        assertThat(oocut.hasMeasurement(MeasurementStatus.OPEN), is(equalTo(false)));
        oocut.newMeasurement(Vehicle.UNKNOWN);
        assertThat(oocut.hasMeasurement(MeasurementStatus.OPEN), is(equalTo(true)));
        assertThat(oocut.loadMeasurements(MeasurementStatus.FINISHED).size(), is(equalTo(1)));
    }

    /**
     * Checks that calling {@link MeasurementPersistence#loadMeasurements(MeasurementStatus)} on an empty database
     * returns an empty list.
     */
    @Test
    public void testLoadFinishedMeasurements_noMeasurements() {
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

        oocut.updateRecentMeasurement(FINISHED);
        List<Measurement> finishedMeasurements = oocut.loadMeasurements(FINISHED);
        assertThat(finishedMeasurements.size(), is(equalTo(1)));
        assertThat(finishedMeasurements.get(0).getIdentifier(), is(equalTo(measurement.getIdentifier())));
    }
}
