package de.cyface.datacapturing.persistence;

import static de.cyface.datacapturing.ServiceTestUtils.AUTHORITY;
import static de.cyface.persistence.serialization.MeasurementSerializer.BYTES_IN_HEADER;
import static de.cyface.persistence.serialization.MeasurementSerializer.BYTES_IN_ONE_GEO_LOCATION_ENTRY;
import static de.cyface.persistence.serialization.MeasurementSerializer.BYTES_IN_ONE_POINT_ENTRY;
import static de.cyface.testutils.SharedTestUtils.insertSampleMeasurement;
import static de.cyface.testutils.SharedTestUtils.insertTestAcceleration;
import static de.cyface.testutils.SharedTestUtils.insertTestDirection;
import static de.cyface.testutils.SharedTestUtils.insertTestGeoLocation;
import static de.cyface.testutils.SharedTestUtils.insertTestMeasurement;
import static de.cyface.testutils.SharedTestUtils.insertTestRotation;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.Context;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import de.cyface.datacapturing.ServiceTestUtils;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.Persistence;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.Vehicle;
import de.cyface.persistence.serialization.FileCorruptedException;
import de.cyface.persistence.serialization.GeoLocationsFile;
import de.cyface.persistence.serialization.MetaFile;

/**
 * Tests the correct workings of the <code>MeasurementPersistence</code> class.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.3.0
 * @since 2.0.3
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class PersistenceTest {

    /**
     * An object of the class under test. It is setup prior to each test execution.
     */
    private MeasurementPersistence oocut;
    /**
     * {@link Context} used to access the persistence layer
     */
    private Context context;

    /**
     * Initializes the <code>oocut</code> with the Android persistence stack.
     */
    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        oocut = new MeasurementPersistence(context, AUTHORITY);
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
     */
    @Test
    public void testLoadFinishedMeasurements_oneFinishedOneRunning() {
        oocut.newMeasurement(Vehicle.UNKNOWN);
        assertThat(oocut.hasMeasurement(Measurement.MeasurementStatus.OPEN), is(equalTo(true)));

        try {
            oocut.closeRecentMeasurement();
        } catch (NoSuchMeasurementException e) {
            throw new IllegalStateException(e);
        }
        assertThat(oocut.hasMeasurement(Measurement.MeasurementStatus.OPEN), is(equalTo(false)));

        oocut.newMeasurement(Vehicle.UNKNOWN);
        assertThat(oocut.hasMeasurement(Measurement.MeasurementStatus.OPEN), is(equalTo(true)));
        assertThat(oocut.loadMeasurements(Measurement.MeasurementStatus.FINISHED).size(), is(equalTo(1)));
    }

    /**
     * Checks that calling <code>loadFinishedMeasurements</code> on an empty database returns an empty list.
     */
    @Test
    public void testLoadFinishedMeasurements_noMeasurements() {
        assertThat(oocut.loadMeasurements(Measurement.MeasurementStatus.FINISHED).isEmpty(), is(equalTo(true)));
    }

    /**
     * Test that loading an open and a closed measurement works as expected.
     *
     */
    @Test
    public void testLoadMeasurementSuccessfully() {
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
    public void testLoadGeoLocations_10hTrack() throws NoSuchMeasurementException {
        // The Location frequency is always 1 Hz, i.e. 10h of measurement:
        testLoadGeoLocations(3600 * 10);
    }

    @Ignore
    public void testLoadGeoLocations(int numberOftestEntries) throws NoSuchMeasurementException {
        // Arrange

        // Act: Store and load the test entries
        Measurement measurement = oocut.newMeasurement(Vehicle.UNKNOWN);
        GeoLocation geoLocation = new GeoLocation(1.0, 1.0, 1L, 1.0, 1);
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < numberOftestEntries; i++) {
            oocut.storeLocation(geoLocation, measurement);
        }
        Log.i(ServiceTestUtils.TAG, "Inserting " + numberOftestEntries + " entries took: "
                + (System.currentTimeMillis() - startTime) + " ms");

        measurement.getMetaFile().append(new MetaFile.PointMetaData(numberOftestEntries, 0, 0, 0));
        oocut.closeRecentMeasurement();

        // Load entries again
        startTime = System.currentTimeMillis();
        final List<GeoLocation> locationList = GeoLocationsFile.loadFile(measurement).deserialize();
        Log.i(ServiceTestUtils.TAG, "Loading " + locationList.size() + " entries  took: "
                + (System.currentTimeMillis() - startTime) + " ms");

        // Assert
        assertThat(numberOftestEntries, is(equalTo(locationList.size())));
    }

    /**
     * Tests that the serialization and compression results into bytes of the expected length.
     * Also decompresses the compressed bytes to make sure it's still readable.
     */
    @Test
    public void testLoadSerializedCompressedAndDecompressDeserialize()
            throws NoSuchMeasurementException, FileCorruptedException, IOException, DataFormatException {

        final int SERIALIZED_SIZE = BYTES_IN_HEADER + 3 * BYTES_IN_ONE_GEO_LOCATION_ENTRY
                + 3 * 3 * BYTES_IN_ONE_POINT_ENTRY;
        // Before the epic #CY-4067 the compression resulted into 30 bytes - did the compression change?
        final int SERIALIZED_COMPRESSED_SIZE = 31;

        // Serialize and check length
        Measurement measurement = insertSerializationTestSample();
        byte[] serializedData = oocut.loadSerialized(measurement);
        assertThat(serializedData.length, is(equalTo(SERIALIZED_SIZE)));

        // Serialize + compress and check length
        measurement = insertSerializationTestSample();
        InputStream compressedStream = oocut.loadSerializedCompressed(measurement);
        assertThat(compressedStream.available(), is(equalTo(SERIALIZED_COMPRESSED_SIZE)));

        // Decompress the compressed bytes and check length and bytes
        byte[] compressedBytes = new byte[compressedStream.available()];
        DataInputStream dis = new DataInputStream(compressedStream);
        dis.readFully(compressedBytes);
        Inflater inflater = new Inflater();
        inflater.setInput(compressedBytes, 0, compressedBytes.length);
        byte[] decompressedBytes = new byte[1000];
        int decompressedLength = inflater.inflate(decompressedBytes);
        inflater.end();
        assertThat(decompressedLength, is(equalTo(SERIALIZED_SIZE)));
        assertThat(Arrays.copyOfRange(decompressedBytes, 0, SERIALIZED_SIZE), is(equalTo(serializedData)));
    }

    private Measurement insertSerializationTestSample() throws NoSuchMeasurementException {
        // Insert sample measurement data
        Persistence persistence = new Persistence(context, AUTHORITY);
        final Measurement measurement = insertTestMeasurement(persistence, Vehicle.UNKNOWN);
        insertTestGeoLocation(measurement, 1L, 1.0, 1.0, 1.0, 1);
        insertTestGeoLocation(measurement, 1L, 1.0, 1.0, 1.0, 1);
        insertTestGeoLocation(measurement, 1L, 1.0, 1.0, 1.0, 1);
        insertTestAcceleration(measurement, 1L, 1.0, 1.0, 1.0);
        insertTestAcceleration(measurement, 1L, 1.0, 1.0, 1.0);
        insertTestAcceleration(measurement, 1L, 1.0, 1.0, 1.0);
        insertTestRotation(measurement, 1L, 1.0, 1.0, 1.0);
        insertTestRotation(measurement, 1L, 1.0, 1.0, 1.0);
        insertTestRotation(measurement, 1L, 1.0, 1.0, 1.0);
        insertTestDirection(measurement, 1L, 1.0, 1.0, 1.0);
        insertTestDirection(measurement, 1L, 1.0, 1.0, 1.0);
        insertTestDirection(measurement, 1L, 1.0, 1.0, 1.0);
        // Write point counters to MetaFile
        measurement.getMetaFile().append(new MetaFile.PointMetaData(3, 3, 3, 3));
        // Finish measurement
        persistence.closeMeasurement(measurement);
        // Assert that data is in the database
        final Measurement finishedMeasurement = persistence.loadMeasurement(measurement.getIdentifier(),
                Measurement.MeasurementStatus.FINISHED);
        assertThat(finishedMeasurement, notNullValue());
        List<GeoLocation> geoLocations = persistence.loadTrack(finishedMeasurement);
        assertThat(geoLocations.size(), is(3));
        return measurement;
    }

    /**
     * Tests whether the sync adapter loads the correct measurements for synchronization.
     *
     */
    @Test
    public void testGetSyncableMeasurement() throws NoSuchMeasurementException {
        // Create a not finished measurement
        insertSampleMeasurement(false, false, oocut);

        // Create a not synced finished measurement
        Measurement finishedMeasurement = insertSampleMeasurement(true, false, oocut);

        // Create a synchronized measurement
        insertSampleMeasurement(true, true, oocut);

        final List<Measurement> loadedMeasurements = oocut.loadMeasurements(Measurement.MeasurementStatus.FINISHED);

        assertThat(loadedMeasurements.size(), is(1));
        assertThat(loadedMeasurements.get(0).getIdentifier(), is(equalTo(finishedMeasurement.getIdentifier())));

    }
}
