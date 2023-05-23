/*
 * Copyright 2019-2023 Cyface GmbH
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

import android.location.Location;
import android.location.LocationManager;
import android.os.Parcel;

import androidx.annotation.NonNull;

import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;

/**
 * The default implementation of the {@link DistanceCalculationStrategy} which calculates the
 * {@link Measurement#getDistance()} using simply {@link Location#distanceTo(Location)}.
 *
 * @author Armin Schnabel
 * @version 2.0.3
 * @since 3.2.0
 */
public class DefaultDistanceCalculationStrategy implements DistanceCalculationStrategy {

    /**
     * The <code>Parcelable</code> creator as required by the Android Parcelable specification.
     */
    public static final Creator<DefaultDistanceCalculationStrategy> CREATOR = new Creator<DefaultDistanceCalculationStrategy>() {
        @Override
        public DefaultDistanceCalculationStrategy createFromParcel(final Parcel in) {
            return new DefaultDistanceCalculationStrategy(in);
        }

        @Override
        public DefaultDistanceCalculationStrategy[] newArray(final int size) {
            return new DefaultDistanceCalculationStrategy[size];
        }
    };

    /**
     * No arguments constructor is redeclared here, since it is overwritten by the constructor required by
     * <code>Parcelable</code>.
     */
    public DefaultDistanceCalculationStrategy() {
        // Nothing to do here
    }

    /**
     * Constructor as required by <code>Parcelable</code> implementation.
     *
     * @param in A <code>Parcel</code> that is a serialized version of a <code>IgnoreEventsStrategy</code>.
     */
    private DefaultDistanceCalculationStrategy(@SuppressWarnings("unused") final @NonNull Parcel in) {
        // Nothing to do here.
    }

    @Override
    public double calculateDistance(@NonNull GeoLocation lastLocation, @NonNull GeoLocation newLocation) {
        // Changed to that the code is more similar to SR-calculated distance [STAD-518]
        final Location previousLocation = new Location(LocationManager.GPS_PROVIDER);
        final Location nextLocation = new Location(LocationManager.GPS_PROVIDER);
        previousLocation.setLatitude(lastLocation.getLat());
        previousLocation.setLongitude(lastLocation.getLon());
        previousLocation.setSpeed((float) lastLocation.getSpeed());
        previousLocation.setTime(lastLocation.getTimestamp());
        nextLocation.setLatitude(newLocation.getLat());
        nextLocation.setLongitude(newLocation.getLon());
        nextLocation.setSpeed((float) newLocation.getSpeed());
        nextLocation.setTime(newLocation.getTimestamp());
        // w/o `accuracy`, `distanceTo()` returns `0` on Samsung Galaxy S9 Android 10 [STAD-513]
        previousLocation.setAccuracy(lastLocation.getAccuracy());
        nextLocation.setAccuracy(newLocation.getAccuracy());

        return previousLocation.distanceTo(nextLocation);
    }

    @Override
    public int describeContents() {
        // Nothing to do
        return 0;
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        // Nothing to do
    }
}
