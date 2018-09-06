package de.cyface.persistence;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.ContentValues;
import android.database.Cursor;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.test.ProviderTestCase2;

/**
 * Tests that CRUD operations on measurements and the measurements table are working correctly.
 *
 * @author Klemens Muthmann
 * @version 1.0.1
 * @since 1.0.0
 */
@RunWith(AndroidJUnit4.class)
public class MeasurementTest extends ProviderTestCase2<MeasuringPointsContentProvider> {
    /**
     * A <code>ContentValues</code> object as prototype to create measurements in the database.
     */
    private ContentValues fixtureMeasurement;

    /**
     * Constructor required by <code>ProviderTestCase2</code>.
     */
    public MeasurementTest() {
        super(MeasuringPointsContentProvider.class, TestUtils.AUTHORITY);
    }

    /**
     * Initializes the <code>ProviderTestCase2</code> as well as the fixture measurement.
     *
     * @throws Exception
     */
    @Before
    public void setUp() throws Exception {
        setContext(InstrumentationRegistry.getTargetContext());
        super.setUp();
        fixtureMeasurement = new ContentValues();
        fixtureMeasurement.put(MeasurementTable.COLUMN_FINISHED, false);
        fixtureMeasurement.put(MeasurementTable.COLUMN_VEHICLE, "BICYCLE");
    }

    /**
     * Tests whether it is possible to delete one measurement and all corresponding data in one go.
     */
    @Test
    public void testCascadingDeleteOneMeasurement() {
        final long identifier = TestUtils.create(getMockContentResolver(), TestUtils.getMeasurementUri(),
                fixtureMeasurement);

        final ContentValues fixtureGpsPoint = geoLocationContentValues(identifier);

        final ContentValues fixtureAcceleration = accelerationContentValues(identifier);

        final ContentValues fixtureRotation = rotationContentValues(identifier);

        final ContentValues fixtureDirection = directionContentValues(identifier);

        TestUtils.create(getMockContentResolver(), TestUtils.getGeoLocationsUri(), fixtureGpsPoint);
        TestUtils.create(getMockContentResolver(), TestUtils.getAccelerationsUri(), fixtureAcceleration);
        TestUtils.create(getMockContentResolver(), TestUtils.getRotationsUri(), fixtureRotation);
        TestUtils.create(getMockContentResolver(), TestUtils.getDirectionsUri(), fixtureDirection);

        Cursor measurementCursor = null;
        try {
            measurementCursor = getMockContentResolver().query(TestUtils.getMeasurementUri(), null,
                    MeasurementTable.COLUMN_FINISHED + "=?", new String[] {Long.valueOf(0).toString()}, null);
            assertThat(measurementCursor.getCount() > 0, is(equalTo(true)));
        } finally {
            if (measurementCursor != null) {
                measurementCursor.close();
            }
        }

        final int rowsDeleted = getMockContentResolver().delete(TestUtils.getMeasurementUri(), null, null);
        assertEquals("Delete was unsuccessful for uri " + TestUtils.getMeasurementUri(), 5, rowsDeleted);
        assertThat(TestUtils.count(getMockContentResolver(), TestUtils.getGeoLocationsUri()), is(0));
        assertThat(TestUtils.count(getMockContentResolver(), TestUtils.getAccelerationsUri()), is(0));
        assertThat(TestUtils.count(getMockContentResolver(), TestUtils.getRotationsUri()), is(0));
        assertThat(TestUtils.count(getMockContentResolver(), TestUtils.getDirectionsUri()), is(0));
    }

    /**
     * Tests whether it is possible to delete multiple measurements and their corresponding data in one go.
     */
    @Test
    public void cascadingDeleteMeasurements() {
        for (int i = 0; i < 2; i++) {
            final long identifier = TestUtils.create(getMockContentResolver(), TestUtils.getMeasurementUri(),
                    fixtureMeasurement);

            final ContentValues fixtureGpsPoint = geoLocationContentValues(identifier);

            final ContentValues fixtureAcceleration = accelerationContentValues(identifier);

            final ContentValues fixtureRotation = rotationContentValues(identifier);

            final ContentValues fixtureDirection = directionContentValues(identifier);

            TestUtils.create(getMockContentResolver(), TestUtils.getGeoLocationsUri(), fixtureGpsPoint);
            TestUtils.create(getMockContentResolver(), TestUtils.getAccelerationsUri(), fixtureAcceleration);
            TestUtils.create(getMockContentResolver(), TestUtils.getRotationsUri(), fixtureRotation);
            TestUtils.create(getMockContentResolver(), TestUtils.getDirectionsUri(), fixtureDirection);
        }

        final int rowsDeleted = getMockContentResolver().delete(TestUtils.getMeasurementUri(), null, null);
        assertEquals("Delete was unsuccessful for uri " + TestUtils.getMeasurementUri(), 10, rowsDeleted);
    }

    /**
     * Provides a set of <code>ContentValues</code> to create a row in the table for geo locations.
     *
     * @param measurementIdentifier The identifier of the measurement the new geo location should reference.
     * @return The filled out <code>ContentValues</code>.
     */
    private ContentValues geoLocationContentValues(final long measurementIdentifier) {
        final ContentValues ret = new ContentValues();
        ret.put(GpsPointsTable.COLUMN_GPS_TIME, 10000L);
        ret.put(GpsPointsTable.COLUMN_IS_SYNCED, false);
        ret.put(GpsPointsTable.COLUMN_LAT, 13.0);
        ret.put(GpsPointsTable.COLUMN_LON, 51.0);
        ret.put(GpsPointsTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
        ret.put(GpsPointsTable.COLUMN_SPEED, 1.0);
        ret.put(GpsPointsTable.COLUMN_ACCURACY, 300);
        return ret;
    }

    /**
     * Provides a set of <code>ContentValues</code> to create a row in the table for accelerations.
     *
     * @param measurementIdentifier The identifier of the measurement the new acceleration should reference.
     * @return The filled out <code>ContentValues</code>.
     */
    private ContentValues accelerationContentValues(final long measurementIdentifier) {
        final ContentValues ret = new ContentValues();
        ret.put(AccelerationPointTable.COLUMN_TIME, 10000L);
        ret.put(AccelerationPointTable.COLUMN_IS_SYNCED, false);
        ret.put(AccelerationPointTable.COLUMN_AX, 1.0);
        ret.put(AccelerationPointTable.COLUMN_AY, 1.0);
        ret.put(AccelerationPointTable.COLUMN_AZ, 1.0);
        ret.put(AccelerationPointTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
        return ret;
    }

    /**
     * Provides a set of <code>ContentValues</code> to create a row in the table for rotations.
     *
     * @param measurementIdentifier The identifier of the measurement the new rotation should reference.
     * @return The filled out <code>ContentValues</code>.
     */
    private ContentValues rotationContentValues(final long measurementIdentifier) {
        final ContentValues ret = new ContentValues();
        ret.put(RotationPointTable.COLUMN_TIME, 10000L);
        ret.put(RotationPointTable.COLUMN_IS_SYNCED, false);
        ret.put(RotationPointTable.COLUMN_RX, 1.0);
        ret.put(RotationPointTable.COLUMN_RY, 1.0);
        ret.put(RotationPointTable.COLUMN_RZ, 1.0);
        ret.put(RotationPointTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
        return ret;
    }

    /**
     * Provides a set of <code>ContentValues</code> to create a row in the table for directions.
     *
     * @param measurementIdentifier The identifier of the measurement the new direction should reference.
     * @return The filled out <code>ContentValues</code>.
     */
    private ContentValues directionContentValues(final long measurementIdentifier) {
        final ContentValues ret = new ContentValues();
        ret.put(DirectionPointTable.COLUMN_TIME, 10000L);
        ret.put(DirectionPointTable.COLUMN_IS_SYNCED, false);
        ret.put(DirectionPointTable.COLUMN_MX, 1.0);
        ret.put(DirectionPointTable.COLUMN_MY, 1.0);
        ret.put(DirectionPointTable.COLUMN_MZ, 1.0);
        ret.put(DirectionPointTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
        return ret;
    }

    /**
     * Shuts down the test and makes sure the database is empty for the next test.
     *
     * @throws Exception See <code>ContentProviderClient2#tearDown()</code>.
     */
    @After
    public void tearDown() throws Exception {
        getMockContentResolver().delete(TestUtils.getGeoLocationsUri(), null, null);
        getMockContentResolver().delete(TestUtils.getAccelerationsUri(), null, null);
        getMockContentResolver().delete(TestUtils.getDirectionsUri(), null, null);
        getMockContentResolver().delete(TestUtils.getRotationsUri(), null, null);
        getMockContentResolver().delete(TestUtils.getMeasurementUri(), null, null);
        super.tearDown();
        getProvider().shutdown();
    }
}
