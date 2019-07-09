/*
 * Copyright 2018 Cyface GmbH
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
package de.cyface.utils;

import android.content.ContentProvider;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;

/**
 * A wrapper {@link Exception} for code thrown for a null {@link Cursor} after executing
 * {@link ContentProvider#query(Uri, String[], Bundle, CancellationSignal)}.
 *
 * @author Armin Schnabel
 * @version 1.0.1
 * @since 3.0.0
 */
public final class CursorIsNullException extends Exception {

    /**
     * Creates a new completely initialized {@link CursorIsNullException}, wrapping another {@link Exception}
     * from deeper within the system.
     *
     * @param e The wrapped {@code Exception} containing further details about the error.
     */
    public CursorIsNullException(final @NonNull Exception e) {
        super(e);
    }

    /**
     * Creates a new completely initialized {@link CursorIsNullException}.
     *
     * @param message The message to show as a detailed explanation for this {@link Exception}.
     */
    public CursorIsNullException(final @NonNull String message) {
        super(message);
    }

    /**
     * Creates a new completely initialized {@link CursorIsNullException} with a default message.
     */
    CursorIsNullException() {
        super("Cursor is null. Unable to perform operation.");
    }
}
