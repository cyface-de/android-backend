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

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.GeoLocationV6;
import de.cyface.persistence.model.Measurement;

/**
 * Interface for strategies to filter ("clean") {@link GeoLocation}s captured by
 * {@code DataCapturingBackgroundService#onLocationCaptured()} events.
 * <p>
 * Must be {@code Parcelable} to be passed from the {@code DataCapturingService} via {@code Intent}.
 *
 * @author Armin Schnabel
 * @version 1.1.0
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

    /**
     * Implements the SQL-equivalent to load only the "cleaned" {@link GeoLocation}s or {@link GeoLocationV6} with the same filters as in the
     * {@link #isClean(GeoLocation)} implementation.
     * <p>
     * <b>Attention: The caller needs to wrap this method call with a try-finally block to ensure the returned
     * {@code Cursor} is always closed after use. The cursor cannot be closed within this implementation as it's
     * accessed by the caller.</b>
     *
     * @param resolver {@code ContentResolver} that provides access to the {@link MeasuringPointsContentProvider} or {@link V6ContentProvider}.
     * @param measurementId The identifier for the {@link Measurement} to load the track for.
     * @param geoLocationsUri The content provider {@link Uri} for the {@link GeoLocationsTable} or {@link GeoLocationsTableV6}.
     * @return The {@code Cursor} which points to the "clean" {@link GeoLocation}s or {@link GeoLocationV6} in the database.
     */
    @Nullable
    Cursor loadCleanedLocations(@NonNull final ContentResolver resolver, final long measurementId,
            @NonNull final Uri geoLocationsUri);
}
