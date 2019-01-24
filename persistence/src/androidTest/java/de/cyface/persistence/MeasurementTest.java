package de.cyface.persistence;

import static de.cyface.persistence.TestUtils.AUTHORITY;
import static de.cyface.persistence.Utils.getGeoLocationsUri;
import static de.cyface.persistence.Utils.getMeasurementUri;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.provider.ProviderTestRule;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.serialization.MeasurementSerializer;
import de.cyface.utils.Validate;

/**
 * Tests that CRUD operations on measurements and the measurements table are working correctly.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.0
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
        fixtureMeasurement.put(MeasurementTable.COLUMN_STATUS, MeasurementStatus.OPEN.getDatabaseIdentifier());
        fixtureMeasurement.put(MeasurementTable.COLUMN_VEHICLE, "BICYCLE");
        fixtureMeasurement.put(MeasurementTable.COLUMN_ACCELERATIONS, 0);
        fixtureMeasurement.put(MeasurementTable.COLUMN_ROTATIONS, 0);
        fixtureMeasurement.put(MeasurementTable.COLUMN_DIRECTIONS, 0);
        fixtureMeasurement.put(MeasurementTable.COLUMN_PERSISTENCE_FILE_FORMAT_VERSION,
                MeasurementSerializer.PERSISTENCE_FILE_FORMAT_VERSION);

        mockContentResolver = providerRule.getResolver();
    }

    /**
     * Tests whether it is possible to delete one measurement and all corresponding data in one go.
     */
    @Test
    public void testCascadingDeleteOneMeasurement() {
        final long identifier = TestUtils.create(mockContentResolver, getMeasurementUri(AUTHORITY), fixtureMeasurement);

        final ContentValues fixtureGpsPoint = geoLocationContentValues(identifier);
        // FIXME: delete point3d points too

        TestUtils.create(mockContentResolver, getGeoLocationsUri(AUTHORITY), fixtureGpsPoint);

        Cursor measurementCursor = null;
        try {
            measurementCursor = mockContentResolver.query(getMeasurementUri(AUTHORITY), null,
                    MeasurementTable.COLUMN_STATUS + "=?",
                    new String[] {MeasurementStatus.FINISHED.getDatabaseIdentifier()}, null);
            Validate.notNull(measurementCursor);
            assertThat(measurementCursor.getCount() > 0, is(equalTo(true)));
        } finally {
            if (measurementCursor != null) {
                measurementCursor.close();
            }
        }

        final int rowsDeleted = mockContentResolver.delete(getMeasurementUri(AUTHORITY), null, null);
        assertThat("Delete was unsuccessful for uri " + getMeasurementUri(AUTHORITY), 5, is(rowsDeleted));
        assertThat(TestUtils.count(mockContentResolver, getGeoLocationsUri(AUTHORITY)), is(0));
    }

    /**
     * Tests whether it is possible to delete multiple measurements and their corresponding data in one go.
     */
    @Test
    public void cascadingDeleteMeasurements() {
        for (int i = 0; i < 2; i++) {
            final long identifier = TestUtils.create(mockContentResolver, getMeasurementUri(AUTHORITY),
                    fixtureMeasurement);

            // FIXME delete sample points too

            final ContentValues fixtureGpsPoint = geoLocationContentValues(identifier);

            TestUtils.create(mockContentResolver, getGeoLocationsUri(AUTHORITY), fixtureGpsPoint);
        }

        final int rowsDeleted = mockContentResolver.delete(getMeasurementUri(AUTHORITY), null, null);
        assertThat("Delete was unsuccessful for uri " + getMeasurementUri(AUTHORITY), 10, is(rowsDeleted));
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
     *         /
     *         private ContentValues accelerationContentValues(final long measurementIdentifier) {
     *         final ContentValues ret = new ContentValues();
     *         ret.put(AccelerationPointTable.COLUMN_TIME, 10000L);
     *         ret.put(AccelerationPointTable.COLUMN_IS_SYNCED, false);
     *         ret.put(AccelerationPointTable.COLUMN_AX, 1.0);
     *         ret.put(AccelerationPointTable.COLUMN_AY, 1.0);
     *         ret.put(AccelerationPointTable.COLUMN_AZ, 1.0);
     *         ret.put(AccelerationPointTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
     *         return ret;
     *         }
     */

    /**
     * Shuts down the test and makes sure the database is empty for the next test.
     */
    @After
    public void tearDown() {
        mockContentResolver.delete(getGeoLocationsUri(AUTHORITY), null, null);
        mockContentResolver.delete(getMeasurementUri(AUTHORITY), null, null);
        // FIXME: points and device identifier, too
    }
}
