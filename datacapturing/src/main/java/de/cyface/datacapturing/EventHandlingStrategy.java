/*
 * Copyright 2017 Cyface GmbH
 * This file is part of the Cyface SDK for Android.
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.datacapturing;

import android.app.Notification;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import de.cyface.datacapturing.backend.DataCapturingBackgroundService;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;

/**
 * Interface for strategies to respond to events triggered by the {@link DataCapturingBackgroundService}.
 * E.g.: Show a notification when little space is available and stop the capturing.
 * Must be {@code Parcelable} to be passed from the {@link DataCapturingService} via {@code Intent}.
 * <p>
 * An implementation of this class must also provide a {@code Notification} shown by the data capturing service,
 * during capturing.
 * <p>
 * Another event which is handled is the {@link Measurement#distance} calculation when the
 * {@link DataCapturingBackgroundService#onLocationCaptured(GeoLocation)} event is triggered.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 2.0.0
 * @since 2.5.0
 */
public interface EventHandlingStrategy extends Parcelable {

    /**
     * Implement a strategy to react to a low space warning.
     *
     * @param dataCapturingBackgroundService A reference to the background service to allow operations
     *            on it like stopping the capturing.
     */
    void handleSpaceWarning(@NonNull final DataCapturingBackgroundService dataCapturingBackgroundService);

    /**
     * Provides an Android representation of a {@code Notification}, that can be displayed on screen.
     *
     * @param context The {@link DataCapturingBackgroundService} as context for the new {@code Notification}.
     * @return An Android {@code Notification} object configured to work as capturing notification.
     */
    @NonNull
    Notification buildCapturingNotification(@NonNull final DataCapturingBackgroundService context);

    /**
     * Implements a strategy to calculate the {@link Measurement#distance} based on two subsequent {@link GeoLocation}s.
     *
     * @param lastLocation The {@code GeoLocation} captured before {@param newLocation}
     * @param newLocation The {@code GeoLocation} captured after {@param lastLocation}
     * @return The distance which is added to the {@code Measurement} based on the provided {@code GeoLocation}s.
     */
    double updateDistance(@NonNull final GeoLocation lastLocation, @NonNull final GeoLocation newLocation);
}
