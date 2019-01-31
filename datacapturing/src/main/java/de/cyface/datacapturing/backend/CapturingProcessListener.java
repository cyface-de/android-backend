package de.cyface.datacapturing.backend;

import android.location.LocationManager;
import de.cyface.datacapturing.model.CapturedData;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.datacapturing.exception.DataCapturingException;

/**
 * Interface for all classes that need to listen to events sent by a <code>CapturingProcess</code>.
 *
 * @author Klemens Muthmann
 * @version 5.0.2
 * @since 1.0.0
 */
public interface CapturingProcessListener {
    /**
     * Called every time a new data point with a valid geo location coordinate has been captured.
     *
     * @param location Captured data wrapper object.
     */
    void onLocationCaptured(GeoLocation location);

    /**
     * Transmits the accelerations, rotations and directions captured in intervals of approximately one geo
     * location fix or one second if no fix occurs..
     *
     * @param data The data captured covering a list of accelerations, rotations and directions.
     */
    void onDataCaptured(CapturedData data) throws DataCapturingException;

    /**
     * Called when the {@link LocationManager} this object is registered with thinks there was a successful
     * geo location fix.
     */
    void onLocationFix();

    /**
     * Called when the {@link LocationManager} this object is registered with thinks geo location fix was lost.
     */
    void onLocationFixLost();
}
