package de.cyface.datacapturing.backend;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Abstract base class for classes informing the system about the current state of the geo location device. It reacts to fix events and if those events occur often enough it tells its <code>CapturingProcessListener</code>s about the state change.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
public abstract class GPSStatusHandler {
    private static final int MAX_TIME_SINCE_LAST_SATELLITE_UPDATE = 2000;

    private boolean hasGpsFix;
    private long timeOfLastLocationUpdate;
    private final Collection<CapturingProcessListener> listener = new ArrayList<>();

    void setDataCapturingListener(final Collection<CapturingProcessListener> listener) {
        this.listener.addAll(listener);
    }

    boolean hasGpsFix() {
        return hasGpsFix;
    }

    void setTimeOfLastLocationUpdate(final long timeOfLastLocationUpdate) {
        this.timeOfLastLocationUpdate = timeOfLastLocationUpdate;
    }

    abstract void shutdown();

    void handleSatteliteStatusChange() {
        // If time of last location update was less then 2 seconds we still have a fix.
        long timePassedSinceLastSatteliteUpdate = System.currentTimeMillis() - timeOfLastLocationUpdate;
        hasGpsFix = timePassedSinceLastSatteliteUpdate < MAX_TIME_SINCE_LAST_SATELLITE_UPDATE;
    }

    void handleFirstFix() {
        hasGpsFix = true;
        handleGpsFixEvent();
    }

    private void handleGpsFixEvent() {
        if (hasGpsFix) {
            for (CapturingProcessListener listener : this.listener) {
                listener.onGpsFix();
            }
        } else {
            for (CapturingProcessListener listener : this.listener) {
                listener.onGpsFixLost();
            }
        }
    }
}
