package de.cyface.datacapturing.ui;

import android.location.Location;

import de.cyface.datacapturing.DataCapturingService;
import de.cyface.datacapturing.Reason;

/**
 * Created by muthmann on 16.02.18.
 */

public interface UIListener {
    void onLocationUpdate(Location location);

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
}
