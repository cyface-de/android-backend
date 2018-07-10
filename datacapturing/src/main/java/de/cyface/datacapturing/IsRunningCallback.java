package de.cyface.datacapturing;

import java.util.concurrent.TimeUnit;

/**
 * A callback for the {@link DataCapturingService#isRunning(long, TimeUnit, IsRunningCallback)} method. If the
 * background service is successfully notified the {@link #isRunning()} method is called otherwise {@link #timedOut()}
 * is called.
 *
 * @author Klemens Muthmann
 * @since 2.0.0
 * @version 1.0.0
 */

public interface IsRunningCallback {
    /**
     * Method called if the callback for whether the background service is running was notified of a running service
     */
    void isRunning();

    /**
     * Method called if the callback for whether the background service is running timed out. This does not necessarily
     * mean that the service is not running, but will in almost all cases.
     */
    void timedOut();
}
