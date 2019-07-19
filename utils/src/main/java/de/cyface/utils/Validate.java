/*
 * Copyright 2017 Cyface GmbH
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

import java.util.Collection;

import android.content.ContentProvider;
import android.database.Cursor;
import androidx.annotation.Nullable;

/**
 * A class with static methods to check method pre- and post conditions.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.2.1
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
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible. See {@code ContentResolver#query()}.
     */
    public static void softCatchNullCursor(@Nullable Cursor cursor) throws CursorIsNullException{
        if (cursor == null) {
            throw new CursorIsNullException();
        }
    }
}
