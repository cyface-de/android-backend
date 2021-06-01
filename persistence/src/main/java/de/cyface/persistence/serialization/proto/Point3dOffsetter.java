package de.cyface.persistence.serialization.proto;

import org.apache.commons.lang3.Validate;

public class Point3dOffsetter {
    private final Offsetter ts;
    private final Offsetter x;
    private final Offsetter y;
    private final Offsetter z;

    public Point3dOffsetter() {
        ts = new Offsetter();
        x = new Offsetter();
        y = new Offsetter();
        z = new Offsetter();
    }

    public Point3d offset(Formatter.Point3d point) {
        final long timestamp = ts.offset(point.getTimestamp());
        final long xValue = x.offset(point.getX());
        final long yValue = y.offset(point.getY());
        final long zValue = z.offset(point.getZ());
        Validate.isTrue(xValue <= Integer.MAX_VALUE);
        Validate.isTrue(yValue <= Integer.MAX_VALUE);
        Validate.isTrue(zValue <= Integer.MAX_VALUE);
        return new Point3d(timestamp, (int)xValue, (int)yValue, (int)zValue);
    }

    public static class Point3d {
        private final long timestamp;
        private final int x;
        private final int y;
        private final int z;

        public Point3d(long timestamp, int x, int y, int z) {
            this.timestamp = timestamp;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getZ() {
            return z;
        }
    }
}