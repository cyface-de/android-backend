/*
 * Copyright 2021-2023 Cyface GmbH
 *
 * This file is part of the Cyface SDK for Android.
 *
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.persistence.content;

import static de.cyface.persistence.DefaultPersistenceLayer.PERSISTENCE_FILE_FORMAT_VERSION;
import static de.cyface.persistence.content.LocationTable.Companion;
import static de.cyface.persistence.content.TestUtils.AUTHORITY;
import static de.cyface.persistence.model.MeasurementStatus.OPEN;
import static de.cyface.testutils.SharedTestUtils.clearPersistenceLayer;
import static de.cyface.utils.CursorIsNullException.softCatchNullCursor;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;

/**
 * Tests that the {@link DefaultProviderClient} for {@link de.cyface.persistence.model.GeoLocation}s works.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.0.3
 * @since 1.0.0
 */
@RunWith(AndroidJUnit4.class)
public final class ParcelableGeoLocationTest {

    private Context context;
    private ContentResolver mockResolver;
    private Long measurementId;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mockResolver = context.getContentResolver();
        clearPersistenceLayer(context, mockResolver, AUTHORITY);
        measurementId = createMeasurement();
    }

    @After
    public void tearDown() {
        clearPersistenceLayer(context, mockResolver, AUTHORITY);
    }

    /**
     * A convenience method to quickly get some test data.
     *
     * @return A test fixture with one geo location.
     */
    private ContentValues getTestFixture() {
        ContentValues values = new ContentValues();
        values.put(BaseColumns.TIMESTAMP, 1234567890L);
        values.put(LocationTable.COLUMN_LAT, 51.03624633f);
        values.put(LocationTable.COLUMN_LON, 13.78828128f);
        values.put(LocationTable.COLUMN_ALTITUDE, 400.);
        values.put(LocationTable.COLUMN_SPEED, 2.0f);
        values.put(LocationTable.COLUMN_ACCURACY, 3.0f);
        values.put(LocationTable.COLUMN_VERTICAL_ACCURACY, 20.);
        values.put(BaseColumns.MEASUREMENT_ID, measurementId);
        return values;
    }

    /**
     * Test that there are no geo locations left after we delete them from the database.
     */
    @Test
    public void testDeleteAllMeasuringPoints() {
        mockResolver.insert(Companion.getUri(AUTHORITY), getTestFixture());

        assertThat(mockResolver.delete(Companion.getUri(AUTHORITY), null, null) > 0, is(equalTo(true)));
    }

    /**
     * Test that deleting a geo location via selection actually removes that location from the content provider.
     */
    @Test
    public void testDeleteMeasuringPointViaSelection() {
        Uri createdRowUri = mockResolver.insert(Companion.getUri(AUTHORITY), getTestFixture());
        Validate.notNull(createdRowUri);
        String createdId = createdRowUri.getLastPathSegment();

        assertThat(
                mockResolver.delete(Companion.getUri(AUTHORITY), BaseColumns.ID + "= ?", new String[] {createdId}),
                is(1));
    }

    /**
     * Test that deleting a geo location via URI identifier actually removes that location from the content provider.
     */
    @Test
    public void testDeleteMeasuringPointViaURL() {
        Uri createdRowUri = mockResolver.insert(Companion.getUri(AUTHORITY), getTestFixture());
        Validate.notNull(createdRowUri);
        String createdId = createdRowUri.getLastPathSegment();

        assertThat(mockResolver.delete(Companion.getUri(AUTHORITY).buildUpon().appendPath(createdId).build(), null,
                null), is(1));
    }

    /**
     * Test that inserting a geo location into a content provider results in a content provider containing one geo
     * location.
     */
    @Test
    public void testCreateMeasuringPoint() {
        Uri insert = mockResolver.insert(Companion.getUri(AUTHORITY), getTestFixture());
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
        Uri insert = mockResolver.insert(Companion.getUri(AUTHORITY), getTestFixture());
        Validate.notNull(insert);
        String lastPathSegment = insert.getLastPathSegment();

        try (Cursor urlQuery = mockResolver.query(
                Companion.getUri(AUTHORITY).buildUpon().appendPath(lastPathSegment).build(), null, null, null, null);
                Cursor selectionQuery = mockResolver.query(Companion.getUri(AUTHORITY), null, BaseColumns.ID + "=?",
                        new String[] {lastPathSegment}, null);
                Cursor allQuery = mockResolver.query(Companion.getUri(AUTHORITY), null, null, null, null)) {
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
        Uri insert = mockResolver.insert(Companion.getUri(AUTHORITY), getTestFixture());
        Validate.notNull(insert);
        String lastPathSegment = insert.getLastPathSegment();

        ContentValues newValues = new ContentValues();
        newValues.put(LocationTable.COLUMN_LAT, 10.34);

        Uri dataPointUri = Companion.getUri(AUTHORITY).buildUpon().appendPath(lastPathSegment).build();
        assertThat(mockResolver.update(dataPointUri, newValues, null, null), is(1));

        try (Cursor cursor = mockResolver.query(dataPointUri, null, null, null, null)) {
            softCatchNullCursor(cursor);
            assertThat(cursor.getCount(), is(1));
            cursor.moveToFirst();
            int columnIndex = cursor.getColumnIndexOrThrow(LocationTable.COLUMN_LAT);
            assertThat(cursor.getDouble(columnIndex), is(10.34));
        }
    }

    /**
     * Creates a {@link de.cyface.persistence.model.Measurement} in the test database.
     *
     * @return The created object.
     */
    private long createMeasurement() {
        var values = new ContentValues();
        values.put(MeasurementTable.COLUMN_STATUS, OPEN.getDatabaseIdentifier());
        values.put(MeasurementTable.COLUMN_MODALITY, "BICYCLE");
        values.put(MeasurementTable.COLUMN_PERSISTENCE_FILE_FORMAT_VERSION,
                PERSISTENCE_FILE_FORMAT_VERSION);
        values.put(MeasurementTable.COLUMN_DISTANCE, 0.0);
        values.put(BaseColumns.TIMESTAMP, 1);
        Uri result = mockResolver.insert(MeasurementTable.Companion.getUri(AUTHORITY), values);
        return Long.parseLong(result.getLastPathSegment());
    }

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

        assertThat(values.getAsLong(BaseColumns.TIMESTAMP),
                is(cursor.getLong(cursor.getColumnIndexOrThrow(BaseColumns.TIMESTAMP))));
        assertThat(values.getAsDouble(LocationTable.COLUMN_LAT),
                is(cursor.getDouble(cursor.getColumnIndexOrThrow(LocationTable.COLUMN_LAT))));
        assertThat(values.getAsDouble(LocationTable.COLUMN_LON),
                is(cursor.getDouble(cursor.getColumnIndexOrThrow(LocationTable.COLUMN_LON))));
        assertThat(values.getAsDouble(LocationTable.COLUMN_ALTITUDE),
                is(cursor.getDouble(cursor.getColumnIndexOrThrow(LocationTable.COLUMN_ALTITUDE))));
        assertThat(values.getAsInteger(BaseColumns.MEASUREMENT_ID),
                is(cursor.getInt(cursor.getColumnIndexOrThrow(BaseColumns.MEASUREMENT_ID))));
        assertThat(values.getAsDouble(LocationTable.COLUMN_SPEED),
                is(cursor.getDouble(cursor.getColumnIndexOrThrow(LocationTable.COLUMN_SPEED))));
        assertThat(values.getAsDouble(LocationTable.COLUMN_ACCURACY),
                is(cursor.getDouble(cursor.getColumnIndexOrThrow(LocationTable.COLUMN_ACCURACY))));
        assertThat(values.getAsDouble(LocationTable.COLUMN_VERTICAL_ACCURACY),
                is(cursor.getDouble(cursor.getColumnIndex(LocationTable.COLUMN_VERTICAL_ACCURACY))));
    }
}
