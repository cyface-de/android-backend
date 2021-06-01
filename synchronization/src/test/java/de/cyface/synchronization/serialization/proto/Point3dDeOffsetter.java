package de.cyface.synchronization.serialization.proto;

import org.apache.commons.lang3.Validate;

import de.cyface.persistence.serialization.proto.Point3dOffsetter;

public class Point3dDeOffsetter {
    private final DeOffsetter ts;
    private final DeOffsetter x;
    private final DeOffsetter y;
    private final DeOffsetter z;

    public Point3dDeOffsetter() {
        ts = new DeOffsetter();
        x = new DeOffsetter();
        y = new DeOffsetter();
        z = new DeOffsetter();
    }

    public Point3dOffsetter.Point3d absolute(Point3dOffsetter.Point3d point) {
        final long timestamp = ts.absolute(point.getTimestamp());
        final long xValue = x.absolute(point.getX());
        final long yValue = y.absolute(point.getY());
        final long zValue = z.absolute(point.getZ());
        Validate.isTrue(xValue <= Integer.MAX_VALUE);
        Validate.isTrue(yValue <= Integer.MAX_VALUE);
        Validate.isTrue(zValue <= Integer.MAX_VALUE);
        return new Point3dOffsetter.Point3d(timestamp, (int)xValue, (int)yValue, (int)zValue);
    }
}