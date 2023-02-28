/*
 * Copyright 2017-2023 Cyface GmbH
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
import static de.cyface.persistence.content.TestUtils.AUTHORITY;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import de.cyface.persistence.model.EventType;
import de.cyface.persistence.model.MeasurementStatus;

/**
 * Tests that CRUD operations on measurements and the measurements table are working correctly as they are used in the
 * {@link MeasurementProvider}.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.8
 * @since 1.0.0
 */
@RunWith(AndroidJUnit4.class)
public class MeasurementTest {

    /**
     * A <code>ContentValues</code> object as prototype to create measurements in the database.
     */
    private ContentValues fixtureMeasurement;
    /**
     * A mock resolver for accessing the mocked content provider.
     */
    private ContentResolver resolver;

    /**
     * Initializes the <code>ContentProvider</code> as well as the fixture measurement.
     */
    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        resolver = context.getContentResolver();
        fixtureMeasurement = new ContentValues();
        fixtureMeasurement.put(MeasurementTable.COLUMN_STATUS, MeasurementStatus.SYNCED.getDatabaseIdentifier());
        fixtureMeasurement.put(MeasurementTable.COLUMN_MODALITY, "BICYCLE");
        fixtureMeasurement.put(MeasurementTable.COLUMN_PERSISTENCE_FILE_FORMAT_VERSION,
                PERSISTENCE_FILE_FORMAT_VERSION);
        fixtureMeasurement.put(MeasurementTable.COLUMN_DISTANCE, 0.0);
        fixtureMeasurement.put(BaseColumns.TIMESTAMP, 123L);
    }

    /**
     * Shuts down the test and makes sure the database is empty for the next test.
     */
    @After
    public void tearDown() {
        resolver.delete(MeasurementTable.Companion.getUri(AUTHORITY), null, null);
    }

    /**
     * Tests whether it is possible to delete one measurement and all corresponding data in one go.
     */
    @Test
    public void testCascadingDeleteOneMeasurement() {
        // Arrange
        createMeasurementWithData();
        // Ensure the associated data is created
        final var measurementUri = MeasurementTable.Companion.getUri(AUTHORITY);
        final var eventUri = EventTable.Companion.getUri(AUTHORITY);
        final var locationUri = LocationTable.Companion.getUri(AUTHORITY);
        final var pressureUri = PressureTable.Companion.getUri(AUTHORITY);
        assertThat(TestUtils.count(resolver, measurementUri), is(equalTo(1)));
        assertThat(TestUtils.count(resolver, eventUri), is(equalTo(1)));
        assertThat(TestUtils.count(resolver, locationUri), is(equalTo(1)));
        assertThat(TestUtils.count(resolver, pressureUri), is(equalTo(1)));

        // Act
        final int rowsDeleted = resolver.delete(measurementUri, null, null);

        // Assert
        assertThat("Delete was unsuccessful for uri " + measurementUri, rowsDeleted, is(1));
        assertThat(TestUtils.count(resolver, locationUri), is(0));
        // Ensure the associated data was cascadingly deleted
        assertThat(TestUtils.count(resolver, eventUri), is(equalTo(0)));
        assertThat(TestUtils.count(resolver, locationUri), is(equalTo(0)));
        assertThat(TestUtils.count(resolver, pressureUri), is(equalTo(0)));
    }

    /**
     * Tests whether it is possible to delete multiple measurements and their corresponding data in one go.
     */
    @Test
    public void testCascadingDeleteMultipleMeasurements() {
        // Arrange: Create measurements with data
        for (int i = 0; i < 2; i++) {
            createMeasurementWithData();
        }
        // Ensure the associated data is created
        final var measurementUri = MeasurementTable.Companion.getUri(AUTHORITY);
        final var eventUri = EventTable.Companion.getUri(AUTHORITY);
        final var locationUri = LocationTable.Companion.getUri(AUTHORITY);
        final var pressureUri = PressureTable.Companion.getUri(AUTHORITY);
        assertThat(TestUtils.count(resolver, measurementUri), is(equalTo(2)));
        assertThat(TestUtils.count(resolver, eventUri), is(equalTo(2)));
        assertThat(TestUtils.count(resolver, locationUri), is(equalTo(2)));
        assertThat(TestUtils.count(resolver, pressureUri), is(equalTo(2)));

        // Act
        final int rowsDeleted = resolver.delete(measurementUri, null, null);

        // Assert
        assertThat("Delete was unsuccessful for uri " + measurementUri, rowsDeleted, is(2));

        // Ensure the associated data was cascadingly deleted
        assertThat(TestUtils.count(resolver, measurementUri), is(equalTo(0)));
        assertThat(TestUtils.count(resolver, eventUri), is(equalTo(0)));
        assertThat(TestUtils.count(resolver, locationUri), is(equalTo(0)));
        assertThat(TestUtils.count(resolver, pressureUri), is(equalTo(0)));
    }

    /**
     * Creates a measurement in the test database with some sample data.
     *
     * @return The id of the created measurement.
     */
    @SuppressWarnings("UnusedReturnValue")
    private long createMeasurementWithData() {
        final var measurementUri = MeasurementTable.Companion.getUri(AUTHORITY);
        final var measurementId = TestUtils.create(resolver, measurementUri, fixtureMeasurement);
        final var fixtureEvent = eventContentValues(measurementId);
        final var fixtureGeoLocation = geoLocationContentValues(measurementId);
        final var fixturePressure = pressureContentValues(measurementId);
        TestUtils.create(resolver, EventTable.Companion.getUri(AUTHORITY), fixtureEvent);
        TestUtils.create(resolver, LocationTable.Companion.getUri(AUTHORITY), fixtureGeoLocation);
        TestUtils.create(resolver, PressureTable.Companion.getUri(AUTHORITY), fixturePressure);
        return measurementId;
    }

    /**
     * Provides a set of <code>ContentValues</code> to create a row in the table for geo locations.
     *
     * @param measurementIdentifier The identifier of the measurement the new geo location should reference.
     * @return The filled out <code>ContentValues</code>.
     */
    private ContentValues geoLocationContentValues(final long measurementIdentifier) {
        final ContentValues ret = new ContentValues();
        ret.put(BaseColumns.TIMESTAMP, 10000L);
        ret.put(LocationTable.COLUMN_LAT, 13.0);
        ret.put(LocationTable.COLUMN_LON, 51.0);
        ret.put(BaseColumns.MEASUREMENT_ID, measurementIdentifier);
        ret.put(LocationTable.COLUMN_SPEED, 1.0);
        ret.put(LocationTable.COLUMN_ACCURACY, 3.0f);
        return ret;
    }

    /**
     * Provides a set of {@code ContentValues} to create a row in the {@link PressureTable}.
     *
     * @param measurementIdentifier The identifier of the measurement the new row should reference.
     * @return The filled out {@code ContentValues}.
     */
    private ContentValues pressureContentValues(final long measurementIdentifier) {
        final var ret = new ContentValues();
        ret.put(BaseColumns.TIMESTAMP, 10000L);
        ret.put(PressureTable.COLUMN_PRESSURE, 1013.);
        ret.put(BaseColumns.MEASUREMENT_ID, measurementIdentifier);
        return ret;
    }

    /**
     * Provides a set of {@code ContentValues} to create a row in the {@link EventTable}.
     *
     * @param measurementIdentifier The identifier of the measurement the new row should reference.
     * @return The filled out {@code ContentValues}.
     */
    private ContentValues eventContentValues(final long measurementIdentifier) {
        final var ret = new ContentValues();
        ret.put(BaseColumns.TIMESTAMP, 10000L);
        ret.put(EventTable.COLUMN_TYPE, EventType.LIFECYCLE_START.getDatabaseIdentifier());
        ret.put(BaseColumns.MEASUREMENT_ID, measurementIdentifier);
        return ret;
    }
}
