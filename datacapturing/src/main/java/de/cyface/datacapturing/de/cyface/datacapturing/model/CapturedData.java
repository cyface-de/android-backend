package de.cyface.datacapturing.de.cyface.datacapturing.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * Immutable data handling object for captured data.
 * </p>
 *
 * @author Klemens Muthmann
 * @version 2.0.0
 * @since 1.0.0
 */
public class CapturedData implements Parcelable {

    private final double lat;
    private final double lon;
    private final long gpsTime;
    private final double gpsSpeed;
    private final int gpsAccuracy;
    private final List<Point3D> accelerations;
    private final List<Point3D> rotations;
    private final List<Point3D> directions;

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
}
