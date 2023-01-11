package de.cyface.persistence;

import static de.cyface.persistence.TestUtils.AUTHORITY_V6;
import static de.cyface.persistence.Utils.getGeoLocationsV6Uri;
import static de.cyface.utils.CursorIsNullException.softCatchNullCursor;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.provider.ProviderTestRule;

import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;

/**
 * Tests whether the content provider for measuring points works or not.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.3.0
 */
@RunWith(AndroidJUnit4.class)
public final class GeoLocationV6Test {
    /**
     * Test rule that provides a mock connection to a <code>ContentProvider</code> to test against.
     */
    @Rule
    public ProviderTestRule providerRule = new ProviderTestRule.Builder(V6ContentProvider.class, AUTHORITY_V6)
            .build();
    /**
     * A mock content resolver provided by the Android test environment to work on a simulated content provider.
     */
    private ContentResolver mockResolver;

    /**
     * Compares a cursor from the database with a set of content values via JUnit assertions.
     *
     * @param message Error message to show if cursor contains multiple elements.
     * @param cursor The cursor to compare
     * @param values The values to compare to
     */
    private void cursorEqualsValues(final String message, final Cursor cursor, final ContentValues values) {
        assertThat(message, 1, is(cursor.getCount()));
        cursor.moveToFirst();

        assertThat(values.getAsLong(GeoLocationsTableV6.COLUMN_GEOLOCATION_TIME),
                is(cursor.getLong(cursor.getColumnIndex(GeoLocationsTableV6.COLUMN_GEOLOCATION_TIME))));
        assertThat(values.getAsFloat(GeoLocationsTableV6.COLUMN_LAT),
                is(cursor.getFloat(cursor.getColumnIndex(GeoLocationsTableV6.COLUMN_LAT))));
        assertThat(values.getAsFloat(GeoLocationsTableV6.COLUMN_LON),
                is(cursor.getFloat(cursor.getColumnIndex(GeoLocationsTableV6.COLUMN_LON))));
        assertThat(values.getAsFloat(GeoLocationsTableV6.COLUMN_ALTITUDE),
                is(cursor.getFloat(cursor.getColumnIndex(GeoLocationsTableV6.COLUMN_ALTITUDE))));
        assertThat(values.getAsInteger(GeoLocationsTableV6.COLUMN_MEASUREMENT_FK),
                is(cursor.getInt(cursor.getColumnIndex(GeoLocationsTableV6.COLUMN_MEASUREMENT_FK))));
        assertThat(values.getAsFloat(GeoLocationsTableV6.COLUMN_SPEED),
                is(cursor.getFloat(cursor.getColumnIndex(GeoLocationsTableV6.COLUMN_SPEED))));
        assertThat(values.getAsInteger(GeoLocationsTableV6.COLUMN_ACCURACY),
                is(cursor.getInt(cursor.getColumnIndex(GeoLocationsTableV6.COLUMN_ACCURACY))));
        assertThat(values.getAsInteger(GeoLocationsTableV6.COLUMN_VERTICAL_ACCURACY),
                is(cursor.getInt(cursor.getColumnIndex(GeoLocationsTableV6.COLUMN_VERTICAL_ACCURACY))));
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
    private ContentValues getTestFixture() {
        ContentValues values = new ContentValues();
        values.put(GeoLocationsTableV6.COLUMN_GEOLOCATION_TIME, 1234567890L);
        values.put(GeoLocationsTableV6.COLUMN_LAT, 51.03624633f);
        values.put(GeoLocationsTableV6.COLUMN_LON, 13.78828128f);
        values.put(GeoLocationsTableV6.COLUMN_ALTITUDE, 400.123);
        values.put(GeoLocationsTableV6.COLUMN_SPEED, 2.0f);
        values.put(GeoLocationsTableV6.COLUMN_ACCURACY, 300);
        values.put(GeoLocationsTableV6.COLUMN_VERTICAL_ACCURACY, 1200);
        values.put(GeoLocationsTableV6.COLUMN_MEASUREMENT_FK, 2);
        return values;
    }

    /**
     * Test that there are no geo locations left after we delete them from the database.
     */
    @Test
    public void testDeleteAllMeasuringPoints() {
        mockResolver.insert(getGeoLocationsV6Uri(AUTHORITY_V6), getTestFixture());

        assertThat(mockResolver.delete(getGeoLocationsV6Uri(AUTHORITY_V6), null, null) > 0, is(equalTo(true)));
    }

    /**
     * Test that deleting a geo location via selection actually removes that location from the content provider.
     */
    @Test
    public void testDeleteMeasuringPointViaSelection() {
        Uri createdRowUri = mockResolver.insert(getGeoLocationsV6Uri(AUTHORITY_V6), getTestFixture());
        Validate.notNull(createdRowUri);
        String createdId = createdRowUri.getLastPathSegment();

        assertThat(
                mockResolver.delete(getGeoLocationsV6Uri(AUTHORITY_V6), BaseColumns._ID + "= ?",
                        new String[] {createdId}),
                is(1));
    }

    /**
     * Test that deleting a geo location via URI identifier actually removes that location from the content provider.
     */
    @Test
    public void testDeleteMeasuringPointViaURL() {
        Uri createdRowUri = mockResolver.insert(getGeoLocationsV6Uri(AUTHORITY_V6), getTestFixture());
        Validate.notNull(createdRowUri);
        String createdId = createdRowUri.getLastPathSegment();

        assertThat(
                mockResolver.delete(getGeoLocationsV6Uri(AUTHORITY_V6).buildUpon().appendPath(createdId).build(), null,
                        null),
                is(1));
    }

    /**
     * Test that inserting a geo location into a content provider results in a content provider containing one geo
     * location.
     */
    @Test
    public void testCreateMeasuringPoint() {
        Uri insert = mockResolver.insert(getGeoLocationsV6Uri(AUTHORITY_V6), getTestFixture());
        Validate.notNull(insert);
        String lastPathSegment = insert.getLastPathSegment();
        assertThat(lastPathSegment, not(equalTo("-1")));
        Validate.notNull(lastPathSegment);
        long identifier = Long.parseLong(lastPathSegment);
        assertThat(identifier > 0L, is(true));
    }

    /**
     * Test that reading from a content provider with a geo location returns that geo location.
     */
    @Test
    public void testReadMeasuringPoint() {
        Uri insert = mockResolver.insert(getGeoLocationsV6Uri(AUTHORITY_V6), getTestFixture());
        Validate.notNull(insert);
        String lastPathSegment = insert.getLastPathSegment();

        try (Cursor urlQuery = mockResolver.query(
                getGeoLocationsV6Uri(AUTHORITY_V6).buildUpon().appendPath(lastPathSegment).build(), null, null, null,
                null);
                Cursor selectionQuery = mockResolver.query(getGeoLocationsV6Uri(AUTHORITY_V6), null,
                        BaseColumns._ID + "=?",
                        new String[] {lastPathSegment}, null);
                Cursor allQuery = mockResolver.query(getGeoLocationsV6Uri(AUTHORITY_V6), null, null, null, null)) {
            // Select
            Validate.notNull(urlQuery);
            Validate.notNull(selectionQuery);
            Validate.notNull(allQuery);
            cursorEqualsValues("Unable to load all measuring points via URI.", urlQuery, getTestFixture());
            cursorEqualsValues("Unable to load measuring point via selection.", selectionQuery, getTestFixture());
            cursorEqualsValues("Unable to load all measuring points via URI.", allQuery, getTestFixture());
        }
    }

    /**
     * Test that changing a single column value for a geo location works as expected.
     */
    @Test
    public void testUpdateMeasuringPoint() throws CursorIsNullException {
        Uri insert = mockResolver.insert(getGeoLocationsV6Uri(AUTHORITY_V6), getTestFixture());
        Validate.notNull(insert);
        String lastPathSegment = insert.getLastPathSegment();

        ContentValues newValues = new ContentValues();
        newValues.put(GeoLocationsTableV6.COLUMN_LAT, 10.34f);

        Uri dataPointUri = getGeoLocationsV6Uri(AUTHORITY_V6).buildUpon().appendPath(lastPathSegment).build();
        assertThat(mockResolver.update(dataPointUri, newValues, null, null), is(1));

        try (Cursor cursor = mockResolver.query(dataPointUri, null, null, null, null)) {
            softCatchNullCursor(cursor);
            assertThat(cursor.getCount(), is(1));
            cursor.moveToFirst();
            int columnIndex = cursor.getColumnIndex(GeoLocationsTableV6.COLUMN_LAT);
            assertThat(cursor.getFloat(columnIndex), is(10.34F));
        }
    }

    /**
     * Clean the database after each test.
     */
    @After
    public void tearDown() {
        mockResolver.delete(getGeoLocationsV6Uri(AUTHORITY_V6), null, null);
    }

}
