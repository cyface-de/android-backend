/*
 * Created on 03.12.15 at 21:02
 */
package de.cyface.persistence;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.test.ProviderTestCase2;
import android.test.mock.MockContentResolver;

import java.util.ArrayList;
import java.util.List;

import de.cynav.persistence.BuildConfig;

/**
 * <p>
 * Abstract base class for all tests working on cyface tables.
 * </p>
 *
 * @author Klemens Muthmann
 * @version 1.0.1
 * @since 1.0.0
 */
public abstract class CyfaceDatabaseTest extends ProviderTestCase2<MeasuringPointsContentProvider> {

    private MockContentResolver mockContentResolver;
    protected abstract Uri getTableUri();

    /**
     * Default constructor sets the superclass to use the {@code MeasuringPointsContentProvider} and the package
     * {@code BuildConfig.provider}.
     */
    public CyfaceDatabaseTest() {
        super(MeasuringPointsContentProvider.class, BuildConfig.provider);
    }

    @Override public MockContentResolver getMockContentResolver() {
        return mockContentResolver;
    }

    @Override
    public void setUp() throws Exception {
//        setContext(InstrumentationRegistry.getTargetContext());
        super.setUp();
        mockContentResolver = super.getMockContentResolver();

    }

    protected final void create(final ContentValues entry, final String expectedIdentifier) {
        create(entry,expectedIdentifier,getTableUri());
    }

    protected final void create(final ContentValues entry, final String expectedIdentifier, final Uri uri) {
        final Uri result = mockContentResolver.insert(uri, entry);
        assertFalse("Unable to create new entry.", result.getLastPathSegment().equals("-1"));
        String identifier = result.getLastPathSegment();
        assertEquals("Entry inserted under wrong id.", expectedIdentifier, identifier);
    }

    protected final void read(final ContentValues entry) {
        Cursor cursor = null;
        try {
            cursor = mockContentResolver.query(getTableUri(), null, null, null, null);
            List<ContentValues> fixture = new ArrayList<>();
            fixture.add(entry);
            TestUtils.compareCursorWithValues(cursor,fixture);
        } finally {
            cursor.close();
        }
    }

    protected final void update(final String identifier, final String columnName, final double changedValue) {
        ContentValues changedValues = new ContentValues();
        changedValues.put(columnName, changedValue);

        final int rowsUpdated = mockContentResolver.update(getTableUri(),
                changedValues, BaseColumns._ID + "=?", new String[] {identifier});
        assertEquals("Update of rotation point was unsuccessful.", 1, rowsUpdated);
    }

    protected final void delete(final long identifier) {
        final int rowsDeleted = mockContentResolver.delete(getTableUri(),BaseColumns._ID+ "=?", new String[]{String.valueOf(identifier)});
        assertEquals("Delete was unsuccessful for uri "+getTableUri(),1,rowsDeleted);
    }
}