package de.cyface.persistence.serialization.proto;

import org.apache.commons.lang3.Validate;

import android.database.Cursor;

import androidx.annotation.NonNull;

import de.cyface.persistence.GeoLocationsTable;
import de.cyface.protos.model.LocationRecords;

public class LocationSerializer {

    private final LocationRecords.Builder builder;

    public LocationSerializer() {
        this.builder = LocationRecords.newBuilder();
    }

    public void readFrom(@NonNull final Cursor cursor) {

        // The offsetter must be initialized once for each location
        final LocationOffsetter offsetter = new LocationOffsetter();

        while (cursor.moveToNext()) {
            final long timestamp = cursor
                    .getLong(cursor.getColumnIndexOrThrow(GeoLocationsTable.COLUMN_GEOLOCATION_TIME));
            final double latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(GeoLocationsTable.COLUMN_LAT));
            final double longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(GeoLocationsTable.COLUMN_LON));
            final double speedMps = cursor.getDouble(cursor.getColumnIndexOrThrow(GeoLocationsTable.COLUMN_SPEED));
            final int accuracyCm = cursor.getInt(cursor.getColumnIndexOrThrow(GeoLocationsTable.COLUMN_ACCURACY));

            // The proto serializer expects some fields in a different format and in offset-format
            final Formatter.Location formatted = new Formatter.Location(timestamp, latitude, longitude, speedMps,
                    accuracyCm);
            final LocationOffsetter.LocationOffsets offsets = offsetter.offset(formatted);

            builder.addTimestamp(offsets.getTimestamp())
                    .addLatitude(offsets.getLatitude())
                    .addLongitude(offsets.getLongitude())
                    .addAccuracy(offsets.getAccuracy())
                    .addSpeed(offsets.getSpeed());
        }
    }

    public LocationRecords result() {
        Validate.isTrue(builder.isInitialized());
        return builder.build();
    }
}
