package de.cyface.datacapturing;

/**
 * This class is a wrapper for all message codes used by the Cyface backend to send inter process communication (IPC)
 * messages.
 *
 * @author Klemens Muthmann
 * @version 1.1.1
 * @since 2.0.0
 */
public class MessageCodes {
    /**
     * The code for messages sent from the {@link DataCapturingService} to the
     * {@link de.cyface.datacapturing.backend.DataCapturingBackgroundService} to register the former as client of the
     * latter.
     */
    public static final int REGISTER_CLIENT = 1;
    /**
     * The code for messages sent from the {@link de.cyface.datacapturing.backend.DataCapturingBackgroundService} to the
     * {@link DataCapturingService} every time a new geo location was captured.
     */
    public static final int LOCATION_CAPTURED = 4;
    /**
     * The code for messages sent from the {@link de.cyface.datacapturing.backend.DataCapturingBackgroundService} to the
     * {@link DataCapturingService} every time some sensor data was captured.
     */
    public static final int DATA_CAPTURED = 5;
    /**
     * The code for messages sent from the {@link de.cyface.datacapturing.backend.DataCapturingBackgroundService} to the
     * {@link DataCapturingService} if it believes it has a valid geo location fix.
     */
    public static final int GPS_FIX = 6;
    /**
     * The code for messages sent from the {@link de.cyface.datacapturing.backend.DataCapturingBackgroundService} to the
     * {@link DataCapturingService} if it believes it lost the geo location fix.
     */
    public static final int NO_GPS_FIX = 7;
    /**
     * The code for messages sent from the {@link de.cyface.datacapturing.backend.DataCapturingBackgroundService} to the
     * {@link DataCapturingService} if permission to access geo locations via satellite is not granted.
     */
    public static final int ERROR_PERMISSION = 9;
    /**
     * The code for messages sent from the {@link de.cyface.datacapturing.backend.DataCapturingBackgroundService} to the
     * {@link DataCapturingService} if the background service stopped (after being asked to from outside).
     */
    public static final int SERVICE_STOPPED = 10;
    /**
     * The code for messages sent from the {@link de.cyface.datacapturing.backend.DataCapturingBackgroundService} to the
     * {@link DataCapturingService} when the background service stopped itself. This can happen when
     * a {@link EventHandlingStrategy} was passed which stops the DataCapturingBackgroundService
     * when it notices that only little space is left.
     */
    public static final int SERVICE_STOPPED_ITSELF = 11;

    // TODO This needs to be qualified. We should for example add the application id.
    /**
     * Broadcast action identifier for ping messages sent to the
     * {@link de.cyface.datacapturing.backend.DataCapturingBackgroundService}, to check if it is alive.
     */
    public static final String ACTION_PING = "de.cyface.ping";
    /**
     * Broadcast action identifier for pong messages sent from the
     * {@link de.cyface.datacapturing.backend.DataCapturingBackgroundService} as answer to a received ping.
     */
    public static final String ACTION_PONG = "de.cyface.pong";
    /**
     * Broadcast action identifier sent by the {@link de.cyface.datacapturing.backend.DataCapturingBackgroundService}
     * after it has successfully started.
     */
    public static final String BROADCAST_SERVICE_STARTED = "de.cyface.service_started";
    /**
     * Broadcast action identifier sent by the {@link de.cyface.datacapturing.backend.DataCapturingBackgroundService}
     * after it has successfully stopped.
     */
    public static final String FINISHED_HANDLER_BROADCAST_SERVICE_STOPPED = "de.cyface.service_stopped";

    /**
     * Private constructor for utility class.
     */
    private MessageCodes() {
        // nothing to do here.
    }
}
