/*
 * Copyright 2017-2023 Cyface GmbH
 *
 * This file is part of the Cyface SDK for Android.
 *
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.datacapturing;

import de.cyface.datacapturing.backend.DataCapturingBackgroundService;
import de.cyface.datacapturing.model.CapturedData;
import de.cyface.datacapturing.ui.Reason;
import de.cyface.persistence.strategies.LocationCleaningStrategy;
import de.cyface.persistence.model.ParcelableGeoLocation;
import de.cyface.persistence.model.Modality;
import de.cyface.utils.DiskConsumption;

/**
 * An interface for a listener, listening for data capturing events. This listener can be registered with a
 * {@link DataCapturingService} via {@link DataCapturingService#start(Modality, StartUpFinishedHandler)}.
 * <p>
 * This interface needs to be public as this interface is implemented by sdk implementing apps (SR).
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.0.0
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
     * This method is called each time the data capturing service receives a new {@link ParcelableGeoLocation} which passes the
     * {@link LocationCleaningStrategy}.
     *
     * @param position The new {@code GeoLocation}.
     */
    void onNewGeoLocationAcquired(ParcelableGeoLocation position);

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
    // Because this is used in the custom {@link EventHandlingStrategy}s of SDK implementing apps
    @SuppressWarnings("unused")
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
    @SuppressWarnings({"UnusedReturnValue"}) // Because this might be useful for SDK implementing apps
    boolean onRequiresPermission(String permission, Reason reason);

    /**
     * This method is called when the capturing stopped. This can occurs when a {@link EventHandlingStrategy}
     * was implemented which stops the {@link DataCapturingBackgroundService} when the space is low.
     */
    void onCapturingStopped();
}
