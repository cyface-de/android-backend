/*
 * Created on 30.11.15 at 22:39
 */
package de.cyface.persistence;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.test.MoreAsserts;
import android.test.mock.MockContentResolver;

import java.util.ArrayList;
import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.fail;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * <p>
 * A utility class with static methods for supporting tests on the persistence code.
 * </p>
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
class TestUtils {
    private static void compareCursorWithValues(final Cursor cursor, final List<ContentValues> contentValues) {
        assertThat(contentValues.size()<=cursor.getCount(),is(equalTo(true)));
        for (ContentValues values : contentValues) {
            cursor.moveToNext();
            for (String key : values.keySet()) {
                int cursorColumnIndex = cursor.getColumnIndex(key);
                assertFalse(cursorColumnIndex == -1);
                int dataType = cursor.getType(cursorColumnIndex);
                switch (dataType) {
                    case Cursor.FIELD_TYPE_FLOAT:
                        assertEquals(values.getAsDouble(key), cursor.getDouble(cursorColumnIndex), 0.01);
                        break;
                    case Cursor.FIELD_TYPE_INTEGER:
                        assertEquals((long) values.getAsLong(key), cursor.getLong(cursorColumnIndex));
                        break;
                    case Cursor.FIELD_TYPE_STRING:
                        assertEquals(values.getAsString(key), cursor.getString(cursorColumnIndex));
                    default:
                        fail();
                }
            }
        }

    }

    static long create(final MockContentResolver mockResolver, final Uri contentUri, final ContentValues entry) {
        final Uri result = mockResolver.insert(contentUri, entry);
        assertFalse("Unable to create new entry.", result.getLastPathSegment().equals("-1"));
        long identifier = Long.parseLong(result.getLastPathSegment());
        assertThat("Entry inserted under wrong id.",identifier > 0L,is(true));
        return identifier;
    }

    static void read(final MockContentResolver mockResolver, final Uri contentUri, final ContentValues entry) {
        try (Cursor cursor = mockResolver.query(contentUri, null, null, null, null);) {
            List<ContentValues> fixture = new ArrayList<>();
            fixture.add(entry);
            TestUtils.compareCursorWithValues(cursor,fixture);
        }
    }

    static void update(final MockContentResolver mockResolver, final Uri contentUri, final long identifier, final String columnName, final double changedValue) {
        ContentValues changedValues = new ContentValues();
        changedValues.put(columnName, changedValue);

        final int rowsUpdated = mockResolver.update(contentUri,
                changedValues, BaseColumns._ID + "=?", new String[] {Long.valueOf(identifier).toString()});
        assertEquals("Update of rotation point was unsuccessful.", 1, rowsUpdated);
    }

    static void delete(final MockContentResolver mockResolver, final Uri contentUri, final long identifier) {
        final int rowsDeleted = mockResolver.delete(contentUri,BaseColumns._ID+ "=?", new String[]{String.valueOf(identifier)});
        assertEquals("Delete was unsuccessful for uri "+contentUri,1,rowsDeleted);
    }

    static int count(final MockContentResolver mockResolver, final Uri contentUri) {
        return mockResolver.query(contentUri,null,null,null,null).getCount();
    }
}
