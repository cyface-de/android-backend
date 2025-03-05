/*
 * Copyright 2025 Cyface GmbH
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
package de.cyface.synchronization

import android.os.Parcel
import android.os.Parcelable
import de.cyface.persistence.model.Track

/**
 * Implementation of [LocationAnonymization] that disables anonymization.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.14.0
 */
class LocationAnonymizationDisabled() : LocationAnonymization {

    /**
     * Constructs a [LocationAnonymizationDisabled] object from a `Parcel`.
     * This is used for deserialization when passing the object between components.
     *
     * @param parcel The `Parcel` containing the serialized object data.
     */
    @Suppress("UNUSED_PARAMETER") // Required by Parcelable interface
    constructor(parcel: Parcel) : this()

    override fun anonymize(track: Track): Track {
        return track // Return track without anonymization
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) = Unit

    companion object CREATOR : Parcelable.Creator<LocationAnonymizationDisabled> {
        override fun createFromParcel(parcel: Parcel) = LocationAnonymizationDisabled(parcel)
        override fun newArray(size: Int) = arrayOfNulls<LocationAnonymizationDisabled>(size)
    }
}
