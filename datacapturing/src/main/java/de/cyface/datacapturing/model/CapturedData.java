package de.cyface.datacapturing.model;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;

/**
 * Immutable data handling object for captured data.
 *
 * @author Klemens Muthmann
 * @version 3.0.1
 * @since 1.0.0
 */
public class CapturedData implements Parcelable {
    /**
     * All accelerations captured since the last position was captured.
     */
    private final List<Point3D> accelerations;
    /**
     * All rotations captured since the last position was captured.
     */
    private final List<Point3D> rotations;
    /**
     * All directions captured since the last position was captured.
     */
    private final List<Point3D> directions;

    /**
     * Creates a new captured data object from the provided data. The lists are copied and thus may be changed after
     * this constructor has been called without changes occurring in this object.
     * We are not using Collections.unmodifiableList(acc) as we experienced them NOT to be unmodifiable which resulted
     * into data loss. The reason for this could be that we are using parcelable which creates copies instead of
     * references.
     * We could try it with Serializable?
     *
     * @param accelerations The raw acceleration values as points in a 3D space.
     *            The list contains all captured values since the last GPS fix.
     * @param rotations The raw rotational acceleration values as returned by the gyroscope.
     *            - * The list contains all captured values since the last GPS fix.
     * @param directions The intensity of the earth's magnetic field on each of the three axis in space.
     *            The list contains all captured values since the last GPS fix.
     */
    public CapturedData(final @NonNull List<Point3D> accelerations, final @NonNull List<Point3D> rotations,
            final @NonNull List<Point3D> directions) {
        this.accelerations = new LinkedList<>(accelerations);
        this.rotations = new LinkedList<>(rotations);
        this.directions = new LinkedList<>(directions);
    }

    /**
     * @return All accelerations captured since the last position was captured.
     */
    public List<Point3D> getAccelerations() {
        return Collections.unmodifiableList(accelerations);
    }

    /**
     * @return All rotations captured since the last position was captured.
     */
    public List<Point3D> getRotations() {
        return Collections.unmodifiableList(rotations);
    }

    /**
     * @return All directions captured since the last position was captured.
     */
    public List<Point3D> getDirections() {
        return Collections.unmodifiableList(directions);
    }

    /*
     * MARK: Code for parcelable interface
     */

    /**
     * Recreates this object from the provided <code>Parcel</code>.
     *
     * @param in Serialized form of a <code>CapturedData</code> object.
     */
    protected CapturedData(Parcel in) {
        accelerations = in.createTypedArrayList(Point3D.CREATOR);
        rotations = in.createTypedArrayList(Point3D.CREATOR);
        directions = in.createTypedArrayList(Point3D.CREATOR);
    }

    /**
     * The <code>Parcelable</code> creator as required by the Android Parcelable specification.
     */
    public static final Creator<CapturedData> CREATOR = new Creator<CapturedData>() {
        @Override
        public CapturedData createFromParcel(Parcel in) {
            return new CapturedData(in);
        }

        @Override
        public CapturedData[] newArray(int size) {
            return new CapturedData[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(accelerations);
        dest.writeTypedList(rotations);
        dest.writeTypedList(directions);
    }

    /*
     * MARK: Object Methods
     */

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        CapturedData that = (CapturedData)o;

        if (!accelerations.equals(that.accelerations))
            return false;
        if (!rotations.equals(that.rotations))
            return false;
        return directions.equals(that.directions);

    }

    @Override
    public int hashCode() {
        int result = accelerations.hashCode();
        result = 31 * result + rotations.hashCode();
        result = 31 * result + directions.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "CapturedData{" + "accelerations=" + accelerations + ", rotations=" + rotations + ", directions="
                + directions + '}';
    }
}
