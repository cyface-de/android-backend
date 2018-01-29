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

    public float[] toArray() {
        return new float[] {getX(), getY(), getZ()};
    }

    SamplePointJson SpToJson() {
        return new SamplePointJson(x, y, z, getTimestamp());
    }

    RotationPointJson RpToJson() {
        return new RotationPointJson(x, y, z, getTimestamp());
    }

    MagneticValuePointJson MpToJson() {
        return new MagneticValuePointJson(x, y, z, getTimestamp());
    }

    class SamplePointJson {
        private final float ax;
        private final float ay;
        private final float az;
        private final long timestamp;

        SamplePointJson(final float x, final float y, final float z, final long timestamp) {
            this.ax = x;
            this.ay = y;
            this.az = z;
            this.timestamp = timestamp;
        }

        /**
         * <p>All object attributes need getters so that jackson can generate the json object</p>
         * @return return the java object as json object string
         */
        @Override
        public String toString() {
            try {
                ObjectMapper mapper = new ObjectMapper();
                return mapper.writeValueAsString(this);
            } catch (IOException e) {
                Log.e(TAG, "Could not parse acceleration point object to JSON.");
                throw new IllegalStateException(e);
            }
        }

        public float getAx() {
            return ax;
        }

        public float getAy() {
            return ay;
        }

        public float getAz() {
            return az;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    class RotationPointJson {
        private final float rX;
        private final float rY;
        private final float rZ;
        private final long timestamp;

        RotationPointJson(final float x, final float y, final float z, final long timestamp) {
            this.rX = x;
            this.rY = y;
            this.rZ = z;
            this.timestamp = timestamp;
        }

        /**
         * <p>All object attributes need getters so that jackson can generate the json object</p>
         * @return return the java object as json object string
         */
        @Override
        public String toString() {
            try {
                ObjectMapper mapper = new ObjectMapper();
                return mapper.writeValueAsString(this);
            } catch (IOException e) {
                Log.e(TAG, "Could not parse rotation point object to JSON.");
                throw new IllegalStateException(e);
            }
        }

        public float getrX() {
            return rX;
        }

        public float getrY() {
            return rY;
        }

        public float getrZ() {
            return rZ;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    class MagneticValuePointJson {
        private final float mX;
        private final float mY;
        private final float mZ;
        private final long timestamp;

        MagneticValuePointJson(final float x, final float y, final float z, final long timestamp) {
            this.mX = x;
            this.mY = y;
            this.mZ = z;
            this.timestamp = timestamp;
        }

        /**
         * <p>All object attributes need getters so that jackson can generate the json object</p>
         * @return return the java object as json object string
         */
        @Override
        public String toString() {
            try {
                ObjectMapper mapper = new ObjectMapper();
                return mapper.writeValueAsString(this);
            } catch (IOException e) {
                Log.e(TAG, "Could not parse magnetometer point object to JSON.");
                throw new IllegalStateException(e);
            }
        }

        public float getmX() {
            return mX;
        }

        public float getmY() {
            return mY;
        }

        public float getmZ() {
            return mZ;
        }

        public long getTimestamp() {
            return timestamp;
        }
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
