package de.cyface.datacapturing;

import java.util.Locale;

/**
 * <p>
 * A position captured by the {@link DataCapturingService}.
 * </p>
 *
 * @author Klemens Muthmann
 * @version 1.1.0
 * @since 1.0.0
 */
public class GeoLocation {
    /**
     * <p>
     * The captured latitude of this {@code GeoLocation} in decimal coordinates as a value between -90.0 (south pole)
     * and 90.0 (north pole).
     */
    private final double lat;
    /**
     * The captured longitude of this {@code GeoLocation} in decimal coordinates as a value between -180.0 and 180.0.
     */
    private final double lon;
    /**
     * The timestamp at which this <code>GeoLocation</code> was captured in milliseconds since 1.1.1970.
     */
    private final long timestamp;
    /**
     * The current speed of the measuring device according to its location sensor in meters per second.
     */
    private final double speed;
    /**
     * The current accuracy of the measuring device in meters.
     */
    private final float accuracy;

    /**
     * Creates a new completely initialized <code>GeoLocation</code>.
     *
     * @param lat The captured latitude of this GeoLocation in decimal coordinates as a value between -90.0 (south pole)
     *            and 90.0 (north pole).
     * @param lon The captured longitude of this {@code GeoLocation} in decimal coordinates as a value between -180.0
     *            and 180.0.
     * @param timestamp The timestamp at which this <code>GeoLocation</code> was captured in milliseconds since 1.1.1970.
     * @param speed The current speed of the measuring device according to its location sensor in meters per second.
     * @param accuracy The current accuracy of the measuring device in meters.
     */
    public GeoLocation(final double lat, final double lon, final long timestamp, final double speed,
            final float accuracy) {
        if (lat < -90. || lat > 90.) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Illegal value for latitude. Is required to be between -90.0 and 90.0 but was %d.", lat));
        }
        if (lon < -180. || lon > 180.) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Illegal value for longitude. Is required to be between -180.0 and 180.0 but was %d.", lon));
        }
        if (speed < 0.) {
            throw new IllegalArgumentException(
                    String.format(Locale.US, "Illegal value for speed. Is required to be positive but was %d.", speed));
        }
        if (accuracy < 0.) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Illegal value for accuracy. Is required to be positive but was %d.", accuracy));
        }
        if (timestamp < 0L) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Illegal value for timestamp. Is required to be greater then 0L but was %d.", timestamp));
        }

        this.lat = lat;
        this.lon = lon;
        this.timestamp = timestamp;
        this.speed = speed;
        this.accuracy = accuracy;
    }

    /**
     * @return The captured latitude of this GeoLocation in decimal coordinates as a value between -90.0 (south pole)
     *         and 90.0 (north pole).
     */
    public double getLat() {
        return lat;
    }

    /**
     * @return The captured longitude of this {@code GeoLocation} in decimal coordinates as a value between -180.0 and
     *         180.0.
     */
    public double getLon() {
        return lon;
    }

    /**
     * @return The timestamp at which this <code>GeoLocation</code> was captured in milliseconds since 1.1.1970.
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * @return The current speed of the measuring device according to its location sensor in meters per second.
     */
    public double getSpeed() {
        return speed;
    }

    /**
     * @return The current accuracy of the measuring device in meters.
     */
    public float getAccuracy() {
        return accuracy;
    }
}
