/*
 * Copyright 2023 Cyface GmbH
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
package de.cyface.persistence.model

/**
 * A [Track] consists of [ParcelableGeoLocation]s and [ParcelablePressure]s (data points) collected
 * for a [Measurement]. Its data points are ordered by time.
 *
 * A [Track] begins with the first data point of each type collected after start or resume was triggered
 * and stops with the last collected data point of each type before the next resume command is triggered or when the
 * very last location is reached.
 *
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 4.0.0
 * @property geoLocations The [ParcelableGeoLocation]s collected for this [Track].
 * @property pressures The [ParcelablePressure]s collected for this [Track].
 */
data class Track(
    val geoLocations: MutableList<ParcelableGeoLocation?>,
    val pressures: MutableList<ParcelablePressure?>
) {
    /**
     * Creates a completely initialized instance of this class.
     */
    constructor() : this(ArrayList(), ArrayList())

    /**
     * @param location The [ParcelableGeoLocation] to be added at the end of the [Track].
     */
    fun addLocation(location: ParcelableGeoLocation) {
        geoLocations.add(location)
    }

    /**
     * @param pressure The [ParcelablePressure] to be added at the end of the [Track].
     */
    fun addPressure(pressure: ParcelablePressure) {
        pressures.add(pressure)
    }
}