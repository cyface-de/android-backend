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

import java.util.Objects

/**
 * A [Track] consists of [ParcelableGeoLocation]s and [ParcelablePressure]s (data points) collected
 * for a [Measurement]. Its data points are ordered by time.
 *
 * A [Track] begins with the first data point of each type collected after start or resume was triggered
 * and stops with the last collected data point of each type before the next resume command is triggered or when the
 * very last location is reached.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.5.0
 */
class Track {
    /**
     * The [ParcelableGeoLocation]s collected for this [Track].
     */
    private val geoLocations: MutableList<ParcelableGeoLocation?>

    /**
     * The [ParcelablePressure]s collected for this [Track].
     */
    private val pressures: MutableList<ParcelablePressure?>

    /**
     * Creates a completely initialized instance of this class.
     */
    constructor() {
        geoLocations = ArrayList()
        pressures = ArrayList()
    }

    /**
     * Creates a completely initialized instance of this class.
     *
     * @param locations The locations to add to the track.
     * @param pressures The pressures to add to the track.
     */
    constructor(
        locations: MutableList<ParcelableGeoLocation?>,
        pressures: MutableList<ParcelablePressure?>
    ) {
        this.geoLocations = ArrayList(locations)
        this.pressures = ArrayList(pressures)
    }

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

    /**
     * @return The [ParcelableGeoLocation]s collected for this [Track].
     */
    fun getGeoLocations(): List<ParcelableGeoLocation?> {
        return ArrayList(geoLocations)
    }

    /**
     * @return The [ParcelablePressure]s collected for this [Track].
     */
    fun getPressures(): List<ParcelablePressure?> {
        return pressures
    }

    override fun toString(): String {
        return "Track{" +
                "geoLocations=" + geoLocations +
                ", pressures=" + pressures +
                '}'
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val track = other as Track
        return geoLocations == track.geoLocations && pressures == track.pressures
    }

    override fun hashCode(): Int {
        return Objects.hash(geoLocations, pressures)
    }
}