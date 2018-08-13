package de.cyface.datacapturing.persistence;

import android.content.ContentResolver;
import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.model.Vehicle;

import static de.cyface.datacapturing.ServiceTestUtils.AUTHORITY;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Tests the correct workings of the <code>MeasurementPersistence</code> class.
 *
 * @author Klemens Muthmann
 * @version 1.0.1
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
        oocut.newMeasurement(Vehicle.UNKOWN);
        assertThat(oocut.hasOpenMeasurement(), is(equalTo(true)));
        oocut.closeRecentMeasurement();
        assertThat(oocut.hasOpenMeasurement(), is(equalTo(false)));
        oocut.newMeasurement(Vehicle.UNKOWN);
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
}
