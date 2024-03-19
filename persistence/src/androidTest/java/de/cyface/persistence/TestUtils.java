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
package de.cyface.persistence;

import static junit.framework.TestCase.fail;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.List;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;

import de.cyface.utils.Validate;

/**
 * A utility class with static methods for supporting tests on the persistence code.
 *
 * @author Klemens Muthmann
 * @version 2.0.3
 * @since 1.0.0
 */
class TestUtils {

    static final String AUTHORITY = "de.cyface.persistence.test.provider";

    private static void compareCursorWithValues(final Cursor cursor, final List<ContentValues> contentValues) {
        assertThat(contentValues.size() <= cursor.getCount(), is(equalTo(true)));
        for (ContentValues values : contentValues) {
            cursor.moveToNext();
            for (String key : values.keySet()) {
                int cursorColumnIndex = cursor.getColumnIndex(key);
                assertNotEquals(cursorColumnIndex, -1);
                int dataType = cursor.getType(cursorColumnIndex);
                switch (dataType) {
                    case Cursor.FIELD_TYPE_FLOAT:
                        assertEquals(values.getAsDouble(key), cursor.getDouble(cursorColumnIndex), 0.01);
                        break;
                    case Cursor.FIELD_TYPE_INTEGER:
                        assertEquals((long)values.getAsLong(key), cursor.getLong(cursorColumnIndex));
                        break;
                    case Cursor.FIELD_TYPE_STRING:
                        assertEquals(values.getAsString(key), cursor.getString(cursorColumnIndex));
                    default:
                        fail();
                }
            }
        }
    }

    static long create(final ContentResolver mockResolver, final Uri contentUri, final ContentValues entry) {
        final Uri result = mockResolver.insert(contentUri, entry);
        Validate.notNull(result);
        assertThat("Unable to create new entry.", result.getLastPathSegment(), is(not("-1")));
        Validate.notNull(result.getLastPathSegment());
        long identifier = Long.parseLong(result.getLastPathSegment());
        assertThat("Entry inserted under wrong id.", identifier > 0L, is(true));
        return identifier;
    }

    static void read(final ContentResolver mockResolver, final Uri contentUri, final ContentValues entry) {
        try (Cursor cursor = mockResolver.query(contentUri, null, null, null, null);) {
            List<ContentValues> fixture = new ArrayList<>();
            fixture.add(entry);
            Validate.notNull(cursor);
            TestUtils.compareCursorWithValues(cursor, fixture);
        }
    }

    static void update(final ContentResolver mockResolver, final Uri contentUri, final long identifier,
                       final String columnName, final double changedValue) {
        ContentValues changedValues = new ContentValues();
        changedValues.put(columnName, changedValue);

        final int rowsUpdated = mockResolver.update(contentUri, changedValues, BaseColumns._ID + "=?",
                new String[] {Long.valueOf(identifier).toString()});
        assertEquals("Update of rotation point was unsuccessful.", 1, rowsUpdated);
    }

    static void delete(final ContentResolver mockResolver, final Uri contentUri, final long identifier) {
        final int rowsDeleted = mockResolver.delete(contentUri, BaseColumns._ID + "=?",
                new String[] {String.valueOf(identifier)});
        assertEquals("Delete was unsuccessful for uri " + contentUri, 1, rowsDeleted);
    }

    static int count(final ContentResolver mockResolver, final Uri contentUri) {
        final Cursor cursor = mockResolver.query(contentUri, null, null, null, null);
        Validate.notNull(cursor);
        return cursor.getCount();
    }
}
