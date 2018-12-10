package de.cyface.datacapturing.persistence;

import static de.cyface.datacapturing.ServiceTestUtils.AUTHORITY;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.ContentResolver;
import android.content.Context;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import de.cyface.persistence.model.Measurement;
import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.persistence.model.Vehicle;

/**
 * Tests the correct workings of the <code>MeasurementPersistence</code> class.
 *
 * @author Klemens Muthmann
 * @version 1.0.2
 * @since 2.0.3
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
        oocut = new MeasurementPersistence(resolver, AUTHORITY);
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
     * @throws DataCapturingException Fails the test if anything unexpected happens.
     */
    @Test
    public void testLoadFinishedMeasurements_oneFinishedOneRunning() throws DataCapturingException {
        oocut.newMeasurement(Vehicle.UNKNOWN);
        assertThat(oocut.hasOpenMeasurement(), is(equalTo(true)));
        oocut.closeRecentMeasurement();
        assertThat(oocut.hasOpenMeasurement(), is(equalTo(false)));
        oocut.newMeasurement(Vehicle.UNKNOWN);
        assertThat(oocut.hasOpenMeasurement(), is(equalTo(true)));
        assertThat(oocut.loadFinishedMeasurements().size(), is(equalTo(1)));
    }

    /**
     * Checks that calling <code>loadFinishedMeasurements</code> on an empty database returns an empty list.
     */
    @Test
    public void testLoadFinishedMeasurements_noMeasurements() {
        assertThat(oocut.loadFinishedMeasurements().isEmpty(), is(equalTo(true)));
    }

    /**
     * Test that loading an open and a closed measurement works as expected.
     *
     * @throws DataCapturingException Fails the test if anything unexpected happens.
     */
    @Test
    public void testLoadMeasurementSuccessfully() throws DataCapturingException {
        final Measurement measurement = oocut.newMeasurement(Vehicle.UNKNOWN);
        Measurement loadedOpenMeasurement = oocut.loadMeasurement(measurement.getIdentifier());
        assertThat(loadedOpenMeasurement, is(equalTo(measurement)));

        assertThat(oocut.closeRecentMeasurement(), is(equalTo(1)));
        Measurement loadedClosedMeasurement = oocut.loadMeasurement(measurement.getIdentifier());
        assertThat(loadedClosedMeasurement, is(equalTo(measurement)));
    }
}
