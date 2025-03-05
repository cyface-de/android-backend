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
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.model.Track
import de.cyface.persistence.strategy.DistanceCalculationStrategy

/**
 * Implementation of [LocationAnonymization] that removes the locations within a dynamic radius
 * from the start- and end of the [Track].
 *
 * FIXME: Clarify if each sub-track should be trimmed, or only start/end of the total track.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.14.0
 * @property distanceCalculationStrategy Determines how to calculate the distances.
 * @property radius The function which determines the anonymization radius in meters.
 */
class ExampleLocationAnonymization(
    private val distanceCalculationStrategy: DistanceCalculationStrategy,
    private val radius: () -> Double,
) : LocationAnonymization, Parcelable {

    /**
     * Constructs a [ExampleLocationAnonymization] object from a `Parcel`.
     * This is used for deserialization when passing the object between components.
     *
     * @param parcel The `Parcel` containing the serialized object data.
     */
    constructor(parcel: Parcel) : this(
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            parcel.readParcelable(
                DistanceCalculationStrategy::class.java.classLoader,
                DistanceCalculationStrategy::class.java,
            )
        } else {
            @Suppress("DEPRECATION") // resolved in if-clause above for newer phones
            parcel.readParcelable(DistanceCalculationStrategy::class.java.classLoader)
        } ?: throw IllegalArgumentException("Could not deserialize DistanceCalculationStrategy"),
        { parcel.readDouble() } // Deserialize radius as a lambda returning a stored value
    )

    override fun anonymize(track: Track): Track {
        val locations = track.geoLocations
        if (locations.size < 2) return track

        // Remove start points
        val startPoint = locations.first()
        var trimmed = locations.dropWhile {
            distanceCalculationStrategy.calculateDistance(startPoint, it) <= radius()
        }

        // Remove end points
        if (trimmed.isNotEmpty()) {
            val newEndPoint = trimmed.last()
            trimmed = trimmed.dropLastWhile {
                distanceCalculationStrategy.calculateDistance(newEndPoint, it) <= radius()
            }
        }

        return Track(trimmed.toMutableList(), track.pressures)
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(distanceCalculationStrategy, flags)
        dest.writeDouble(radius())
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ExampleLocationAnonymization> {
        override fun createFromParcel(parcel: Parcel) = ExampleLocationAnonymization(parcel)
        override fun newArray(size: Int) = arrayOfNulls<ExampleLocationAnonymization>(size)
    }
}
