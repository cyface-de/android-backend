/*
 * Copyright 2021 Cyface GmbH
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
package de.cyface.persistence.serialization;

import android.database.Cursor;

import androidx.annotation.NonNull;

import de.cyface.persistence.GeoLocationsTable;
import de.cyface.protos.model.LocationRecords;
import de.cyface.serializer.Formatter;
import de.cyface.serializer.LocationOffsetter;
import de.cyface.utils.Validate;

/**
 * Serializes {@code Location}s in the {@link MeasurementSerializer#TRANSFER_FILE_FORMAT_VERSION}.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.0.0
 */
public class LocationSerializer {

    /**
     * The builder holding the serialized locations.
     */
    private final LocationRecords.Builder builder;
    /**
     * The offsetter to use for this measurement.
     */
    private final LocationOffsetter offsetter;

    /**
     * Fully initialized constructor of this class.
     * <p>
     * Use {@link #readFrom(Cursor)} to add {@code Location} from the database.
     * And {@link #result()} to receive the {@code Location}s in the serialized format.
     */
    public LocationSerializer() {
        this.builder = LocationRecords.newBuilder();
        // Initialize offsetter once for each measurement not on each `readFrom` call! [RFR-104]
        this.offsetter = new LocationOffsetter();
    }

    /**
     * Loads and parses {@code Location}s from a database {@code Cursor}.
     *
     * @param cursor the {@code Cursor} to load the {@code Location} data from.
     */
    public void readFrom(@NonNull final Cursor cursor) {

        while (cursor.moveToNext()) {
            final long timestamp = cursor
                    .getLong(cursor.getColumnIndexOrThrow(GeoLocationsTable.COLUMN_GEOLOCATION_TIME));
            final double latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(GeoLocationsTable.COLUMN_LAT));
            final double longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(GeoLocationsTable.COLUMN_LON));
            final double speedMeterPerSecond = cursor
                    .getDouble(cursor.getColumnIndexOrThrow(GeoLocationsTable.COLUMN_SPEED));
            final double accuracy = cursor.getDouble(cursor.getColumnIndexOrThrow(GeoLocationsTable.COLUMN_ACCURACY));

            // The proto serializer expects some fields in a different format and in offset-format
            final Formatter.Location formatted = new Formatter.Location(timestamp, latitude, longitude,
                    speedMeterPerSecond,
                    accuracy);
            final Formatter.Location offsets = offsetter.offset(formatted);

            builder.addTimestamp(offsets.getTimestamp())
                    .addLatitude(offsets.getLatitude())
                    .addLongitude(offsets.getLongitude())
                    .addAccuracy(offsets.getAccuracy())
                    .addSpeed(offsets.getSpeed());
        }
    }

    /**
     * @return the locations in the serialized format.
     */
    public LocationRecords result() {
        Validate.isTrue(builder.isInitialized());
        return builder.build();
    }
}