package de.cyface.datacapturing;

/**
 * This class is a wrapper for all message codes used by the Cyface backend to send inter process communication (IPC)
 * messages.
 *
 * @author Klemens Muthmann
 * @version 1.1.0
 * @since 2.0.0
 */
public class MessageCodes {
    public static final int REGISTER_CLIENT = 1;
    public static final int SERVICE_STOPPED = 2;
    public static final int LOCATION_CAPTURED = 4;
    public static final int DATA_CAPTURED = 5;
    public static final int GPS_FIX = 6;
    public static final int NO_GPS_FIX = 7;
    public static final int WARNING_SPACE = 8;

    // TODO This needs to be qualified. We should for example add the application id.
    public static final String ACTION_PING = "de.cyface.ping";
    public static final String ACTION_PONG = "de.cyface.pong";
    public static final String BROADCAST_SERVICE_STARTED = "de.cyface.service_started";
    public static final String BROADCAST_SERVICE_STOPPED = "de.cyface.service_stopped";

    /**
     * Private constructor for utility class.
     */
    private MessageCodes() {
        // nothing to do here.
    }
}
