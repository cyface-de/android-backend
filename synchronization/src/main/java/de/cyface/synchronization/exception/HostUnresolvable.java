/*
 * Copyright 2020 Cyface GmbH
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

/**
 * An {@code Exception} thrown when the host of the Cyface server cannot be resolved.
 * <p>
 * This is e.g. the case when the phone is connected to a Wi-Fi which is not connected to the internet.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.1.0-beta1
 */
public class HostUnresolvable extends Exception {

    /**
     * @param detailedMessage A more detailed message explaining the context for this <code>Exception</code>.
     */
    public HostUnresolvable(final String detailedMessage) {
        super(detailedMessage);
    }

    /**
     * @param detailedMessage A more detailed message explaining the context for this <code>Exception</code>.
     * @param cause The <code>Exception</code> that caused this one.
     */
    public HostUnresolvable(final String detailedMessage, final Exception cause) {
        super(detailedMessage, cause);
    }

    /**
     * @param cause The <code>Exception</code> that caused this one.
     */
    public HostUnresolvable(final Exception cause) {
        super(cause);
    }
}
