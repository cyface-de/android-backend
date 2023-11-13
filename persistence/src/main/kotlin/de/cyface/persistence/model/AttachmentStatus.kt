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
 * Status which defines whether a [Attachment] is just [.SAVED] or already [.SYNCED] or [.SKIPPED].
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.10.0
 * @property databaseIdentifier The [String] which represents the enumeration value in the database.
 */
enum class AttachmentStatus(val databaseIdentifier: String) {
    /**
     * This state defines that a [Attachment] has been stored and was not yet [.SYNCED].
     */
    SAVED("SAVED"),

    /**
     * This state defines that a [Attachment] has been synchronized.
     */
    SYNCED("SYNCED"),

    /**
     * This state defines that a [Attachment] was rejected by the API and won't be uploaded.
     */
    SKIPPED("SKIPPED"),

    /**
     * This state defines that a [Attachment] is no longer supported (to be synced).
     */
    DEPRECATED("DEPRECATED");
}