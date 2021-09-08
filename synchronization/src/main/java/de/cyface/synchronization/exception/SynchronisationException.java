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
package de.cyface.synchronization.exception;

import androidx.annotation.NonNull;

/**
 * An <code>Exception</code> thrown each time data synchronisation with a Cyface/Movebis server fails. It provides
 * further information either via a message or another wrapped <code>Exception</code>.
 *
 * @author Klemens Muthmann
 * @version 1.1.3
 * @since 2.0.0
 */
public final class SynchronisationException extends Exception {

    /**
     * Creates a new completely initialized <code>SynchronisationException</code>, wrapping another
     * <code>Exception</code> from
     * deeper within the system.
     *
     * @param cause The wrapped <code>Exception</code>.
     */
    public SynchronisationException(final @NonNull Exception cause) {
        super(cause);
    }

    /**
     * Creates a new completely initialized <code>SynchronisationException</code>, providing a detailed explanation
     * about the
     * error.
     *
     * @param message The message explaining the error condition.
     */
    public SynchronisationException(final @NonNull String message) {
        super(message);
    }

    /**
     * Creates a new completely initialized <code>SynchronisationException</code>, providing a detailed explanation
     * about the
     * error.
     *
     * @param message The message explaining the error condition.
     * @param cause The wrapped <code>Exception</code>.
     */
    public SynchronisationException(final @NonNull String message, final @NonNull Exception cause) {
        super(message, cause);
    }
}
