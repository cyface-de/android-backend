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

import android.net.Uri;

import androidx.annotation.NonNull;

/**
 * This utility class contains shared static methods used by multiple modules.
 *
 * @author Armin Schnabel
 * @version 1.1.0
 * @since 3.0.0
 */
public class Utils {

    /**
     * (!) It's important to provide the authority string as parameter because depending on from where you call this
     * you want to access your own authorities database.
     *
     * @param authority The authority to access the database
     */
    public static Uri getMeasurementUri(@NonNull final String authority) {
        return new Uri.Builder().scheme("content").authority(authority).appendPath(MeasurementTable.URI_PATH).build();
    }

    /**
     * (!) It's important to provide the authority string as parameter because depending on from where you call this
     * you want to access your own authorities database.
     *
     * @param authority The authority to access the database
     */
    public static Uri getGeoLocationsUri(@NonNull final String authority) {
        return new Uri.Builder().scheme("content").authority(authority).appendPath(GeoLocationsTable.URI_PATH).build();
    }

    /**
     * (!) It's important to provide the authority string as parameter because depending on from where you call this
     * you want to access your own authorities database.
     *
     * @param authority The authority to access the database
     */
    public static Uri getIdentifierUri(@NonNull final String authority) {
        return new Uri.Builder().scheme("content").authority(authority).appendPath(IdentifierTable.URI_PATH).build();
    }

    /**
     * (!) It's important to provide the authority string as parameter because depending on from where you call this
     * you want to access your own authorities database.
     *
     * @param authority The authority to access the database
     */
    public static Uri getEventUri(@NonNull final String authority) {
        return new Uri.Builder().scheme("content").authority(authority).appendPath(EventTable.URI_PATH).build();
    }
}
