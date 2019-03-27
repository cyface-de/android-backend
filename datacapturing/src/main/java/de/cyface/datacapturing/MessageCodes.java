package de.cyface.datacapturing;

import androidx.annotation.NonNull;
import de.cyface.datacapturing.backend.DataCapturingBackgroundService;

/**
 * This class is a wrapper for all message codes used by the Cyface backend to send inner- and inter process
 * communication (IPC) messages.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 3.0.0
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
    public static final int GEOLOCATION_FIX = 6;
    /**
     * The code for messages sent from the {@link de.cyface.datacapturing.backend.DataCapturingBackgroundService} to the
     * {@link DataCapturingService} if it believes it lost the geo location fix.
     */
    public static final int NO_GEOLOCATION_FIX = 7;
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
     * Global Broadcast (inter-process) action identifier for service started messages sent by the
     * {@link DataCapturingBackgroundService} to the {@link DataCapturingService}.
     */
    private static final String GLOBAL_BROADCAST_SERVICE_STARTED = "de.cyface.service_started";
    /**
     * Global Broadcast (inter-process) action identifier for ping messages sent by the
     * {@link DataCapturingService}'s {@link PongReceiver} to the
     * {@link de.cyface.datacapturing.backend.PingReceiver}, to check if the {@link DataCapturingBackgroundService} is
     */
    private static final String GLOBAL_BROADCAST_PING = "de.cyface.ping";
    /**
     * Global Broadcast (inter-process) action identifier for pong messages sent by the
     * {@link de.cyface.datacapturing.backend.PingReceiver} as answer to a received ping.
     */
    private static final String GLOBAL_BROADCAST_PONG = "de.cyface.pong";

    /**
     * To avoid collision between sdk integrating apps use the app unique device id as prefix when using
     * this a action identifier for global broadcasts (for inter process communication)
     * 
     * @param appId A device-wide unique identifier for the application containing this SDK such as
     *            {@code Context#getPackageName()} which is required to generate unique global broadcasts for this app.
     *            <b>Attention:</b> The identifier must be identical in the global broadcast sender and receiver.
     */
    @NonNull
    public static String getServiceStartedActionId(@NonNull final String appId) {
        return appId + "_" + GLOBAL_BROADCAST_SERVICE_STARTED;
    }

    /**
     * To avoid collision between sdk integrating apps use the app unique device id as prefix when using
     * this a action identifier for global broadcasts (for inter process communication)
     *
     * @param appId A device-wide unique identifier for the application containing this SDK such as
     *            {@code Context#getPackageName()} which is required to generate unique global broadcasts for this app.
     *            <b>Attention:</b> The identifier must be identical in the global broadcast sender and receiver.
     */
    @NonNull
    public static String getPingActionId(@NonNull final String appId) {
        return appId + "_" + GLOBAL_BROADCAST_PING;
    }

    /**
     * To avoid collision between sdk integrating apps use the app unique device id as prefix when using
     * this a action identifier for global broadcasts (for inter process communication)
     *
     * @param appId A device-wide unique identifier for the application containing this SDK such as
     *            {@code Context#getPackageName()} which is required to generate unique global broadcasts for this app.
     *            <b>Attention:</b> The identifier must be identical in the global broadcast sender and receiver.
     */
    @NonNull
    public static String getPongActionId(@NonNull final String appId) {
        return appId + "_" + GLOBAL_BROADCAST_PONG;
    }

    /**
     * Local (i.e. inner process communication) Broadcast action identifier sent by the {@link DataCapturingService}
     * after it has received a inter-process {@link MessageCodes#SERVICE_STOPPED} from the
     * {@link DataCapturingBackgroundService} that it has successfully stopped.
     */
    static final String LOCAL_BROADCAST_SERVICE_STOPPED = "de.cyface.service_stopped";

    /**
     * Private constructor for utility class.
     */
    private MessageCodes() {
        // nothing to do here.
    }
}
