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

import static de.cyface.persistence.Constants.TAG;

import android.os.Parcel;
import android.util.Log;

import androidx.annotation.NonNull;

import de.cyface.persistence.model.GeoLocation;

/**
 * An implementation of the {@link LocationCleaningStrategy} which uses simple lightweight filters
 * which can be applied "live".
 * <p>
 * The goal is to ignore {@link GeoLocation}s when standing still, to ignore very inaccurate locations and to avoid
 * large distance "jumps", e.g. when the {@code LocationManager} implementation delivers an old, cached location at the
 * beginning of the track.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 4.1.0
 */
public class DefaultLocationCleaningStrategy implements LocationCleaningStrategy {

    /**
     * The lowest accuracy of {@link GeoLocation}s in meters which is too "bad" to be used.
     */
    private static final float UPPER_ACCURACY_THRESHOLD = 20.0f;
    /**
     * The lowest speed of {@link GeoLocation}s in m/s which is just enough to be seen as "moving".
     */
    private static final double LOWER_SPEED_THRESHOLD = 1.0;
    /**
     * The highest speed of {@link GeoLocation}s in m/s which is just enough to be seen as "valid".
     */
    private static final double UPPER_SPEED_THRESHOLD = 100.0;

    /**
     * The <code>Parcelable</code> creator as required by the Android Parcelable specification.
     */
    public static final Creator<DefaultLocationCleaningStrategy> CREATOR = new Creator<DefaultLocationCleaningStrategy>() {
        @Override
        public DefaultLocationCleaningStrategy createFromParcel(final Parcel in) {
            return new DefaultLocationCleaningStrategy(in);
        }

        @Override
        public DefaultLocationCleaningStrategy[] newArray(final int size) {
            return new DefaultLocationCleaningStrategy[size];
        }
    };

    /**
     * No arguments constructor is redeclared here, since it is overwritten by the constructor required by
     * <code>Parcelable</code>.
     */
    public DefaultLocationCleaningStrategy() {
        // Nothing to do here
    }

    /**
     * Constructor as required by <code>Parcelable</code> implementation.
     *
     * @param in A <code>Parcel</code> that is a serialized version of a <code>IgnoreEventsStrategy</code>.
     */
    private DefaultLocationCleaningStrategy(@SuppressWarnings("unused") final @NonNull Parcel in) {
        // Nothing to do here.
    }

    @Override
    public boolean isClean(@NonNull final GeoLocation location) {

        // Ignore very inaccurate locations (e.g. indoors)
        if (location.getAccuracy() >= UPPER_ACCURACY_THRESHOLD) {
            Log.d(TAG, "GeoLocation with bad accuracy filtered: " + location.getAccuracy());
            return false;
        }

        // Ignore locations while standing still
        if (location.getSpeed() < LOWER_SPEED_THRESHOLD) {
            Log.d(TAG, "GeoLocation with low speed filtered: " + location.getSpeed());
            return false;
        }

        // Ignore locations which are too far away from their previous location (upper speed limit)
        if (location.getSpeed() > UPPER_SPEED_THRESHOLD) {
            Log.d(TAG, "GeoLocation with high speed filtered: " + location.getSpeed());
            return false;
        }

        return true;
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
