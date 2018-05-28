package de.cyface.persistence;

import java.util.List;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Base interface for all tables used by this content provider. You need to provide the SQLite database to every method
 * call for performance reasons. That way the connection to the database need not be constructed before the first real
 * call to any data.
 */
public interface CyfaceMeasurementTable {
    /**
     * Called during creation of the database if not already present. This should execute some SQL statements to
     * actually create the table with the correct schema.
     *
     * @param db The database to create the table in.
     */
    void onCreate(SQLiteDatabase db);

    /**
     * Called during an update of the database. This usually happens if {@code newVersion} is different from
     * {@code oldVersion} and should execute some SQL statements to upgrade the old table format to the new one. If no
     * changes happened from one version to the other it can stay the same. If you don't care about the data in the
     * table after an update, you can just delete the old table and create an empty new one. This is the easiest
     * implementation but you WILL LOSE ALL DATA.
     *
     * @param database The database to upgrade the table for.
     * @param oldVersion The prior version number.
     * @param newVersion The current version number.
     */
    void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion);

    /**
     * Called whenever someone deletes a row in the database. The method should call some SQL statement to actually
     * delete the row in the database.
     *
     * @param database The database containing this table.
     * @param selection Selection statement as supported by the Android API, probably with ? for placeholders. This
     *            corresponds to the SQL WHERE clause.
     * @param selectionArgs Concrete values for the ? placeholders in the {@code selection} parameter. Use this to avoid
     *            SQL injection attacks.
     * @return The number of rows deleted or -1 if now rows have been deleted.
     */
    int deleteRow(SQLiteDatabase database, String selection, String[] selectionArgs);

    /**
     * @return The database name as used in the SQLite database itself.
     */
    String getName();

    /**
     * Inserts a new data row into the this table.
     *
     * @param database The database the table belongs to.
     * @param values The new values to insert.
     * @return The identifier of the newly created row as referenced by {@code BaseColumns#_ID}.
     */
    long insertRow(SQLiteDatabase database, ContentValues values);

    /**
     * Query the table for specific data rows.
     *
     * @param database The database this table belongs to.
     * @param projection The set of rows to select from the table. This is the part you would place behind an SQL SELECT
     *            statement. You may pass null to indicate that all columns should be selected.
     * @param selection The conditions a row needs to meet to be selected. This is the part you would place into the SQL
     *            WHERE statement. You may insert placeholders via ?. You may pass {@code null} if there is no selection
     *            required.
     * @param selectionArgs The values used to replace the ? placeholders in the {@code selection} part. You may pass
     *            {@code null} if there are no selection arguments. Use this to avoid SQL injection attacks.
     * @param sortOrder The sort order of the returned rows formatted as SQL ORDER BY value without the ORDER BY. So
     *            usually either "asc" or "desc". Passing {@code null} will result in no particular sort order.
     * @return A cursor running over the selected rows.
     */
    Cursor query(SQLiteDatabase database, String[] projection, String selection, String[] selectionArgs,
            String sortOrder);

    /**
     * Update a specific row of this table with the provided values.
     *
     * @param database The database this table belongs to.
     * @param values The values to update for the specified row.
     * @param selection A selection statement selecting exactly one row to update. May contain ? placeholders filled by
     *            {@code selectionArgs}. This is what you usually expect behind an SQL WHERE statement.
     * @param selectionArgs The values for the ? placeholders within {@code selection}. Use this to avoid SQL injection
     *            attacks.
     * @return
     */
    int update(SQLiteDatabase database, ContentValues values, String selection, String[] selectionArgs);

    /**
     * Allows to batch insert multiple values into this table.
     *
     * @param database The database the table is part of.
     * @param valuesList The list of values for each row to insert.
     * @return the list of identifiers for the newly created table rows.
     */
    long[] insertBatch(SQLiteDatabase database, final List<ContentValues> valuesList);
}
