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
import android.net.Uri;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.test.ProviderTestCase2;

/**
 * Tests that CRUD operations on measurements and the measurements table are working correctly.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
@RunWith(AndroidJUnit4.class)
public class MeasurementTest extends ProviderTestCase2<MeasuringPointsContentProvider> {
    /**
     * A <code>ContentValues</code> object as prototype to create measurements in the database.
     */
    private ContentValues fixtureMeasurement;
    /**
     * The <code>Uri</code> to use when accessing the measurements table.
     */
    Uri contentUri = MeasuringPointsContentProvider.MEASUREMENT_URI;

    /**
     * Constructor required by <code>ProviderTestCase2</code>.
     */
    public MeasurementTest() {
        super(MeasuringPointsContentProvider.class, BuildConfig.provider);
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
        long identifier = TestUtils.create(getMockContentResolver(), contentUri, fixtureMeasurement);

        ContentValues fixtureGpsPoint = geoLocationContentValues(identifier);

        ContentValues fixtureAcceleration = accelerationContentValues(identifier);

        ContentValues fixtureRotation = rotationContentValues(identifier);

        ContentValues fixtureDirection = directionContentValues(identifier);

        TestUtils.create(getMockContentResolver(), MeasuringPointsContentProvider.GPS_POINTS_URI, fixtureGpsPoint);
        TestUtils.create(getMockContentResolver(), MeasuringPointsContentProvider.SAMPLE_POINTS_URI,
                fixtureAcceleration);
        TestUtils.create(getMockContentResolver(), MeasuringPointsContentProvider.ROTATION_POINTS_URI, fixtureRotation);
        TestUtils.create(getMockContentResolver(), MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI,
                fixtureDirection);

        Cursor measurementCursor = null;
        try {
            measurementCursor = getMockContentResolver().query(contentUri, null,
                    MeasurementTable.COLUMN_FINISHED + "=?", new String[] {Long.valueOf(0).toString()}, null);
            assertThat(measurementCursor.getCount() > 0, is(equalTo(true)));
        } finally {
            if (measurementCursor != null) {
                measurementCursor.close();
            }
        }

        final int rowsDeleted = getMockContentResolver().delete(contentUri, null, null);
        assertEquals("Delete was unsuccessful for uri " + contentUri, 5, rowsDeleted);
        assertThat(TestUtils.count(getMockContentResolver(), MeasuringPointsContentProvider.GPS_POINTS_URI), is(0));
        assertThat(TestUtils.count(getMockContentResolver(), MeasuringPointsContentProvider.SAMPLE_POINTS_URI), is(0));
        assertThat(TestUtils.count(getMockContentResolver(), MeasuringPointsContentProvider.ROTATION_POINTS_URI),
                is(0));
        assertThat(TestUtils.count(getMockContentResolver(), MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI),
                is(0));
    }

    /**
     * Tests whether it is possible to delete multiple measurements and their corresponding data in one go.
     */
    @Test
    public void cascadingDeleteMeasurements() {
        for (int i = 0; i < 2; i++) {
            long identifier = TestUtils.create(getMockContentResolver(), contentUri, fixtureMeasurement);

            ContentValues fixtureGpsPoint = geoLocationContentValues(identifier);

            ContentValues fixtureAcceleration = accelerationContentValues(identifier);

            ContentValues fixtureRotation = rotationContentValues(identifier);

            ContentValues fixtureDirection = directionContentValues(identifier);

            TestUtils.create(getMockContentResolver(), MeasuringPointsContentProvider.GPS_POINTS_URI, fixtureGpsPoint);
            TestUtils.create(getMockContentResolver(), MeasuringPointsContentProvider.SAMPLE_POINTS_URI,
                    fixtureAcceleration);
            TestUtils.create(getMockContentResolver(), MeasuringPointsContentProvider.ROTATION_POINTS_URI,
                    fixtureRotation);
            TestUtils.create(getMockContentResolver(), MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI,
                    fixtureDirection);
        }

        final int rowsDeleted = getMockContentResolver().delete(MeasuringPointsContentProvider.MEASUREMENT_URI, null,
                null);
        assertEquals("Delete was unsuccessful for uri " + MeasuringPointsContentProvider.MEASUREMENT_URI, 10,
                rowsDeleted);
    }

    /**
     * Provides a set of <code>ContentValues</code> to create a row in the table for geo locations.
     *
     * @param measurementIdentifier The identifier of the measurement the new geo location should reference.
     * @return The filled out <code>ContentValues</code>.
     */
    private ContentValues geoLocationContentValues(final long measurementIdentifier) {
        ContentValues ret = new ContentValues();
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
        ContentValues ret = new ContentValues();
        ret.put(SamplePointTable.COLUMN_TIME, 10000L);
        ret.put(SamplePointTable.COLUMN_IS_SYNCED, false);
        ret.put(SamplePointTable.COLUMN_AX, 1.0);
        ret.put(SamplePointTable.COLUMN_AY, 1.0);
        ret.put(SamplePointTable.COLUMN_AZ, 1.0);
        ret.put(SamplePointTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
        return ret;
    }

    /**
     * Provides a set of <code>ContentValues</code> to create a row in the table for rotations.
     *
     * @param measurementIdentifier The identifier of the measurement the new rotation should reference.
     * @return The filled out <code>ContentValues</code>.
     */
    private ContentValues rotationContentValues(final long measurementIdentifier) {
        ContentValues ret = new ContentValues();
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
        ContentValues ret = new ContentValues();
        ret.put(MagneticValuePointTable.COLUMN_TIME, 10000L);
        ret.put(MagneticValuePointTable.COLUMN_IS_SYNCED, false);
        ret.put(MagneticValuePointTable.COLUMN_MX, 1.0);
        ret.put(MagneticValuePointTable.COLUMN_MY, 1.0);
        ret.put(MagneticValuePointTable.COLUMN_MZ, 1.0);
        ret.put(MagneticValuePointTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
        return ret;
    }

    /**
     * Shuts down the test and makes sure the database is empty for the next test.
     *
     * @throws Exception See <code>ContentProviderClient2#tearDown()</code>.
     */
    @After
    public void tearDown() throws Exception {
        getMockContentResolver().delete(MeasuringPointsContentProvider.GPS_POINTS_URI, null, null);
        getMockContentResolver().delete(MeasuringPointsContentProvider.SAMPLE_POINTS_URI, null, null);
        getMockContentResolver().delete(MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI, null, null);
        getMockContentResolver().delete(MeasuringPointsContentProvider.MEASUREMENT_URI, null, null);
        getMockContentResolver().delete(MeasuringPointsContentProvider.ROTATION_POINTS_URI, null, null);
        super.tearDown();
        getProvider().shutdown();
    }
}
