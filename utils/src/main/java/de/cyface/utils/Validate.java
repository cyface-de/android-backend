package de.cyface.utils;

import java.util.Collection;

import android.database.Cursor;
import androidx.annotation.Nullable;

/**
 * A class with static methods to check method pre- and post conditions.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.0
 * @since 2.2.0
 */
public class Validate {

    public static void notEmpty(final String string) {
        notEmpty("String should not be empty.", string);
    }

    public static void notEmpty(final String message, final String string) {
        if (string == null || string.isEmpty()) {
            throw new ValidationException(message);
        }
    }

    public static void notEmpty(final Collection collection) {
        if (collection == null || collection.isEmpty()) {
            throw new ValidationException("Collection may not be empty or null.");
        }
    }

    public static void notEmpty(final Object[] array) {
        if (array == null || array.length == 0) {
            throw new ValidationException("Array may not be empty or null.");
        }
    }

    public static void notNull(final String message, final Object object) {
        if (object == null) {
            throw new ValidationException(message);
        }
    }

    public static void notNull(final Object object) {
        notNull("Object was null.", object);
    }

    public static void isTrue(final boolean b) {
        isTrue(b, "Expression was not true.");
    }

    public static void isTrue(final boolean b, final String message) {
        if (!b) {
            throw new ValidationException(message);
        }
    }

    /**
     * Checks if the {@link Cursor} is null. If so, a soft {@link DataCapturingException} is thrown.
     *
     * @param cursor the {@code Cursor} to be checked
     * @throws DataCapturingException If content provider was inaccessible. See {@code ContentResolver#query()}.
     */
    public static void softCatchNullCursor(@Nullable Cursor cursor) throws DataCapturingException {
        if (cursor == null) {
            throw new DataCapturingException("Cursor was null, unable to perform operation.");
        }
    }
}
