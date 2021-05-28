package de.cyface.persistence.serialization;

import de.cyface.persistence.model.Point3d;

/**
 * The protocol for writing {@link Point3d}s such as accelerations, rotations and directions to a file.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.0.0
 */
public interface FileSupport<T> {

    /**
     * Appends data to a file for a certain measurement.
     *
     * @param data The data to append.
     */
    void append(T data);

    /**
     * Creates a data representation from some serializable object.
     *
     * @param data A valid object to create a data in Cyface binary format representation for.
     * @return The data in the Cyface binary format.
     */
    byte[] serialize(T data);
}