package de.cyface.datacapturing;

import java.util.Locale;

/**
 * <p>
 * A position captured by the {@link DataCapturingService}
 * </p>
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
public class GpsPosition {
    /**
     * <p>
     * The captured latitude of this {@code GpsPosition} in decimal coordinates as a value between -90.0 (south pole)
     * and 90.0 (north pole).
     * </p>
     */
    private final double lat;
    /**
     * <p>
     * The captured longitude of this {@code GpsPosition} in decimal coordinates as a value between -180.0 and 180.0.
     * </p>
     */
    private final double lon;
    /**
     * <p>
     * The current speed of the measuring device according to its location sensor in meters per second.
     * </p>
     */
    private final double speed;
    /**
     * <p>
     * The current accuracy of the measuring device in meters.
     * </p>
     */
    private final float accuracy;

    /**
     * <p>
     * Creates a new completely initialized GpsPosition.
     * </p>
     * 
     * @param lat The captured latitude of this GpsPosition in decimal coordinates as a value between -90.0 (south pole)
     *            and 90.0 (north pole).
     * @param lon The captured longitude of this {@code GpsPosition} in decimal coordinates as a value between -180.0
     *            and 180.0.
     * @param speed The current speed of the measuring device according to its location sensor in meters per second.
     * @param accuracy The current accuracy of the measuring device in meters.
     */
    public GpsPosition(final double lat, final double lon, final double speed, final float accuracy) {
        if (lat < -90. || lat > 90.) {
            throw new IllegalArgumentException(String
                    .format(Locale.US, "Illegal value for latitude. Is required to be between -90.0 and 90.0 but was %d", lat));
        }
        if (lon < -180. || lon > 180.) {
            throw new IllegalArgumentException(String
                    .format(Locale.US, "Illegal value for longitude. Is required to be between -180.0 and 180.0 but was %d", lon));
        }
        if (speed < 0.) {
            throw new IllegalArgumentException(
                    String.format(Locale.US, "Illegal value for speed. Is required to be positive but was %d", speed));
        }
        if (accuracy < 0.) {
            throw new IllegalArgumentException(
                    String.format(Locale.US, "Illegal value for accuracy. Is required to be positive but was %d", accuracy));
        }

        this.lat = lat;
        this.lon = lon;
        this.speed = speed;
        this.accuracy = accuracy;
    }

    /**
     * @return The captured latitude of this GpsPosition in decimal coordinates as a value between -90.0 (south pole)
     *         and 90.0 (north pole).
     */
    public double getLat() {
        return lat;
    }

    /**
     * @return The captured longitude of this {@code GpsPosition} in decimal coordinates as a value between -180.0 and
     *         180.0.
     */
    public double getLon() {
        return lon;
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
