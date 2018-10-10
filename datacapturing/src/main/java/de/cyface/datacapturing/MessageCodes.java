package de.cyface.datacapturing;

import android.os.Message;

import de.cyface.datacapturing.backend.DataCapturingBackgroundService;

/**
 * This class is a wrapper for all message codes used by the Cyface backend to send inner- and inter process
 * communication (IPC) messages.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.2
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
    /**
     * The code for inter-process {@link Message}s sent from the {@link DataCapturingBackgroundService} to the
     * {@link DataCapturingService} when the background service started.
     */
    public static final int SERVICE_STARTED = 12;

    /**
     * Global Broadcast (inter-process) action identifier for ping messages sent by the
     * {@link DataCapturingService}'s {@link PongReceiver} to the
     * {@link DataCapturingBackgroundService#pingReceiver}, to check if the {@link DataCapturingBackgroundService} is
     * alive.
     *
     * @deprecated because global broadcasts are a security risk and interfere with multiply apps integrating
     *             out SDK ! FIXME in CY-3575 !
     */
    public static final String GLOBAL_BROADCAST_PING = "de.cyface.ping";
    /**
     * Global Broadcast (inter-process) action identifier for pong messages sent by the
     * {@link DataCapturingBackgroundService#pingReceiver} as answer to a received ping.
     *
     *
     * @deprecated because global broadcasts are a security risk and interfere with multiply apps integrating
     *             out SDK ! FIXME in CY-3575 !
     */
    public static final String GLOBAL_BROADCAST_PONG = "de.cyface.pong";
    /**
     * Global Broadcast action identifier sent by the {@link DataCapturingBackgroundService} to the
     * {@link DataCapturingService}'s {@link StartUpFinishedHandler} after it
     * has successfully started.
     * 
     * @deprecated because global broadcasts are a security risk and interfere with multiply apps integrating
     *             out SDK ! FIXME in CY-3575 !
     */
    public static final String GLOBAL_BROADCAST_SERVICE_STARTED = "de.cyface.service_started";
    /**
     * Local (i.e. inner process communication) Broadcast action identifier sent by the {@link DataCapturingService}
     * after it has received a inter-process {@link MessageCodes#SERVICE_STOPPED} from the
     * {@link DataCapturingBackgroundService} that it has successfully stopped.
     */
    public static final String LOCAL_BROADCAST_SERVICE_STOPPED = "de.cyface.service_stopped";

    /**
     * Private constructor for utility class.
     */
    private MessageCodes() {
        // nothing to do here.
    }
}
