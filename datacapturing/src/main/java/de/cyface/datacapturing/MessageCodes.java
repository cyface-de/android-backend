package de.cyface.datacapturing;

/**
 * This class is a wrapper for all message codes used by the Cyface backend to send inter process communication (IPC) messages.
 *
 * @version 1.0.0
 * @since 2.0.0
 */
public class MessageCodes {
    public static final int REGISTER_CLIENT = 1;
    public static final int POINT_CAPTURED = 5;
    public static final int GPS_FIX = 6;
    public static final int NO_GPS_FIX = 7;
    public static final int WARNING_SPACE = 8;

    /**
     * Private constructor for utility class.
     */
    private MessageCodes() {
        // nothing to do here.
    }
}
