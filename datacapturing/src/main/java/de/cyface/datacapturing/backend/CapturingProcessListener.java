package de.cyface.datacapturing.backend;

import android.location.LocationManager;

import androidx.annotation.NonNull;
import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.model.CapturedData;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.GeoLocationV6;

/**
 * Interface for all classes that need to listen to events sent by a <code>CapturingProcess</code>.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 5.0.3
 * @since 1.0.0
 */
public interface CapturingProcessListener {
    /**
     * Called every time a new data point with a valid geo location coordinate has been captured.
     *
     * @param location Captured data wrapper object.
     * @param locationV6 Captured location with altitude data.
     */
    void onLocationCaptured(@NonNull GeoLocation location, @NonNull GeoLocationV6 locationV6);

    /**
     * Transmits the accelerations, rotations and directions captured in intervals of approximately one geo
     * location fix or one second if no fix occurs..
     *
     * @param data The data captured covering a list of accelerations, rotations and directions.
     */
    void onDataCaptured(@NonNull CapturedData data) throws DataCapturingException;

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
