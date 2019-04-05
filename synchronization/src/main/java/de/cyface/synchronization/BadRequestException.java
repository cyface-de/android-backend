/*
 * Copyright 2017 Cyface GmbH
 * This file is part of the Cyface SDK for Android.
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.synchronization;

import androidx.annotation.NonNull;

/**
 * An {@code Exception} thrown when the server returns an 400 error. The {@code Exception} contains
 * all the details available and relevant to diagnose the error case.
 *
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 1.0.0
 */
final class BadRequestException extends Exception {

    /**
     * @param detailedMessage A more detailed message explaining the context for this {@code Exception}.
     */
    BadRequestException(@NonNull final String detailedMessage) {
        this(detailedMessage, null);

    }

    /**
     * @param detailedMessage A more detailed message explaining the context for this {@code Exception}.
     * @param cause The {@code Exception} that caused this one.
     */
    @SuppressWarnings("WeakerAccess") // May be used in future
    BadRequestException(@NonNull final String detailedMessage, @NonNull final Exception cause) {
        super(detailedMessage, cause);

    }
}
