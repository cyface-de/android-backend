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
package de.cyface.synchronization;

import androidx.annotation.NonNull;

/**
 * An {@code Exception} thrown by the server when the server thinks that this client sent too many requests in to short
 * time. This helps to prevent DDoS attacks. The client should just retry a short time later.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 4.2.2
 **/
public class TooManyRequestsException extends Exception {

    /**
     * @param detailedMessage A more detailed message explaining the context for this {@code Exception}.
     */
    TooManyRequestsException(final String detailedMessage) {
        super(detailedMessage);
    }

    /**
     * @param detailedMessage A more detailed message explaining the context for this {@code Exception}.
     * @param cause The {@code Exception} that caused this one.
     */
    @SuppressWarnings("unused") // May be used in the future
    public TooManyRequestsException(@NonNull final String detailedMessage, @NonNull final Exception cause) {
        super(detailedMessage, cause);
    }
}
