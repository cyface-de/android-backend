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

import de.cyface.persistence.model.Point3d;

/**
 * The protocol for writing {@link Point3d}s such as accelerations, rotations and directions to a file.
 *
 * @author Armin Schnabel
 * @version 1.0.1
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