package de.cyface.persistence.serialization.proto;

import static de.cyface.persistence.Constants.TAG;

import java.util.List;

import android.util.Log;

import androidx.annotation.NonNull;

import de.cyface.persistence.model.Point3d;
import de.cyface.persistence.serialization.Point3dType;
import de.cyface.protos.model.Accelerations;
import de.cyface.protos.model.Directions;
import de.cyface.protos.model.Rotations;

public class Point3dSerializer {

    /**
     * Serializes the provided {@link de.cyface.persistence.model.Point3d} points.
     *
     * @param data The {@code Point3d} points to serialize.
     * @param type The sensor data type of the {@code Point3d} data.
     * @return A {@code byte} array containing all the data.
     */
    public static byte[] serialize(final @NonNull List<de.cyface.persistence.model.Point3d> data, Point3dType type) {
        Log.v(TAG, String.format("Serializing %d Point3d points.", data.size()));

        switch (type) {
            case ACCELERATION:
                return accelerations(data).toByteArray();
            case ROTATION:
                return rotations(data).toByteArray();
            case DIRECTION:
                return directions(data).toByteArray();
            default:
                throw new IllegalArgumentException("Unknown type: " + type);
        }
    }

    private static Accelerations accelerations(@NonNull final List<de.cyface.persistence.model.Point3d> data) {
        final Accelerations.Builder builder = Accelerations.newBuilder();

        // The offsetter must be initialized once for each point
        final Point3dOffsetter offsetter = new Point3dOffsetter();

        for (final de.cyface.persistence.model.Point3d point : data) {
            final Point3dOffsetter.Point3d offsets = convert(point, Point3dType.ACCELERATION, offsetter);
            builder.addTimestamp(offsets.getTimestamp())
                    .addX(offsets.getX())
                    .addY(offsets.getY())
                    .addZ(offsets.getZ());
        }
        return builder.build();
    }

    private static Rotations rotations(@NonNull final List<de.cyface.persistence.model.Point3d> data) {
        final Rotations.Builder builder = Rotations.newBuilder();

        // The offsetter must be initialized once for each point
        final Point3dOffsetter offsetter = new Point3dOffsetter();

        for (final de.cyface.persistence.model.Point3d point : data) {
            final Point3dOffsetter.Point3d offsets = convert(point, Point3dType.ROTATION, offsetter);
            builder.addTimestamp(offsets.getTimestamp())
                    .addX(offsets.getX())
                    .addY(offsets.getY())
                    .addZ(offsets.getZ());
        }
        return builder.build();
    }

    private static Directions directions(@NonNull final List<de.cyface.persistence.model.Point3d> data) {
        final Directions.Builder builder = Directions.newBuilder();

        // The offsetter must be initialized once for each point
        final Point3dOffsetter offsetter = new Point3dOffsetter();

        for (final de.cyface.persistence.model.Point3d point : data) {
            final Point3dOffsetter.Point3d offsets = convert(point, Point3dType.DIRECTION, offsetter);
            builder.addTimestamp(offsets.getTimestamp())
                    .addX(offsets.getX())
                    .addY(offsets.getY())
                    .addZ(offsets.getZ());
        }
        return builder.build();
    }

    private static Point3dOffsetter.Point3d convert(Point3d point, Point3dType type, Point3dOffsetter offsetter) {

        // The proto serializer expects some fields in a different format and in offset-format
        final Formatter.Point3d formatted = new Formatter.Point3d(type, point.getTimestamp(), point.getX(),
                point.getY(), point.getZ());
        return offsetter.offset(formatted);
    }
}
