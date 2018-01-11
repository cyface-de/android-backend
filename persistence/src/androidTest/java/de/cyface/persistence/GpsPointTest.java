/*
 * Created at 16:46:10 on 20.01.2015
 */
package de.cyface.persistence;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.test.ProviderTestCase2;
import android.test.mock.MockContentResolver;

/**
 * <p>
 * Tests whether the content provider for measuring points works or not.
 * </p>
 *
 * @author Klemens Muthmann
 *
 * @version 1.0.2
 * @since 1.0.0
 */
@RunWith(AndroidJUnit4.class)
public final class GpsPointTest extends ProviderTestCase2<MeasuringPointsContentProvider> {

    private MockContentResolver mockResolver;
    private ContentValues values;

    public GpsPointTest() {
        super(MeasuringPointsContentProvider.class, BuildConfig.provider);
    }

    private void cursorEqualsValues(final String message, final Cursor cursor, final ContentValues values) {
        assertEquals(message, 1, cursor.getCount());
        cursor.moveToFirst();

        assertEquals(
                values.get(GpsPointsTable.COLUMN_GPS_TIME),
                cursor.getLong(cursor.getColumnIndex(GpsPointsTable.COLUMN_GPS_TIME)));
        assertEquals(
                values.get(GpsPointsTable.COLUMN_LAT),
                cursor.getFloat(cursor.getColumnIndex(GpsPointsTable.COLUMN_LAT)));
        assertEquals(
                values.get(GpsPointsTable.COLUMN_LON),
                cursor.getFloat(cursor.getColumnIndex(GpsPointsTable.COLUMN_LON)));
        assertEquals(
                values.get(GpsPointsTable.COLUMN_MEASUREMENT_FK),
                cursor.getInt(cursor.getColumnIndex(GpsPointsTable.COLUMN_MEASUREMENT_FK)));
        assertEquals(values.get(GpsPointsTable.COLUMN_SPEED),
                cursor.getFloat(cursor.getColumnIndex(GpsPointsTable.COLUMN_SPEED)));
        assertEquals(values.get(GpsPointsTable.COLUMN_ACCURACY),
                cursor.getInt(cursor.getColumnIndex(GpsPointsTable.COLUMN_ACCURACY)));
    }

    @Override
    @Before
    public void setUp() throws Exception {
        setContext(InstrumentationRegistry.getTargetContext());
        super.setUp();
        mockResolver = getMockContentResolver();
        values = new ContentValues();
        values.put(GpsPointsTable.COLUMN_GPS_TIME, 1234567890L);
        values.put(GpsPointsTable.COLUMN_LAT, 51.03624633f);
        values.put(GpsPointsTable.COLUMN_LON, 13.78828128f);
        values.put(GpsPointsTable.COLUMN_SPEED, 2.0f);
        values.put(GpsPointsTable.COLUMN_ACCURACY, 300);
        values.put(GpsPointsTable.COLUMN_MEASUREMENT_FK, 2);
        mockResolver.insert(Uri.parse("content://"+BuildConfig.provider+"/measuring"), values);
    }

    @Test
    public void testDeleteAllMeasuringPoints() throws Exception {
        assertEquals(1, mockResolver.delete(Uri.parse("content://"+BuildConfig.provider+"/measuring"), null, null));
    }

    @Test
    public void testDeleteMeasuringPointViaSelection() throws Exception {
        assertEquals(1, mockResolver.delete(
                Uri.parse("content://"+BuildConfig.provider+"/measuring"), BaseColumns._ID + "= ?", new String[] {"1"}));
    }

    @Test
    public void testDeleteMeasuringPointViaURL() throws Exception {
        assertEquals(1, mockResolver.delete(Uri.parse("content://"+BuildConfig.provider+"/measuring/1"), null, null));
    }

    @Test
    public void testCreateMeasuringPoint() throws Exception {
        Uri insert = mockResolver.insert(Uri.parse("content://"+BuildConfig.provider+"/measuring"), values);
        assertFalse(insert.getLastPathSegment().equals("-1"));
        assertEquals("2", insert.getLastPathSegment());
    }

    @Test
    public void testReadMeasuringPoint() throws Exception {
        Cursor urlQuery = null;
        Cursor selectionQuery = null;
        Cursor allQuery = null;
        try {
            // Select
            urlQuery = mockResolver.query(Uri.parse("content://"+BuildConfig.provider+"/measuring/1"), null, null, null, null);
            cursorEqualsValues("Unable to load all measuring points via URI.", urlQuery, values);

            selectionQuery = mockResolver.query(
                    Uri.parse("content://"+BuildConfig.provider+"/measuring"), null, BaseColumns._ID + "=?",
                    new String[] {"1"}, null);
            cursorEqualsValues("Unable to load measuring point via selection.", selectionQuery, values);

            allQuery = mockResolver.query(Uri.parse("content://"+BuildConfig.provider+"/measuring"), null, null, null, null);
            cursorEqualsValues("Unable to load all measuring points via URI.", allQuery, values);
        } finally {
            if (urlQuery != null) {
                urlQuery.close();
            }
            if (selectionQuery != null) {
                selectionQuery.close();
            }
            if (allQuery != null) {
                allQuery.close();
            }
        }
    }

    @Test
    public void testUpdateMeasuringPoint() throws Exception {
        ContentValues newValues = new ContentValues();
        newValues.put(GpsPointsTable.COLUMN_LAT, 10.34f);

        assertEquals(
                1, mockResolver.update(Uri.parse("content://"+BuildConfig.provider+"/measuring/1"), newValues, null, null));

        Cursor query = null;
        try {
            query = mockResolver.query(Uri.parse("content://"+BuildConfig.provider+"/measuring/1"), null, null, null, null);
            assertEquals(1, query.getCount());
            query.moveToFirst();
            int columnIndex = query.getColumnIndex(GpsPointsTable.COLUMN_LAT);
            assertEquals(10.34f, query.getFloat(columnIndex));
        } finally {
            if (query != null) {
                query.close();
            }
        }
    }

}
