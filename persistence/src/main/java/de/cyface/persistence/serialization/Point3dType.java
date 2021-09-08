/*
 * Copyright 2021 Cyface GmbH
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
package de.cyface.persistence.serialization;

import static de.cyface.persistence.serialization.Point3dFile.ACCELERATIONS_FILE_EXTENSION;
import static de.cyface.persistence.serialization.Point3dFile.ACCELERATIONS_FOLDER_NAME;
import static de.cyface.persistence.serialization.Point3dFile.DIRECTIONS_FOLDER_NAME;
import static de.cyface.persistence.serialization.Point3dFile.DIRECTION_FILE_EXTENSION;
import static de.cyface.persistence.serialization.Point3dFile.ROTATIONS_FOLDER_NAME;
import static de.cyface.persistence.serialization.Point3dFile.ROTATION_FILE_EXTENSION;

/**
 * Supported sensor data types.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.0.0
 */
public enum Point3dType {
    ACCELERATION, ROTATION, DIRECTION;

    /**
     * @return the file extension used for files containing data of this sensor type.
     */
    public String fileExtension() {
        switch (this) {
            case ACCELERATION:
                return ACCELERATIONS_FILE_EXTENSION;
            case ROTATION:
                return ROTATION_FILE_EXTENSION;
            case DIRECTION:
                return DIRECTION_FILE_EXTENSION;
            default:
                throw new IllegalArgumentException("Unknown type: " + this);
        }
    }

    /**
     * @return the folder name used for files containing data of this sensor type.
     */
    public String folderName() {
        switch (this) {
            case ACCELERATION:
                return ACCELERATIONS_FOLDER_NAME;
            case ROTATION:
                return ROTATIONS_FOLDER_NAME;
            case DIRECTION:
                return DIRECTIONS_FOLDER_NAME;
            default:
                throw new IllegalArgumentException("Unknown type: " + this);
        }
    }
}