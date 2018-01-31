package de.cyface.datacapturing.backend;

import android.location.LocationManager;

import de.cyface.datacapturing.model.CapturedData;

/**
 * <p>
 * Interface for all classes that need to listen to events sent by a <code>CapturingProcess</code>.
 * </p>
 *
 * @author Klemens Muthmann
 * @version 3.0.0
 * @since 1.0.0
 */
public interface CapturingProcessListener {
    /**
     * <p>
     * Called every time a new data point with a valid GPS coordinate has been captured. Transmitted alongside are
     * the accelerations, rotations and magnetic values captured since the last fix.
     * </p>
     *
     * @param data Captured data wrapper object.
     */
    void onPointCaptured(final CapturedData data);
    /**
     * <p>
     * Called when the {@link LocationManager} this object is registered with thinks there was a successful
     * GPS fix.
     * </p>
     */
    void onGpsFix();

    /**
     * <p>
     * Called when the {@link LocationManager} this object is registered with thinks GPS fix was lost.
     * </p>
     */
    void onGpsFixLost();
}
