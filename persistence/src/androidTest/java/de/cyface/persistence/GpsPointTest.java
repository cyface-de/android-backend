/*
 * Created at 16:46:10 on 20.01.2015
 */
package de.cyface.persistence;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;

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
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

/**
 * Tests whether the content provider for measuring points works or not.
 *
 * @author Klemens Muthmann
 * @version 1.1.1
 * @since 1.0.0
 */
@RunWith(AndroidJUnit4.class)
public final class GpsPointTest {
    /**
     * Test rule that provides a mock connection to a <code>ContentProvider</code> to test against.
     */
    @Rule
    public ProviderTestRule providerRule = new ProviderTestRule.Builder(MeasuringPointsContentProvider.class, AUTHORITY)
            .build();
    /**
     * A mock content resolver provided by the Android test environment to work on a simulated content provider.
     */
    private ContentResolver mockResolver;

    /**
     * Compares a cursor from the database with a set of content values via JUnit assertions.
     *
     * @param message Error message to show if cursor contains multiple elements.
     * @param cursor  The cursor to compare
     * @param values  The values to compare to
     */
    private void cursorEqualsValues(final String message, final Cursor cursor, final ContentValues values) {
        assertThat(message, 1, is(cursor.getCount()));
        cursor.moveToFirst();

        assertThat(values.getAsLong(GeoLocationsTable.COLUMN_GPS_TIME),
                is(cursor.getLong(cursor.getColumnIndex(GeoLocationsTable.COLUMN_GPS_TIME))));
        assertThat(values.getAsFloat(GeoLocationsTable.COLUMN_LAT),
                is(cursor.getFloat(cursor.getColumnIndex(GeoLocationsTable.COLUMN_LAT))));
        assertThat(values.getAsFloat(GeoLocationsTable.COLUMN_LON),
                is(cursor.getFloat(cursor.getColumnIndex(GeoLocationsTable.COLUMN_LON))));
        assertThat(values.getAsInteger(GeoLocationsTable.COLUMN_MEASUREMENT_FK),
                is(cursor.getInt(cursor.getColumnIndex(GeoLocationsTable.COLUMN_MEASUREMENT_FK))));
        assertThat(values.getAsFloat(GeoLocationsTable.COLUMN_SPEED),
                is(cursor.getFloat(cursor.getColumnIndex(GeoLocationsTable.COLUMN_SPEED))));
        assertThat(values.getAsInteger(GeoLocationsTable.COLUMN_ACCURACY),
                is(cursor.getInt(cursor.getColumnIndex(GeoLocationsTable.COLUMN_ACCURACY))));
    }

    @Before
    public void setUp() {
        mockResolver = providerRule.getResolver();
    }

    /**
     * A convenience method to quickly get some test data.
     *
     * @return A test fixture with one geo location.
     */
    private ContentValues getTextFixture() {
        ContentValues values = new ContentValues();
        values.put(GeoLocationsTable.COLUMN_GPS_TIME, 1234567890L);
        values.put(GeoLocationsTable.COLUMN_LAT, 51.03624633f);
        values.put(GeoLocationsTable.COLUMN_LON, 13.78828128f);
        values.put(GeoLocationsTable.COLUMN_SPEED, 2.0f);
        values.put(GeoLocationsTable.COLUMN_ACCURACY, 300);
        values.put(GeoLocationsTable.COLUMN_MEASUREMENT_FK, 2);
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

        assertThat(mockResolver.delete(TestUtils.getGeoLocationsUri(), BaseColumns._ID + "= ?", new String[]{createdId}), is(1));
    }

    /**
     * Test that deleting a geo location via URI identifier actually removes that location from the content provider.
     */
    @Test
    public void testDeleteMeasuringPointViaURL() {
        Uri createdRowUri = mockResolver.insert(TestUtils.getGeoLocationsUri(), getTextFixture());
        String createdId = createdRowUri.getLastPathSegment();

        assertThat(mockResolver.delete(TestUtils.getGeoLocationsUri().buildUpon().appendPath(createdId).build(), null, null), is(1));
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
        assertThat(identifier > 0L, is(true));
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
                     BaseColumns._ID + "=?", new String[]{lastPathSegment}, null);
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
        newValues.put(GeoLocationsTable.COLUMN_LAT, 10.34f);

        Uri dataPointUri = TestUtils.getGeoLocationsUri().buildUpon().appendPath(lastPathSegment).build();
        assertThat(mockResolver.update(dataPointUri, newValues, null, null), is(1));

        try (Cursor query = mockResolver.query(dataPointUri, null, null, null, null);) {

            assertThat(query.getCount(), is(1));
            query.moveToFirst();
            int columnIndex = query.getColumnIndex(GeoLocationsTable.COLUMN_LAT);
            assertThat(query.getFloat(columnIndex), is(10.34F));
        }
    }

    /**
     * Clean the database after each test.
     */
    @After
    public void tearDown() {
        mockResolver.delete(TestUtils.getGeoLocationsUri(), null, null);
    }

}
