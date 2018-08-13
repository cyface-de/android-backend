/*
 * Created at 16:46:10 on 20.01.2015
 */
package de.cyface.persistence;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.test.ProviderTestCase2;
import android.test.mock.MockContentResolver;

/**
 * Tests whether the content provider for measuring points works or not.
 *
 * @author Klemens Muthmann
 *
 * @version 1.1.0
 * @since 1.0.0
 */
@RunWith(AndroidJUnit4.class)
public final class GpsPointTest extends ProviderTestCase2<MeasuringPointsContentProvider> {

    /**
     * A mock content resolver provided by the Android test environment to work on a simulated content provider.
     */
    private MockContentResolver mockResolver;

    /**
     * Required by <code>ProviderTestCase2</code> to completely initialize a test instance.
     */
    public GpsPointTest() {
        super(MeasuringPointsContentProvider.class, TestUtils.AUTHORITY);
    }

    /**
     * Compares a cursor from the database with a set of content values via JUnit assertions.
     *
     * @param message Error message to show if cursor contains multiple elements.
     * @param cursor The cursor to compare
     * @param values The values to compare to
     */
    private void cursorEqualsValues(final String message, final Cursor cursor, final ContentValues values) {
        assertEquals(message, 1, cursor.getCount());
        cursor.moveToFirst();

        assertEquals(values.get(GpsPointsTable.COLUMN_GPS_TIME),
                cursor.getLong(cursor.getColumnIndex(GpsPointsTable.COLUMN_GPS_TIME)));
        assertEquals(values.get(GpsPointsTable.COLUMN_LAT),
                cursor.getFloat(cursor.getColumnIndex(GpsPointsTable.COLUMN_LAT)));
        assertEquals(values.get(GpsPointsTable.COLUMN_LON),
                cursor.getFloat(cursor.getColumnIndex(GpsPointsTable.COLUMN_LON)));
        assertEquals(values.get(GpsPointsTable.COLUMN_MEASUREMENT_FK),
                cursor.getInt(cursor.getColumnIndex(GpsPointsTable.COLUMN_MEASUREMENT_FK)));
        assertEquals(values.get(GpsPointsTable.COLUMN_SPEED),
                cursor.getFloat(cursor.getColumnIndex(GpsPointsTable.COLUMN_SPEED)));
        assertEquals(values.get(GpsPointsTable.COLUMN_ACCURACY),
                cursor.getInt(cursor.getColumnIndex(GpsPointsTable.COLUMN_ACCURACY)));
    }

    @Override
    @Before
    public void setUp() throws Exception {
        // WARNING: Never change the order of the following two lines, even though the Google documentation tells you
        // something different!
        setContext(InstrumentationRegistry.getTargetContext());
        super.setUp();
        mockResolver = getMockContentResolver();
    }

    /**
     * A convenience method to quickly get some test data.
     *
     * @return A test fixture with one geo location.
     */
    private ContentValues getTextFixture() {
        ContentValues values = new ContentValues();
        values.put(GpsPointsTable.COLUMN_GPS_TIME, 1234567890L);
        values.put(GpsPointsTable.COLUMN_LAT, 51.03624633f);
        values.put(GpsPointsTable.COLUMN_LON, 13.78828128f);
        values.put(GpsPointsTable.COLUMN_SPEED, 2.0f);
        values.put(GpsPointsTable.COLUMN_ACCURACY, 300);
        values.put(GpsPointsTable.COLUMN_MEASUREMENT_FK, 2);
        return values;
    }

    /**
     * Test that there are no geo locations left after we delete them from the database.
     */
    @Test
    public void testDeleteAllMeasuringPoints() {
        mockResolver.insert(TestUtils.getGeoLocationsUri(), getTextFixture());

        assertThat(mockResolver.delete(TestUtils.getGeoLocationsUri(), null, null) > 0, is(equalTo(true)));
    }

    /**
     * Test that deleting a geo location via selection actually removes that location from the content provider.
     */
    @Test
    public void testDeleteMeasuringPointViaSelection() {
        Uri createdRowUri = mockResolver.insert(TestUtils.getGeoLocationsUri(), getTextFixture());
        String createdId = createdRowUri.getLastPathSegment();

        assertEquals(1,
                mockResolver.delete(TestUtils.getGeoLocationsUri(), BaseColumns._ID + "= ?", new String[] {createdId}));
    }

    /**
     * Test that deleting a geo location via URI identifier actually removes that location from the content provider.
     */
    @Test
    public void testDeleteMeasuringPointViaURL() {
        Uri createdRowUri = mockResolver.insert(TestUtils.getGeoLocationsUri(), getTextFixture());
        String createdId = createdRowUri.getLastPathSegment();

        assertEquals(1,
                mockResolver.delete(TestUtils.getGeoLocationsUri().buildUpon().appendPath(createdId).build(),
                        null, null));
    }

    /**
     * Test that inserting a geo location into a content provider results in a content provider containing one geo location.
     */
    @Test
    public void testCreateMeasuringPoint() {
        Uri insert = mockResolver.insert(TestUtils.getGeoLocationsUri(), getTextFixture());
        String lastPathSegment = insert.getLastPathSegment();
        assertThat(lastPathSegment, not(equalTo("-1")));
        long identifier = Long.parseLong(lastPathSegment);
        assertTrue(identifier > 0L);
    }

    /**
     * Test that reading from a content provider with a geo location returns that geo location.
     */
    @Test
    public void testReadMeasuringPoint() {
        Uri insert = mockResolver.insert(TestUtils.getGeoLocationsUri(), getTextFixture());
        String lastPathSegment = insert.getLastPathSegment();

        try (Cursor urlQuery = mockResolver.query(TestUtils.getGeoLocationsUri().buildUpon().appendPath(lastPathSegment).build(),
                null, null, null, null);
                Cursor selectionQuery = mockResolver.query(TestUtils.getGeoLocationsUri(), null,
                        BaseColumns._ID + "=?", new String[] {lastPathSegment}, null);
                Cursor allQuery = mockResolver.query(TestUtils.getGeoLocationsUri(), null, null,
                        null, null);) {
            // Select
            cursorEqualsValues("Unable to load all measuring points via URI.", urlQuery, getTextFixture());
            cursorEqualsValues("Unable to load measuring point via selection.", selectionQuery, getTextFixture());
            cursorEqualsValues("Unable to load all measuring points via URI.", allQuery, getTextFixture());
        }
    }

    /**
     * Test that changing a single column value for a geo location works as expected.
     */
    @Test
    public void testUpdateMeasuringPoint() {
        Uri insert = mockResolver.insert(TestUtils.getGeoLocationsUri(), getTextFixture());
        String lastPathSegment = insert.getLastPathSegment();

        ContentValues newValues = new ContentValues();
        newValues.put(GpsPointsTable.COLUMN_LAT, 10.34f);

        Uri dataPointUri = TestUtils.getGeoLocationsUri().buildUpon().appendPath(lastPathSegment).build();
        assertEquals(1, mockResolver.update(dataPointUri, newValues, null, null));

        try (Cursor query = mockResolver.query(dataPointUri, null, null, null, null);) {

            assertEquals(1, query.getCount());
            query.moveToFirst();
            int columnIndex = query.getColumnIndex(GpsPointsTable.COLUMN_LAT);
            assertEquals(10.34f, query.getFloat(columnIndex));
        }
    }

    /**
     * Clean the database after each test.
     *
     * @throws Exception In case anything unexpected happens in the super class.
     */
    @After
    public void tearDown() throws Exception {
        mockResolver.delete(TestUtils.getGeoLocationsUri(), null, null);
        super.tearDown();
        getProvider().shutdown();
    }

}
