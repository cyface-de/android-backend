package de.cyface.persistence.serialization.proto;

import static de.cyface.persistence.Constants.TAG;

import java.util.List;

import android.util.Log;

import androidx.annotation.NonNull;

import de.cyface.persistence.model.Point3d;
import de.cyface.persistence.serialization.Point3dType;
import de.cyface.protos.model.LocationRecords;

public class Point3dSerializer {

    /**
     * Serializes the provided {@link Point3d} points.
     *
     * @param data The {@code Point3d} points to serialize.
     * @param type The sensor data type of the {@code Point3d} data.
     * @return A {@code byte} array containing all the data.
     */
    public static byte[] serialize(final @NonNull List<Point3d> data, Point3dType type) {
        Log.v(TAG, String.format("Serializing %d Point3d points.", data.size()));

        final LocationRecords records = readFrom(data, type);
        return records.toByteArray();
    }

    private static LocationRecords readFrom(@NonNull final List<Point3d> data, Point3dType type) {
        final LocationRecords.Builder builder = LocationRecords.newBuilder();

        // The offsetter must be initialized once for each location
        final Point3dOffsetter offsetter = new Point3dOffsetter();

        for (final Point3d point : data) {

            // The proto serializer expects some fields in a different format and in offset-format
            final Formatter.Point3d formatted = new Formatter.Point3d(type, point.getTimestamp(), point.getX(), point.getY(), point.getZ());
            final Point3dOffsetter.Point3sOffsets offsets = offsetter.offset(formatted);

            builder.addTimestamp(offsets.getTimestamp())
                    .addLatitude(offsets.getX())
                    .addLongitude(offsets.getY())
                    .addAccuracy(offsets.getZ());
        }

        return builder.build();
    }
}
