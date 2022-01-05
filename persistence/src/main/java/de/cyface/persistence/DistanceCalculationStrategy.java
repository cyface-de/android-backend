/*
 * Copyright 2019 Cyface GmbH
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
package de.cyface.persistence;

import android.os.Parcelable;

import androidx.annotation.NonNull;
import de.cyface.persistence.model.ParcelableGeoLocation;
import de.cyface.persistence.model.Measurement;

/**
 * Interface for strategies to respond to {@code DataCapturingBackgroundService#onLocationCaptured()} events
 * to calculate the {@link Measurement#getDistance()} from {@link ParcelableGeoLocation} pairs.
 * <p>
 * Must be {@code Parcelable} to be passed from the {@code DataCapturingService} via {@code Intent}.
 *
 * @author Armin Schnabel
 * @version 2.0.2
 * @since 3.2.0
 */
public interface DistanceCalculationStrategy extends Parcelable {

    /**
     * Implements a strategy to calculate the {@link Measurement#getDistance()} based on two subsequent {@link ParcelableGeoLocation}s.
     *
     * @param lastLocation The {@code GeoLocation} captured before {@param newLocation}
     * @param newLocation The {@code GeoLocation} captured after {@param lastLocation}
     * @return The distance which is added to the {@code Measurement} based on the provided {@code GeoLocation}s.
     */
    double calculateDistance(@NonNull final ParcelableGeoLocation lastLocation, @NonNull final ParcelableGeoLocation newLocation);
}
