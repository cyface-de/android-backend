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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class MeasurementPersistenceTest {

    private MeasurementPersistence oocut;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getTargetContext();
        ContentResolver resolver = context.getContentResolver();
        oocut = new MeasurementPersistence(resolver);
    }

    @After
    public void tearDown() {
        oocut.clear();
        oocut.shutdown();
    }

    @Test
    public void testLoadFinishedMeasurements_oneFinishedOneRunning() throws DataCapturingException {
        oocut.newMeasurement(Vehicle.UNKOWN);
        assertThat(oocut.hasOpenMeasurement(), is(equalTo(true)));
        oocut.closeRecentMeasurement();
        assertThat(oocut.hasOpenMeasurement(), is(equalTo(false)));
        oocut.newMeasurement(Vehicle.UNKOWN);
        assertThat(oocut.hasOpenMeasurement(), is(equalTo(true)));
        assertThat(oocut.loadFinishedMeasurements().size(),is(equalTo(1)));
    }

    @Test
    public void testLoadFinishedMeasurements_noMeasurements() {
        assertThat(oocut.loadFinishedMeasurements().isEmpty(),is(equalTo(true)));
    }
}
