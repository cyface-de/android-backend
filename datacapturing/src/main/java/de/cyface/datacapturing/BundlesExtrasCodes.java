package de.cyface.datacapturing;

import de.cyface.datacapturing.backend.DataCapturingBackgroundService;
import de.cyface.persistence.serialization.AccelerationsFile;
import de.cyface.persistence.serialization.DirectionsFile;
import de.cyface.persistence.serialization.GeoLocationsFile;
import de.cyface.persistence.serialization.RotationsFile;

/**
 * A utility class collecting all codes identifying extras used to transmit data via bundles in this application.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.1.0
 * @since 2.1.0
 */
public class BundlesExtrasCodes {
    /**
     * Code that identifies the extra transmitted to the background service to tell it which measurement to capture.
     */
    public static final String MEASUREMENT_ID = "de.cyface.extra.mid";
    /**
     * Code that identifies the extra transmitted between ping and pong messages to associated a ping with a pong, while
     * checking whether the service is running.
     */
    public static final String PING_PONG_ID = "de.cyface.pingpong.id";
    /**
     * Code that identifies the authority id if transmitted via an Android bundle.
     */
    public static final String AUTHORITY_ID = "de.cyface.authority.id";
    /**
     * Code that identifies the status information in the message send when the data capturing service has been stopped.
     * This should be a boolean extra that is <code>false</code> if the service was not actually stopped (if stop is
     * called twice in succession) or <code>true</code> is stopping was successful. In the first case the intent should
     * also contain the measurement id as an extra identified by {@link #MEASUREMENT_ID}.
     */
    public static final String STOPPED_SUCCESSFULLY = "de.cyface.extra.stopped_successfully";
    /**
     * Code that identifies the {@link EventHandlingStrategy} if transmitted via an Android bundle.
     */
    public static final String EVENT_HANDLING_STRATEGY_ID = "de.cyface.event_handling_strategy.id";
    /**
     * Code that contains the number of entries stored in the {@link GeoLocationsFile} captured. This allows the
     * {@link DataCapturingService} to keep the number of points stored when a measurement was paused and to resume the
     * counting by passing it back to the {@link DataCapturingBackgroundService} on resume.
     */
    public static final String GEOLOCATION_COUNT = "de.cyface.extra.geolocation.count";
    /**
     * Code that contains the number of entries stored in the {@link AccelerationsFile} captured. This allows the
     * {@link DataCapturingService} to keep the number of points stored when a measurement was paused and to resume the
     * counting by passing it back to the {@link DataCapturingBackgroundService} on resume.
     */
    public static final String ACCELERATION_POINT_COUNT = "de.cyface.extra.acceleration_point.count";
    /**
     * Code that contains the number of entries stored in the {@link RotationsFile} captured. This allows the
     * {@link DataCapturingService} to keep the number of points stored when a measurement was paused and to resume the
     * counting by passing it back to the {@link DataCapturingBackgroundService} on resume.
     */
    public static final String ROTATION_POINT_COUNT = "de.cyface.extra.rotation_point.count";
    /**
     * Code that contains the number of entries stored in the {@link DirectionsFile} captured. This allows the
     * {@link DataCapturingService} to keep the number of points stored when a measurement was paused and to resume the
     * counting by passing it back to the {@link DataCapturingBackgroundService} on resume.
     */
    public static final String DIRECTION_POINT_COUNT = "de.cyface.extra.direction_point.count";

    /**
     * Constructor is private to prevent creation of utility class.
     */
    private BundlesExtrasCodes() {
        // Nothing to do here.
    }
}
