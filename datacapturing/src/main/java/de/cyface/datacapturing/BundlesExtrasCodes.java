package de.cyface.datacapturing;

/**
 * A utility class collecting all codes identifying extras used to transmit data via bundles in this application.
 *
 * @author Klemens Muthmann
 * @since 2.0.2
 * @version 1.0.2
 */
public class BundlesExtrasCodes {
    /**
     * Code that identifys the extra transmitted to the background service to tell it which measurement to capture.
     */
    public static final String START_WITH_MEASUREMENT_ID = "de.cyface.extra.mid";
    /**
     * Code that identifys the extra transmitted between ping and pong messages to associated a ping with a pong, while
     * checking whether the service is running.
     */
    public static final String PING_PONG_ID = "de.cyface.pingpong.id";

    public static final String AUTHORITY_ID = "de.cyface.authority.id";

    /**
     * Constructor is private to prevent creation of utility class.
     */
    private BundlesExtrasCodes() {
        // Nothing to do here.
    }
}
