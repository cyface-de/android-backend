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

import de.cyface.persistence.v1.model.Measurement
import de.cyface.persistence.v1.model.Track

/**
 * Defines the types of [Event]s which may be collected.
 *
 * An example are the use of the life-cycle methods such as start, pause, resume, etc. which are required to
 * slice [Measurement]s into [Track]s before they are resumed.
 *
 * @author Armin Schnabel
 * @version 1.0.1
 * @since 4.0.0
 */
enum class EventType(val databaseIdentifier: String) {
    LIFECYCLE_START("LIFECYCLE_START"),
    LIFECYCLE_PAUSE("LIFECYCLE_PAUSE"),
    LIFECYCLE_RESUME("LIFECYCLE_RESUME"),
    LIFECYCLE_STOP("LIFECYCLE_STOP"),
    MODALITY_TYPE_CHANGE("MODALITY_TYPE_CHANGE");
}