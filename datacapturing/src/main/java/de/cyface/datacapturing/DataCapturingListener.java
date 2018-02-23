package de.cyface.datacapturing;

/**
 * An interface for a listener, listening for data capturing events. This listener can be registered with a
 * {@link DataCapturingService} via
 * {@link DataCapturingService#start(DataCapturingListener,de.cyface.datacapturing.model.Vehicle)}.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
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
}
