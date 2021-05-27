/*
 * Copyright 2019 Cyface GmbH
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
package de.cyface.persistence;

import androidx.annotation.NonNull;
import de.cyface.persistence.model.Measurement;

/**
 * An <code>Exception</code> which occurs every time someone wants to execute an action on a
 * {@link Measurement}, that does not exist on this device.
 *
 * @author Klemens Muthmann
 * @since 2.0.1
 * @version 1.0.1
 */
public final class NoSuchMeasurementException extends Exception {
    /**
     * Creates a new completely initialized <code>NoSuchMeasurementException</code>, providing a detailed explanation
     * about the error to the caller.
     * 
     * @param message The explanation of why this error occurred.
     */
    public NoSuchMeasurementException(final @NonNull String message) {
        super(message);
    }
}
