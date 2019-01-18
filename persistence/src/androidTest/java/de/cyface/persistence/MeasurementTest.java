package de.cyface.persistence;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.provider.ProviderTestRule;

import static de.cyface.persistence.TestUtils.AUTHORITY;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Tests that CRUD operations on measurements and the measurements table are working correctly.
 *
 * @author Klemens Muthmann
 * @version 1.0.2
 * @since 1.0.0
 */
@RunWith(AndroidJUnit4.class)
public class MeasurementTest {
    /**
     * Test rule that provides a mock connection to a <code>ContentProvider</code> to test against.
     */
    @Rule
    public ProviderTestRule providerRule = new ProviderTestRule.Builder(MeasuringPointsContentProvider.class, AUTHORITY)
            .build();
    /**
     * A <code>ContentValues</code> object as prototype to create measurements in the database.
     */
    private ContentValues fixtureMeasurement;
    /**
     * A mock resolver for accessing the mocked content provider.
     */
    private ContentResolver mockContentResolver;

    /**
     * Initializes the <code>ProviderTestCase2</code> as well as the fixture measurement.
     */
    @Before
    public void setUp() {
        fixtureMeasurement = new ContentValues();
        fixtureMeasurement.put(MeasurementTable.COLUMN_FINISHED, false);
        fixtureMeasurement.put(MeasurementTable.COLUMN_VEHICLE, "BICYCLE");

        mockContentResolver = providerRule.getResolver();
    }

    /**
     * Tests whether it is possible to delete one measurement and all corresponding data in one go.
     */
    @Test
    public void testCascadingDeleteOneMeasurement() {
        final long identifier = TestUtils.create(mockContentResolver, TestUtils.getMeasurementUri(),
                fixtureMeasurement);

        final ContentValues fixtureGpsPoint = geoLocationContentValues(identifier);

        final ContentValues fixtureAcceleration = accelerationContentValues(identifier);

        final ContentValues fixtureRotation = rotationContentValues(identifier);

        final ContentValues fixtureDirection = directionContentValues(identifier);

        TestUtils.create(mockContentResolver, TestUtils.getGeoLocationsUri(), fixtureGpsPoint);
        TestUtils.create(mockContentResolver, TestUtils.getAccelerationsUri(), fixtureAcceleration);
        TestUtils.create(mockContentResolver, TestUtils.getRotationsUri(), fixtureRotation);
        TestUtils.create(mockContentResolver, TestUtils.getDirectionsUri(), fixtureDirection);

        Cursor measurementCursor = null;
        try {
            measurementCursor = mockContentResolver.query(TestUtils.getMeasurementUri(), null,
                    MeasurementTable.COLUMN_FINISHED + "=?", new String[]{Long.valueOf(0).toString()}, null);
            assertThat(measurementCursor.getCount() > 0, is(equalTo(true)));
        } finally {
            if (measurementCursor != null) {
                measurementCursor.close();
            }
        }

        final int rowsDeleted = mockContentResolver.delete(TestUtils.getMeasurementUri(), null, null);
        assertThat("Delete was unsuccessful for uri " + TestUtils.getMeasurementUri(), 5, is(rowsDeleted));
        assertThat(TestUtils.count(mockContentResolver, TestUtils.getGeoLocationsUri()), is(0));
        assertThat(TestUtils.count(mockContentResolver, TestUtils.getAccelerationsUri()), is(0));
        assertThat(TestUtils.count(mockContentResolver, TestUtils.getRotationsUri()), is(0));
        assertThat(TestUtils.count(mockContentResolver, TestUtils.getDirectionsUri()), is(0));
    }

    /**
     * Tests whether it is possible to delete multiple measurements and their corresponding data in one go.
     */
    @Test
    public void cascadingDeleteMeasurements() {
        for (int i = 0; i < 2; i++) {
            final long identifier = TestUtils.create(mockContentResolver, TestUtils.getMeasurementUri(),
                    fixtureMeasurement);

            final ContentValues fixtureGpsPoint = geoLocationContentValues(identifier);

            final ContentValues fixtureAcceleration = accelerationContentValues(identifier);

            final ContentValues fixtureRotation = rotationContentValues(identifier);

            final ContentValues fixtureDirection = directionContentValues(identifier);

            TestUtils.create(mockContentResolver, TestUtils.getGeoLocationsUri(), fixtureGpsPoint);
            TestUtils.create(mockContentResolver, TestUtils.getAccelerationsUri(), fixtureAcceleration);
            TestUtils.create(mockContentResolver, TestUtils.getRotationsUri(), fixtureRotation);
            TestUtils.create(mockContentResolver, TestUtils.getDirectionsUri(), fixtureDirection);
        }

        final int rowsDeleted = mockContentResolver.delete(TestUtils.getMeasurementUri(), null, null);
        assertThat("Delete was unsuccessful for uri " + TestUtils.getMeasurementUri(), 10, is(rowsDeleted));
    }

    /**
     * Provides a set of <code>ContentValues</code> to create a row in the table for geo locations.
     *
     * @param measurementIdentifier The identifier of the measurement the new geo location should reference.
     * @return The filled out <code>ContentValues</code>.
     */
    private ContentValues geoLocationContentValues(final long measurementIdentifier) {
        final ContentValues ret = new ContentValues();
        ret.put(GeoLocationsTable.COLUMN_GPS_TIME, 10000L);
        ret.put(GeoLocationsTable.COLUMN_IS_SYNCED, false);
        ret.put(GeoLocationsTable.COLUMN_LAT, 13.0);
        ret.put(GeoLocationsTable.COLUMN_LON, 51.0);
        ret.put(GeoLocationsTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
        ret.put(GeoLocationsTable.COLUMN_SPEED, 1.0);
        ret.put(GeoLocationsTable.COLUMN_ACCURACY, 300);
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
     */
    @After
    public void tearDown() {
        mockContentResolver.delete(TestUtils.getGeoLocationsUri(), null, null);
        mockContentResolver.delete(TestUtils.getAccelerationsUri(), null, null);
        mockContentResolver.delete(TestUtils.getDirectionsUri(), null, null);
        mockContentResolver.delete(TestUtils.getRotationsUri(), null, null);
        mockContentResolver.delete(TestUtils.getMeasurementUri(), null, null);
    }
}
