package de.cyface.persistence.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * A measured {@code DataPoint} with three coordinates such as an acceleration-, rotation- or magnetic value point.
 *
 * @author Klemens Muthmann
 * @version 2.0.0
 * @since 1.0.0
 */
public final class Point3d extends DataPoint {
    /**
     * The x component of the data point.
     */
    private final float x;
    /**
     * The y component of the data point.
     */
    private final float y;
    /**
     * The z component of the data point.
     */
    private final float z;

    /**
     * Creates a new completely initialized <code>Point3d</code>.
     *
     * @param identifier The database wide unique identifier of this data point.
     * @param x The x component of the data point.
     * @param y The y component of the data point.
     * @param z The z component of the data point.
     * @param timestamp The time when this point was measured in milliseconds since 1.1.1970.
     */
    public Point3d(final Long identifier, final float x, final float y, final float z, final long timestamp) {
        super(identifier, timestamp);
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Creates a new completely initialized <code>Point3d</code>.
     *
     * @param x The x component of the data point.
     * @param y The y component of the data point.
     * @param z The z component of the data point.
     * @param timestamp The time when this point was measured in milliseconds since 1.1.1970.
     */
    public Point3d(final float x, final float y, final float z, final long timestamp) {
        this(null,x,y,z,timestamp);
    }

    /**
     * @return The x component of the data point.
     */
    public float getX() {
        return x;
    }

    /**
     * @return The y component of the data point.
     */
    public float getY() {
        return y;
    }

    /**
     * @return The z component of the data point.
     */
    public float getZ() {
        return z;
    }

    /*
     * MARK: Non Bean Code.
     */

    @Override
    public String toString() {
        return "Point X: " + x + "Y: " + y + "Z: " + z;
    }

    /*
     * MARK: Code For Parcelable Interface.
     */

    /**
     * Recreates this point from the provided <code>Parcel</code>.
     *
     * @param in Serialized form of a <code>Point3d</code>.
     */
    public Point3d(final Parcel in) {
        super(in);
        this.x = in.readFloat();
        this.y = in.readFloat();
        this.z = in.readFloat();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeFloat(x);
        dest.writeFloat(y);
        dest.writeFloat(z);
    }

    /**
     * The <code>Parcelable</code> creator as required by the Android Parcelable specification.
     */
    public static final Parcelable.Creator<Point3d> CREATOR = new Parcelable.Creator<Point3d>() {

        @Override
        public Point3d createFromParcel(Parcel source) {
            return new Point3d(source);
        }

        @Override
        public Point3d[] newArray(int size) {
            return new Point3d[size];
        }
    };
}
