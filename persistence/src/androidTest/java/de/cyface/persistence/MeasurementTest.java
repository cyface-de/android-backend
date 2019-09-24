/*
 * Copyright 2017 Cyface GmbH
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
package de.cyface.persistence;

import static de.cyface.persistence.TestUtils.AUTHORITY;
import static de.cyface.persistence.Utils.getEventUri;
import static de.cyface.persistence.Utils.getGeoLocationsUri;
import static de.cyface.persistence.Utils.getMeasurementUri;
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
import android.database.Cursor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.serialization.MeasurementSerializer;
import de.cyface.utils.Validate;

/**
 * Tests that CRUD operations on measurements and the measurements table are working correctly.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.5
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
                MeasurementSerializer.PERSISTENCE_FILE_FORMAT_VERSION);
        fixtureMeasurement.put(MeasurementTable.COLUMN_DISTANCE, 0.0);
        fixtureMeasurement.put(MeasurementTable.COLUMN_TIMESTAMP, 123L);
    }

    /**
     * Shuts down the test and makes sure the database is empty for the next test.
     */
    @After
    public void tearDown() {
        resolver.delete(getGeoLocationsUri(AUTHORITY), null, null);
        resolver.delete(getMeasurementUri(AUTHORITY), null, null);
        resolver.delete(getEventUri(AUTHORITY), null, null);
    }

    /**
     * Tests whether it is possible to delete one measurement and all corresponding data in one go.
     */
    @Test
    public void testCascadingDeleteOneMeasurement() {

        // Create measurement with data
        final long identifier = TestUtils.create(resolver, getMeasurementUri(AUTHORITY), fixtureMeasurement);
        final ContentValues fixtureGeoLocation = geoLocationContentValues(identifier);
        TestUtils.create(resolver, getGeoLocationsUri(AUTHORITY), fixtureGeoLocation);

        // Test load the create measurement
        Cursor measurementCursor = null;
        try {
            measurementCursor = resolver.query(getMeasurementUri(AUTHORITY), null,
                    MeasurementTable.COLUMN_STATUS + "=?",
                    new String[] {MeasurementStatus.SYNCED.getDatabaseIdentifier()}, null);
            Validate.notNull(measurementCursor);
            assertThat(measurementCursor.getCount() > 0, is(equalTo(true)));
        } finally {
            if (measurementCursor != null) {
                measurementCursor.close();
            }
        }

        // Ensure deletion of measurement with data works
        final int rowsDeleted = resolver.delete(getMeasurementUri(AUTHORITY), null, null);
        assertThat("Delete was unsuccessful for uri " + getMeasurementUri(AUTHORITY), 2, is(rowsDeleted));
        assertThat(TestUtils.count(resolver, getGeoLocationsUri(AUTHORITY)), is(0));
    }

    /**
     * Tests whether it is possible to delete multiple measurements and their corresponding data in one go.
     */
    @Test
    public void testCascadingDeleteMultipleMeasurements() {

        // Create measurements with data
        for (int i = 0; i < 2; i++) {
            final long identifier = TestUtils.create(resolver, getMeasurementUri(AUTHORITY), fixtureMeasurement);
            final ContentValues fixtureGeoLocation = geoLocationContentValues(identifier);
            TestUtils.create(resolver, getGeoLocationsUri(AUTHORITY), fixtureGeoLocation);
        }

        // Ensure deletion of measurements with data works
        final int rowsDeleted = resolver.delete(getMeasurementUri(AUTHORITY), null, null);
        assertThat("Delete was unsuccessful for uri " + getMeasurementUri(AUTHORITY), 4, is(rowsDeleted));
    }

    /**
     * Provides a set of <code>ContentValues</code> to create a row in the table for geo locations.
     *
     * @param measurementIdentifier The identifier of the measurement the new geo location should reference.
     * @return The filled out <code>ContentValues</code>.
     */
    private ContentValues geoLocationContentValues(final long measurementIdentifier) {
        final ContentValues ret = new ContentValues();
        ret.put(GeoLocationsTable.COLUMN_GEOLOCATION_TIME, 10000L);
        ret.put(GeoLocationsTable.COLUMN_LAT, 13.0);
        ret.put(GeoLocationsTable.COLUMN_LON, 51.0);
        ret.put(GeoLocationsTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
        ret.put(GeoLocationsTable.COLUMN_SPEED, 1.0);
        ret.put(GeoLocationsTable.COLUMN_ACCURACY, 300);
        return ret;
    }
}
