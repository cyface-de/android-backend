/*
 * Copyright 2017-2023 Cyface GmbH
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
package de.cyface.persistence.content

import android.net.Uri
import de.cyface.persistence.model.GeoLocation

/**
 * This class represents the table containing all the [de.cyface.persistence.model.Measurement]s currently
 * stored on this device.
 *
 * @author Armin Schnabel
 * @version 5.0.0
 * @since 1.0.0
 */
class MeasurementTable : AbstractCyfaceTable(URI_PATH) {
    companion object {
        /**
         * The path segment in the table URI identifying the [MeasurementTable].
         */
        const val URI_PATH = "Measurement"

        /**
         * Column name for the status of the measurement.
         *
         * Usually only one measurement should be in the [de.cyface.persistence.model.MeasurementStatus.OPEN]
         * or [de.cyface.persistence.model.MeasurementStatus.PAUSED], else there has been some error.
         */
        const val COLUMN_STATUS = "status"

        /**
         * Column name for the modality chosen at the start of a measurement.
         */
        const val COLUMN_MODALITY = "modality"

        /**
         * Column name for the [de.cyface.persistence.PersistenceLayer.PERSISTENCE_FILE_FORMAT_VERSION]
         * for the data persisted with the [de.cyface.persistence.dao.FileDao] of for this measurement.
         */
        const val COLUMN_PERSISTENCE_FILE_FORMAT_VERSION = "fileFormatVersion"

        /**
         * Column name for the distance of this measurement based on its [GeoLocation]s in meters.
         */
        const val COLUMN_DISTANCE = "distance"

        /**
         * Returns the URI which identifies the table represented by this class.
         *
         * It's important to provide the authority string as parameter because depending on from where
         * you call this you want to access your own authorities database.
         *
         * @param authority The authority to access the database
         */
        fun getUri(authority: String): Uri {
            return Uri.Builder().scheme("content").authority(authority).appendPath(URI_PATH).build()
        }
    }

    override val databaseTableColumns: Array<String>
        get() = arrayOf(
            BaseColumns.ID, COLUMN_STATUS, COLUMN_MODALITY,
            COLUMN_PERSISTENCE_FILE_FORMAT_VERSION, COLUMN_DISTANCE, BaseColumns.TIMESTAMP
        )
}