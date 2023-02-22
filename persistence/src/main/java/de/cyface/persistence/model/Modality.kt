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
package de.cyface.persistence.model

/**
 * The [Modality] types to choose from when starting a [Measurement].
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 2.0.0
 * @since 1.0.0
 */
enum class Modality(val databaseIdentifier: String) {
    BICYCLE("BICYCLE"),
    CAR("CAR"),
    MOTORBIKE("MOTORBIKE"),
    BUS("BUS"),
    TRAIN("TRAIN"),
    WALKING("WALKING"),
    UNKNOWN("UNKNOWN")
}