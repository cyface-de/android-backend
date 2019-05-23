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

import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;

/**
 * Interface for strategies to filter ("clean") {@link GeoLocation}s captured by
 * {@code DataCapturingBackgroundService#onLocationCaptured()} events.
 * <p>
 * Must be {@code Parcelable} to be passed from the {@code DataCapturingService} via {@code Intent}.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 4.1.0
 */
public interface LocationCleaningStrategy extends Parcelable {

    /**
     * Implements a strategy to filter {@link GeoLocation}s.
     *
     * @param location The {@code GeoLocation} to be checked
     * @return {@code True} if the {@code GeoLocation} is considered "clean" by this strategy.
     */
    boolean isClean(@NonNull final GeoLocation location);
}
