package de.cyface.datacapturing.de.cyface.datacapturing.model;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.io.IOException;

import static android.content.ContentValues.TAG;

/**
 * <p>
 * A measured {@code DataPoint} with three coordinates such as an acceleration-, rotation- or magnetic value point.
 * </p>
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
public final class Point3D extends DataPoint {
    private final float x;
    private final float y;
    private final float z;

    public Point3D(final long identifier, final float x, final float y, final float z, final long timestamp) {
        super(identifier, timestamp);
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Point3D(final float x, final float y, final float z, final long timestamp) {
        super(null, timestamp);
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getZ() {
        return z;
    }

    /*
     * Non Bean Code.
     */

    @Override
    public String toString() {
        return "Point X: " + x + "Y: " + y + "Z: " + z;
    }

    /*
     * Code For Parcelable Interface.
     */

    public Point3D(final Parcel in) {
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

    public static final Parcelable.Creator<Point3D> CREATOR = new Parcelable.Creator<Point3D>() {

        @Override
        public Point3D createFromParcel(Parcel source) {
            return new Point3D(source);
        }

        @Override
        public Point3D[] newArray(int size) {
            return new Point3D[size];
        }
    };
}
