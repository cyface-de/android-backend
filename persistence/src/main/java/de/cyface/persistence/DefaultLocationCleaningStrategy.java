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

import static de.cyface.persistence.GeoLocationsTable.COLUMN_ACCURACY;
import static de.cyface.persistence.GeoLocationsTable.COLUMN_SPEED;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
 * @version 1.1.0
 * @since 4.1.0
 */
public class DefaultLocationCleaningStrategy implements LocationCleaningStrategy {

    /**
     * The lowest accuracy of {@link GeoLocation}s in centimeters which is too "bad" to be used.
     */
    public static final float UPPER_ACCURACY_THRESHOLD = 2000.0f;
    /**
     * The lower speed boundary in m/s which needs to be exceeded for the location to be "valid".
     */
    public static final double LOWER_SPEED_THRESHOLD = 1.0;
    /**
     * The upper speed boundary in m/s which needs to be undershot for the location to be "valid".
     */
    public static final double UPPER_SPEED_THRESHOLD = 100.0;

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
        return location.getSpeed() > LOWER_SPEED_THRESHOLD && location.getAccuracy() < UPPER_ACCURACY_THRESHOLD
                && location.getSpeed() < UPPER_SPEED_THRESHOLD;
    }

    @Nullable
    public Cursor loadCleanedLocations(@NonNull ContentResolver resolver, final long measurementId,
            @NonNull final Uri geoLocationsUri) {

        @Nullable
        final Cursor geoLocationCursor = resolver.query(geoLocationsUri, null,
                GeoLocationsTable.COLUMN_MEASUREMENT_FK + " = ? AND " + COLUMN_SPEED + " > ? AND " + COLUMN_ACCURACY
                        + " < ? AND " + COLUMN_SPEED + " < ?",
                new String[] {Long.valueOf(measurementId).toString(), String.valueOf(LOWER_SPEED_THRESHOLD),
                        String.valueOf(UPPER_ACCURACY_THRESHOLD), String.valueOf(UPPER_SPEED_THRESHOLD)},
                GeoLocationsTable.COLUMN_GEOLOCATION_TIME + " ASC");

        return geoLocationCursor;
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
