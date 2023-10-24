/*
 * Copyright 2021-2023 Cyface GmbH
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
 * Status which defines whether a [Measurement] is still capturing data, paused, finished or what
 * the uploading state is.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @property databaseIdentifier The [String] which represents the enumeration value in the database.
 */
enum class MeasurementStatus(val databaseIdentifier: String) {
    /**
     * This state defines that a [Measurement] is currently active.
     */
    OPEN("OPEN"),

    /**
     * This state defines that an active [Measurement] was paused and not yet [.FINISHED] or resumed.
     */
    PAUSED("PAUSED"),

    /**
     * This state defines that a [Measurement] has been completed and was not yet [.SYNCED].
     */
    FINISHED("FINISHED"),

    /**
     * This state defines that a [Measurement] is partially uploaded.
     *
     * Currently, this state is reached after the measurement binary was successfully uploaded,
     * but the measurement's attachments (log, image or video files) are not fully synced yet.
     *
     * Check the state in the [File] table entries to see which attachments are not synced yet.
     * After all attachments are uploaded the measurement is set to [.SYNCED].
     */
    UPLOADING("UPLOADING"),

    /**
     * This state defines that a [Measurement] has been synchronized.
     */
    SYNCED("SYNCED"),

    /**
     * This state defines that a [Measurement] was rejected by the API and won't be uploaded.
     */
    SKIPPED("SKIPPED"),

    /**
     * This state defines that a [Measurement] is no longer supported (to be synced/resumed).
     */
    DEPRECATED("DEPRECATED");
}