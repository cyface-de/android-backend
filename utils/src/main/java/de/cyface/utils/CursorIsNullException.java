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
 * @version 1.0.0
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
