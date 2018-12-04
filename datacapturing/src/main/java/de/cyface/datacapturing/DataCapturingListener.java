package de.cyface.datacapturing;

import de.cyface.datacapturing.backend.DataCapturingBackgroundService;
import de.cyface.datacapturing.model.CapturedData;
import de.cyface.datacapturing.ui.Reason;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Vehicle;

/**
 * An interface for a listener, listening for data capturing events. This listener can be registered with a
 * {@link DataCapturingService} via
 * {@link DataCapturingService#startAsync(DataCapturingListener, Vehicle, StartUpFinishedHandler)} .
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.3.1
 * @since 1.0.0
 */
public interface DataCapturingListener {
    /**
     * Called everytime the capturing service received a location Fix and thus is able to track its position.
     */
    void onFixAcquired();

    /**
     * Called everytime the capturing service loses its location Fix.
     */
    void onFixLost();

    /**
     * This method is called each time the data capturing service receives a new geo location.
     *
     * @param position The new geo location position.
     */
    void onNewGeoLocationAcquired(GeoLocation position);

    /**
     * This method is called each time the data capturing service receives new sensor data.
     *
     * @param data The newly received sensor data.
     */
    void onNewSensorDataAcquired(CapturedData data);

    /**
     * This method is called each time the application runs out of space. How much space is used and how much is
     * available may be retrieved from {@code allocation}.
     *
     * @param allocation Information about the applications disk (or rather SD card) space consumption.
     */
    void onLowDiskSpace(DiskConsumption allocation);

    /**
     * Invoked if the service has synchronized all pending cached data successfully and updated the local copies.
     */
    void onSynchronizationSuccessful();

    /**
     * Called when an error has been received by the data capturing background service.
     *
     * @param e An <code>Exception</code> representing the received error.
     */
    void onErrorState(Exception e);

    /**
     * Called if the service notices missing permissions required to run.
     * 
     * @param permission The permission the service requires in the form of an Android permission {@link String}.
     * @param reason A reason for why the service requires that permission. You may show the reason to the user before
     *            asking for the permission or create your own message from it.
     *
     * @return <code>true</code> if the permission was granted; <code>false</code> otherwise.
     */
    boolean onRequiresPermission(String permission, Reason reason);

    /**
     * This method is called when the capturing stopped. This can occurs when a {@link EventHandlingStrategy}
     * was implemented which stops the {@link DataCapturingBackgroundService} when the space is low.
     */
    void onCapturingStopped();
}
