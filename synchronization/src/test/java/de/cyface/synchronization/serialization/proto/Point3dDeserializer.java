package de.cyface.synchronization.serialization.proto;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import de.cyface.persistence.model.Point3d;
import de.cyface.persistence.serialization.Point3dType;
import de.cyface.persistence.serialization.proto.Point3dOffsetter;
import de.cyface.protos.model.Accelerations;
import de.cyface.protos.model.Directions;
import de.cyface.protos.model.Rotations;

public class Point3dDeserializer {

    public static List<Point3d> deserialize(Accelerations entries) {

        final List<Point3dOffsetter.Point3d> list = new ArrayList<>();
        for (int i = 0; i < entries.getTimestampCount(); i++) {
            final Point3dOffsetter.Point3d entry = new Point3dOffsetter.Point3d(entries.getTimestamp(i),
                    entries.getX(i), entries.getY(i), entries.getZ(i));
            list.add(entry);
        }

        return deserialize(list, Point3dType.ACCELERATION);
    }

    public static List<Point3d> deserialize(Rotations entries) {

        final List<Point3dOffsetter.Point3d> list = new ArrayList<>();
        for (int i = 0; i < entries.getTimestampCount(); i++) {
            final Point3dOffsetter.Point3d entry = new Point3dOffsetter.Point3d(entries.getTimestamp(i),
                    entries.getX(i), entries.getY(i), entries.getZ(i));
            list.add(entry);
        }

        return deserialize(list, Point3dType.ROTATION);
    }

    public static List<Point3d> deserialize(Directions entries) {

        final List<Point3dOffsetter.Point3d> list = new ArrayList<>();
        for (int i = 0; i < entries.getTimestampCount(); i++) {
            final Point3dOffsetter.Point3d entry = new Point3dOffsetter.Point3d(entries.getTimestamp(i),
                    entries.getX(i), entries.getY(i), entries.getZ(i));
            list.add(entry);
        }

        return deserialize(list, Point3dType.DIRECTION);
    }

    private static List<Point3d> deserialize(List<Point3dOffsetter.Point3d> entries, Point3dType type) {

        // The de-offsetter must be initialized once for each location
        final Point3dDeOffsetter deOffsetter = new Point3dDeOffsetter();

        return entries.stream().map(entry -> {

            // The proto serialized comes in a different format and in offset-format
            final Point3dOffsetter.Point3d offsets = new Point3dOffsetter.Point3d(entry.getTimestamp(), entry.getX(),
                    entry.getY(), entry.getZ());
            final Point3dOffsetter.Point3d absolutes = deOffsetter.absolute(offsets);
            final DeFormatter.Point3d deFormatted = new DeFormatter.Point3d(type,
                    absolutes.getTimestamp(), absolutes.getX(), absolutes.getY(), absolutes.getZ());

            return new Point3d(deFormatted.getX(), deFormatted.getY(), deFormatted.getZ(),
                    deFormatted.getTimestamp());

        }).collect(Collectors.toList());
    }
}
