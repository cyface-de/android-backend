package de.cyface.persistence.serialization;

import static de.cyface.persistence.serialization.Point3dFile.ACCELERATIONS_FILE_EXTENSION;
import static de.cyface.persistence.serialization.Point3dFile.ACCELERATIONS_FOLDER_NAME;
import static de.cyface.persistence.serialization.Point3dFile.DIRECTIONS_FOLDER_NAME;
import static de.cyface.persistence.serialization.Point3dFile.DIRECTION_FILE_EXTENSION;
import static de.cyface.persistence.serialization.Point3dFile.ROTATIONS_FOLDER_NAME;
import static de.cyface.persistence.serialization.Point3dFile.ROTATION_FILE_EXTENSION;

public enum Point3dType {
    ACCELERATION, ROTATION, DIRECTION;

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
