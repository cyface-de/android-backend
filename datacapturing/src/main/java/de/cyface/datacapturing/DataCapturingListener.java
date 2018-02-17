package de.cyface.datacapturing;

/**
 * <p>
 * An interface for a listener, listening for data capturing events. This listener can be registered with a
 * {@link DataCapturingService} via {@link DataCapturingService#startCapturing(DataCapturingListener)}.
 * </p>
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
public interface DataCapturingListener {
    /**
     * <p>
     * Called everytime the capturing service received a GPS Fix and thus is able to track its position.
     * </p>
     */
    void onFixAcquired();

    /**
     * <p>
     * Called everytime the capturing service loses its GPS Fix.
     * </p>
     */
    void onFixLost();

    /**
     * <p>
     * This method is called each time the data capturing service receives a new GPS position.
     * </p>
     * 
     * @param position The new geo location position.
     */
    void onNewGeoLocationAcquired(GeoLocation position);

    /**
     * <p>
     * This method is called each time the application runs out of space. How much space is used and how much is
     * available may be retrieved from {@code allocation}.
     * </p>
     * 
     * @param allocation Information about the applications disk (or rather SD card) space consumption.
     */
    void onLowDiskSpace(DiskConsumption allocation);

    /**
     * <p>
     * Invoked each time the {@link DataCapturingService} requires some permission from the Android system. That way it
     * is possible to show the user some explanation as to why that permission is required.
     * </p>
     *
     * @param permission The permission the service requires in the form of an Android permission {@link String}.
     * @param reason A reason for why the service requires that permission. You may show the reason to the user before
     *            asking for the permission or create your own message from it.
     * @return {@code true} if the permission has been granted; {@code false} otherwise.
     */
    boolean onRequirePermission(String permission, Reason reason);

    /**
     * <p>
     * Invoked if the service has synchronized all pending cached data successfully and deleted the local copies.
     * </p>
     */
    void onSynchronizationSuccessful();
}
