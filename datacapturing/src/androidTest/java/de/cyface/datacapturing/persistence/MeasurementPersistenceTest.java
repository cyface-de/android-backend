package de.cyface.datacapturing.persistence;

import static de.cyface.datacapturing.ServiceTestUtils.AUTHORITY;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.ContentResolver;
import android.content.Context;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import android.util.Log;

import de.cyface.datacapturing.ServiceTestUtils;
import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.Vehicle;
import de.cyface.persistence.serialization.GeoLocationsFile;
import de.cyface.persistence.serialization.MetaFile;

/**
 * Tests the correct workings of the <code>MeasurementPersistence</code> class.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.0.3
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
     * Creates two measurements: one finished and one still running and checks, that the
     * <code>loadFinishedMeasurements</code> method returns a list of size 1.
     *
     * @throws DataCapturingException Fails the test if anything unexpected happens.
     */
    @Test
    public void testLoadFinishedMeasurements_oneFinishedOneRunning() throws DataCapturingException {
        oocut.newMeasurement(Vehicle.UNKNOWN);
        assertThat(oocut.hasOpenMeasurement(), is(equalTo(true)));

        try {
            oocut.closeRecentMeasurement();
        } catch (NoSuchMeasurementException e) {
            throw new IllegalStateException(e);
        }
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

        try {
            oocut.closeRecentMeasurement();
        } catch (NoSuchMeasurementException e) {
            throw new IllegalStateException(e);
        }
        Measurement loadedClosedMeasurement = oocut.loadMeasurement(measurement.getIdentifier());
        assertThat(loadedClosedMeasurement, is(equalTo(measurement)));
    }

    /**
     * This test makes sure that larger GeoLocation tracks can be loaded completely form the
     * database as there was a bug which limited the query size to 10_000 entries #MOV-248.
     */
    @Test
    public void testLoadGeoLocations_10hTrack() throws DataCapturingException, NoSuchMeasurementException {
        // The Location frequency is always 1 Hz, i.e. 10h of measurement:
        testLoadGeoLocations(3600 * 10);
    }

    @Ignore
    public void testLoadGeoLocations(int numberOftestEntries)
            throws DataCapturingException, NoSuchMeasurementException {
        // Arrange
        Context context = InstrumentationRegistry.getTargetContext();

        // Act: Store and load the test entries
        Measurement measurement = oocut.newMeasurement(Vehicle.UNKNOWN);
        GeoLocation geoLocation = new GeoLocation(1.0, 1.0, 1L, 1.0, 1);
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < numberOftestEntries; i++) {
            oocut.storeLocation(geoLocation, measurement.getIdentifier());
        }
        Log.i(ServiceTestUtils.TAG, "Inserting " + numberOftestEntries + " entries took: "
                + (System.currentTimeMillis() - startTime) + " ms");

        MetaFile.append(context, measurement.getIdentifier(), new MetaFile.PointMetaData(numberOftestEntries, 0, 0, 0));
        oocut.closeRecentMeasurement();

        // Load entries again
        startTime = System.currentTimeMillis();
        final List<GeoLocation> locationList = GeoLocationsFile.deserialize(context, measurement.getIdentifier());
        Log.i(ServiceTestUtils.TAG, "Loading " + locationList.size() + " entries  took: "
                + (System.currentTimeMillis() - startTime) + " ms");

        // Assert
        assertThat(numberOftestEntries, is(equalTo(locationList.size())));
    }
}
