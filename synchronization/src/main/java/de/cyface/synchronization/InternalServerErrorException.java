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
 * An {@code Exception} thrown when the server reports an internal server error.
 *
 * @author Armin Schnabel
 * @version 1.0.1
 * @since 4.0.0
 */
public class InternalServerErrorException extends Exception {

    /**
     * @param detailedMessage A more detailed message explaining the context for this {@code Exception}.
     */
    InternalServerErrorException(@NonNull final String detailedMessage) {
        super(detailedMessage);
    }

    /**
     * @param detailedMessage A more detailed message explaining the context for this {@code Exception}.
     * @param cause The {@code Exception} that caused this one.
     */
    @SuppressWarnings("unused") // May be used in the future
    public InternalServerErrorException(@NonNull final String detailedMessage, @NonNull final Exception cause) {
        super(detailedMessage, cause);
    }
}
