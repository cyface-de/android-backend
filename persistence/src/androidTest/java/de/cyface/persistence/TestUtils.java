/*
 * Created on 30.11.15 at 22:39
 */
package de.cyface.persistence;

import android.content.ContentValues;
import android.database.Cursor;
import android.test.MoreAsserts;

import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.fail;

/**
 * <p>
 * A utility class with static methods for supporting tests on the persistence code.
 * </p>
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
public class TestUtils {
    public static void compareCursorWithValues(final Cursor cursor, final List<ContentValues> contentValues) {
        assertEquals(contentValues.size(), cursor.getCount());
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


}
