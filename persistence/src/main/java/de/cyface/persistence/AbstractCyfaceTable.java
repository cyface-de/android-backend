package de.cyface.persistence;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Abstract base class for all Cyface measurement tables implementing common functionality.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.0
 * @since 1.0.0
 */
public abstract class AbstractCyfaceTable implements CyfaceTable {

    /**
     * The database table name.
     */
    private final String name;

    AbstractCyfaceTable(final String name) {
        if (name.isEmpty()) {
            throw new IllegalStateException("Database table name may not be empty.");
        }

        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public final long insertRow(final SQLiteDatabase database, final ContentValues values) {
        return database.insert(getName(), null, values);
    }

    // BulkInsert is about 80 times faster than insertBatch
    @Override
    public final long[] insertBatch(SQLiteDatabase db, final List<ContentValues> valuesList) {
        long[] ret = new long[valuesList.size()];
        db.beginTransaction();
        try {
            int len = valuesList.size();
            for (int i = 0; i < len; i++) {
                ret[i] = insertRow(db, valuesList.get(i));
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return ret;
    }

    @Override
    public final int deleteRow(final SQLiteDatabase database, final String selection, final String[] selectionArgs) {
        return database.delete(getName(), selection, selectionArgs);
    }

    @Override
    public void onCreate(final SQLiteDatabase database) {
        database.execSQL(getCreateStatement());
    }

    protected abstract String getCreateStatement();

    @Override
    public abstract void onUpgrade(final SQLiteDatabase database, final int oldVersion, final int newVersion);

    @Override
    public Cursor query(final SQLiteDatabase database, final String[] projection, final String selection,
                        final String[] selectionArgs, final String sortOrder) {
        checkColumns(projection);
        /*LOGGER.debug("Querying database table {} with projection {} selection {} and arguments {} limit {} isACountingQuery: {}",
                getName(), projection, selection, Arrays.toString(selectionArgs), queryLimit, isACountingQuery(projection));*/
        return database.query(getName(), projection, selection, selectionArgs, null, null, sortOrder);
    }

    protected void checkColumns(String[] projection) {
        if (projection != null) {
            Set<String> requestedColumns = new HashSet<>(Arrays.asList(projection));
            Set<String> availableColumns = new HashSet<>(Arrays.asList(getDatabaseTableColumns()));
            if (!availableColumns.containsAll(requestedColumns)) {
                throw new IllegalArgumentException("Unknown columns in projection");
            }
        }
    }

    protected abstract String[] getDatabaseTableColumns();

    @Override
    public int update(SQLiteDatabase database, ContentValues values, String selection,
                      String[] selectionArgs) {
        return database.update(getName(), values, selection, selectionArgs);
    }

    @Override
    public String toString() {
        return name;
    }
}
