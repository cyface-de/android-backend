package de.cyface.persistence.model;

import java.io.File;

import androidx.annotation.NonNull;
import de.cyface.persistence.serialization.Point3dFile;

/**
 * This class contains meta information required to deserialize {@link Point3dFile}s such as the file format and the
 * number of points stored in the {@link File}.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.0.0
 */
public class PointMetaData {
    private int accelerationPointCounter;
    private int rotationPointCounter;
    private int directionPointCounter;
    private final short persistenceFileFormatVersion;

    public PointMetaData(final int accelerationPointCounter, final int rotationPointCounter,
            final int directionPointCounter, final short persistenceFileFormatVersion) {
        this.accelerationPointCounter = accelerationPointCounter;
        this.rotationPointCounter = rotationPointCounter;
        this.directionPointCounter = directionPointCounter;
        this.persistenceFileFormatVersion = persistenceFileFormatVersion;
    }

    public int getAccelerationPointCounter() {
        return accelerationPointCounter;
    }

    public int getRotationPointCounter() {
        return rotationPointCounter;
    }

    public int getDirectionPointCounter() {
        return directionPointCounter;
    }

    public short getPersistenceFileFormatVersion() {
        return persistenceFileFormatVersion;
    }

    public void incrementPointCounters(final int accelerations, final int rotations, final int directions) {
        accelerationPointCounter += accelerations;
        rotationPointCounter += rotations;
        directionPointCounter += directions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        PointMetaData that = (PointMetaData)o;
        return accelerationPointCounter == that.accelerationPointCounter
                && rotationPointCounter == that.rotationPointCounter
                && directionPointCounter == that.directionPointCounter
                && persistenceFileFormatVersion == that.persistenceFileFormatVersion;
    }

    @Override
    @NonNull
    public String toString() {
        return "PointMetaData{" + "accelerationPointCounter=" + accelerationPointCounter + ", rotationPointCounter="
                + rotationPointCounter + ", directionPointCounter=" + directionPointCounter
                + ", persistenceFileFormatVersion=" + persistenceFileFormatVersion + '}';
    }
}