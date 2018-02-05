package de.cyface.datacapturing.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Immutable data handling object for captured data.
 *
 * @author Klemens Muthmann
 * @version 2.0.0
 * @since 1.0.0
 */
public class CapturedData implements Parcelable {

    /**
     * The latitude of the captured data in decimal degrees. This is a value between -90.0° south and +90.0° north.
     */
    private final double lat;
    /**
     * The longitude of the captured data in decimal degrees. This is a value between -180.0 (west) and 180.0 (east).
     */
    private final double lon;
    // TODO Which unit is used for the following. Unix timestamp?
    /**
     * The time this data was captured at.
     */
    private final long gpsTime;
    /**
     * The speed the capturing device was traveling at when the data was captured.
     */
    private final double gpsSpeed;
    /**
     * The location accuracy of <code>lat</code> and <code>lon</code>.
     */
    private final int gpsAccuracy;
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
     * @param lat The latitude as captured by the GPS.
     * @param lon The longitude as captured by the GPS.
     * @param gpsTime The time as specified by the GPS sensor.
     * @param gpsSpeed The speed according to the GPS sensor.
     * @param gpsAccuracy The accuracy according to the GPS sensor.
     * @param accelerations The raw acceleration values as points in a 3D space.
     *            The list contains all captured values since the last GPS fix.
     * @param rotations The raw rotational acceleration values as returned by the gyroscope.
     *            - * The list contains all captured values since the last GPS fix.
     * @param directions The intensity of the earth's magnetic field on each of the three axis in space.
     *            The list contains all captured values since the last GPS fix.
     */
    public CapturedData(final double lat, final double lon, final long gpsTime, final double gpsSpeed,
                        final int gpsAccuracy, final List<Point3D> accelerations, final List<Point3D> rotations,
                        final List<Point3D> directions) {
        this.lat = lat;
        this.lon = lon;
        this.gpsTime = gpsTime;
        this.gpsSpeed = gpsSpeed;
        this.gpsAccuracy = gpsAccuracy;
        this.accelerations = new LinkedList<>(accelerations);
        this.rotations = new LinkedList<>(rotations);
        this.directions = new LinkedList<>(directions);
    }


    /**
     * @return The latitude of the captured data in decimal degrees. This is a value between -90.0° south and +90.0° north.
     */
    public double getLat() {
        return lat;
    }

    /**
     * @return The longitude of the captured data in decimal degrees. This is a value between -180.0 (west) and 180.0 (east).
     */
    public double getLon() {
        return lon;
    }

    /**
     * @return The time this data was captured at.
     */
    public long getGpsTime() {
        return gpsTime;
    }

    /**
     * @return The speed the capturing device was traveling at when the data was captured.
     */
    public double getGpsSpeed() {
        return gpsSpeed;
    }

    /**
     * @return The location accuracy of <code>lat</code> and <code>lon</code>.
     */
    public int getGpsAccuracy() {
        return gpsAccuracy;
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
        lat = in.readDouble();
        lon = in.readDouble();
        gpsTime = in.readLong();
        gpsSpeed = in.readDouble();
        gpsAccuracy = in.readInt();
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
        dest.writeDouble(lat);
        dest.writeDouble(lon);
        dest.writeLong(gpsTime);
        dest.writeDouble(gpsSpeed);
        dest.writeInt(gpsAccuracy);
        dest.writeTypedList(accelerations);
        dest.writeTypedList(rotations);
        dest.writeTypedList(directions);
    }

    /*
     * MARK: Object Methods
     */

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CapturedData that = (CapturedData) o;

        if (Double.compare(that.lat, lat) != 0) return false;
        if (Double.compare(that.lon, lon) != 0) return false;
        if (gpsTime != that.gpsTime) return false;
        if (Double.compare(that.gpsSpeed, gpsSpeed) != 0) return false;
        if (gpsAccuracy != that.gpsAccuracy) return false;
        if (!accelerations.equals(that.accelerations)) return false;
        if (!rotations.equals(that.rotations)) return false;
        return directions.equals(that.directions);

    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        temp = Double.doubleToLongBits(lat);
        result = (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(lon);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (int) (gpsTime ^ (gpsTime >>> 32));
        temp = Double.doubleToLongBits(gpsSpeed);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + gpsAccuracy;
        result = 31 * result + accelerations.hashCode();
        result = 31 * result + rotations.hashCode();
        result = 31 * result + directions.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "CapturedData{" +
                "lat=" + lat +
                ", lon=" + lon +
                ", gpsTime=" + gpsTime +
                ", gpsSpeed=" + gpsSpeed +
                ", gpsAccuracy=" + gpsAccuracy +
                ", accelerations=" + accelerations +
                ", rotations=" + rotations +
                ", directions=" + directions +
                '}';
    }
}
