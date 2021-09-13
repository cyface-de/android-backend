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

/**
 * An {@code Exception} thrown when the transfer file is too large to be uploaded in one peace.
 * <p>
 * Our uploader currently does not support uploads with multiple requests (resumable).
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.0.0
 */
public class UploadSessionExpired extends Exception {

    /**
     * @param detailedMessage A more detailed message explaining the context for this <code>Exception</code>.
     */
    public UploadSessionExpired(final String detailedMessage) {
        super(detailedMessage);
    }

    /**
     * @param detailedMessage A more detailed message explaining the context for this <code>Exception</code>.
     * @param cause The <code>Exception</code> that caused this one.
     */
    @SuppressWarnings("unused") // May be used in the future
    public UploadSessionExpired(final String detailedMessage, final Exception cause) {
        super(detailedMessage, cause);
    }

    /**
     * @param cause The <code>Exception</code> that caused this one.
     */
    @SuppressWarnings("unused") // May be used in the future
    public UploadSessionExpired(final Exception cause) {
        super(cause);
    }
}