package de.cyface.datacapturing.ui;

import android.location.Location;

import de.cyface.datacapturing.DataCapturingService;

/**
 * A listener the user interface might register with the <code>MovebisDataCapturingListener</code> to be notified of
 * user interface relevant events.
 *
 * @author Klemens Muthmann
 * @version 1.0.2
 * @since 2.0.0
 */
// Needs to be public as this interface is implemented by sdk implementing apps (SR)
public interface UIListener {
    /**
     * Handler for location changes occurring even while no tracking is active.
     *
     * @param location The new location from the system's location provider (i.e. GNSS).
     */
    void onLocationUpdate(Location location);

    /**
     * Invoked each time the {@link DataCapturingService} requires some permission from the Android system. That way it
     * is possible to show the user some explanation as to why that permission is required.
     *
     * @param permission The permission the service requires in the form of an Android permission {@link String}.
     * @param reason A reason for why the service requires that permission. You may show the reason to the user before
     *            asking for the permission or create your own message from it.
     * @return {@code true} if the permission has been granted; {@code false} otherwise.
     */
    boolean onRequirePermission(String permission, Reason reason);
}
