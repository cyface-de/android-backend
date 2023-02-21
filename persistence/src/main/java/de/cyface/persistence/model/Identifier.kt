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

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An `@Entity` which represents a persisted device identifier.
 *
 * An instance of this class represents one row in a database table containing the identifier data.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.5.0
 * @property uid The system wide unique identifier of this identifier. The database identifier which
 * can be used to identify the latest entry in this table. If the entry was not saved yet it may be
 * `null`, since the persistence layers assigns a unique identifier on saving an entry.
 * @property deviceId A String value which contains an identifier for this device.
 */
@Entity(tableName = "Identifier")
class Identifier(
    @field:PrimaryKey(autoGenerate = true) var uid : Long = 0,
    val deviceId: String
) {
    /**
     * Creates a new instance of this class which was not yet persisted and has [uid] set to null. // FIXME: can this also be null?
     *
     * @param deviceId A String value which contains an identifier for this device.
     */
    constructor(deviceId: String) : this(0, deviceId)
}