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

import static de.cyface.persistence.Constants.TAG;

import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import de.cyface.persistence.model.Event;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.serialization.Point3dFile;
import de.cyface.utils.Validate;

/**
 * The <code>DatabaseHelper</code> class is the part of the content provider where the hard part takes place. It
 * distributes queries from the <code>MeasuringPointsContentProvider</code> to the correct tables.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 4.5.1
 * @since 1.0.0
 */
class DatabaseHelper extends SQLiteOpenHelper {

    /**
     * Name of the database used by the content provider to store data.
     */
    private static final String DATABASE_NAME = "measures";
    /**
     * Increase the DATABASE_VERSION if the database structure changes with a new update
     * but don't forget to adjust onCreate and onUpgrade accordingly for the new structure and incremental upgrade
     */
    private final static int DATABASE_VERSION = 16;
    /**
     * The table containing all the measurements, without the corresponding data. Data is stored in one table per type.
     */
    private final MeasurementTable measurementTable;
    /**
     * The table to store all the geo locations captured on the device.
     */
    private final GeoLocationsTable geoLocationsTable;
    /**
     * The table to store the device identifier to make sure its reset when the database, and thus the next measurement
     * id count is reset, too.
     */
    private final IdentifierTable identifierTable;
    /**
     * The table to store the {@link Event}s on the device.
     */
    private final EventTable eventTable;

    /**
     * Creates a new completely initialized <code>DatabaseHelper</code>.
     *
     * @param context The Android context to use to access the Android System via
     */
    DatabaseHelper(final @NonNull Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);

        // Current database structure
        measurementTable = new MeasurementTable();
        geoLocationsTable = new GeoLocationsTable();
        identifierTable = new IdentifierTable(context);
        eventTable = new EventTable();
    }

    /**
     * The onCreate method is called when the app is freshly installed (i.e. there is no data yet on the phone)
     * Update this (in DatabaseHelper()) if the database structure changes
     *
     * @param db the database in which the data shall be stored
     */
    @Override
    public void onCreate(final SQLiteDatabase db) {
        identifierTable.onCreate(db);
        measurementTable.onCreate(db);
        geoLocationsTable.onCreate(db);
        eventTable.onCreate(db);
    }

    /**
     * The onUpgrade method is called when the app is upgraded and the DATABASE_VERSION changed.
     * <p>
     * This method is not called incrementally by the system which is why this method implements this.
     * <p>
     * This method is automatically executed in a transaction, do not wrap the code in another transaction!
     *
     * @param database the database which shall be upgraded
     * @param oldVersion the database version the app was in before the upgrade
     * @param newVersion the database version of the new, upgraded app which shall be reached
     */
    @Override
    public void onUpgrade(final SQLiteDatabase database, final int oldVersion, final int newVersion) {
        Validate.isTrue(oldVersion == 8 || oldVersion >= 11, "Unsupported versions");

        // Upgrade incrementally to reduce the amount of migration code required
        for (int fromVersion = oldVersion; fromVersion < newVersion; fromVersion++) {
            final int toVersion = fromVersion + 1;
            Log.w(TAG, "Upgrading database from version " + fromVersion + " to " + toVersion);

            // Upgrade code for tables which don't exist anymore as class
            // noinspection SwitchStatementWithTooFewBranches - because others will follow and it's an easier read
            switch (fromVersion) {

                case 8:
                    // Upgrade from V8 which was the last version in SDK V2.
                    // We don't support a data preserving upgrade for sensor data stored in the database
                    Log.w(TAG, "Dropping sensor data from database");

                    // The following tables and table names are deprecated, thus, hard-coded
                    // database.execSQL("DELETE FROM sqlite_sequence;");
                    database.execSQL("DELETE FROM sample_points;");
                    database.execSQL("DROP TABLE sample_points;");
                    database.execSQL("DELETE FROM rotation_points;");
                    database.execSQL("DROP TABLE rotation_points;");
                    database.execSQL("DELETE FROM magnetic_value_points;");
                    database.execSQL("DROP TABLE magnetic_value_points;");

                    break; // upgrades are called incrementally
            }

            // Incremental upgrades for existing tables
            measurementTable.onUpgrade(database, fromVersion, toVersion);
            geoLocationsTable.onUpgrade(database, fromVersion, toVersion);
            identifierTable.onUpgrade(database, fromVersion, toVersion);
            eventTable.onUpgrade(database, fromVersion, toVersion);
        }
    }

    /**
     * Deletes one or multiple rows (depending on the format of the provided URI) from the database. If you delete a
     * {@link Measurement} all corresponding {@link GeoLocation} data is cascadingly deleted as well.
     *
     * @param uri The URI specifying the table to delete from. If this ends with a single numeric identifier that row is
     *            deleted otherwise multiple rows might be deleted depending on the <code>selection</code> and
     *            <code>selectionArgs</code>.
     * @param selection The part of an SQL where statement behind the where. You can use '?' as a placeholder to secure
     *            yourself from SQL injections.
     * @param selectionArgs The arguments to place inside the '?' placeholder from <code>selection</code>.
     * @return The number of rows deleted.
     */
    int deleteRow(final @NonNull Uri uri, final String selection, final String[] selectionArgs) {
        List<String> pathSegments = uri.getPathSegments();
        SQLiteDatabase database = getWritableDatabase();

        CyfaceMeasurementTable table = matchTable(uri);

        int ret = 0;
        database.beginTransaction();
        try {
            if (pathSegments.size() == 2) {
                String rowIdentifier = pathSegments.get(1);
                switch (pathSegments.get(0)) {
                    case MeasurementTable.URI_PATH:
                        // Measurement requires to also delete all dependent entries and then call table.deleteRow
                        // All other database entries just call table.deleteRow directly.

                        ret += deleteDataForMeasurement(database, Long.parseLong(rowIdentifier));
                        // continues here until return ! -->
                    case GeoLocationsTable.URI_PATH:
                    case EventTable.URI_PATH:
                        // Add the id specified by the URI to implement expected behaviour of a content resolver, where
                        // the last element of the path is the identifier of the element requested. This is only
                        // necessary for single row deletions.
                        String adaptedSelection = BaseColumns._ID + "=" + rowIdentifier
                                + (selection == null ? "" : " AND " + selection);
                        ret += table.deleteRow(getWritableDatabase(), adaptedSelection, selectionArgs);
                        database.setTransactionSuccessful();
                        return ret;
                    default:
                        throw new IllegalStateException("Unknown table identified by content provider URI: " + uri);
                }
            } else if (pathSegments.size() == 1) {
                switch (pathSegments.get(0)) {
                    case EventTable.URI_PATH:
                        ret += table.deleteRow(getWritableDatabase(), selection, selectionArgs);
                        database.setTransactionSuccessful();
                        return ret;
                    case IdentifierTable.URI_PATH:
                        ret += table.deleteRow(getWritableDatabase(), selection, selectionArgs);
                        database.setTransactionSuccessful();
                        return ret;
                    case MeasurementTable.URI_PATH:
                        try (final Cursor selectedMeasurementsCursor = query(uri, new String[] {BaseColumns._ID}, selection,
                                selectionArgs, null)) {
                            while (selectedMeasurementsCursor.moveToNext()) {
                                ret += deleteDataForMeasurement(database, selectedMeasurementsCursor
                                        .getLong(selectedMeasurementsCursor.getColumnIndex(BaseColumns._ID)));
                            }
                        }
                        // continues here until return ! -->
                    case GeoLocationsTable.URI_PATH:
                        ret += table.deleteRow(getWritableDatabase(), selection, selectionArgs);
                        database.setTransactionSuccessful();
                        return ret;
                    default:
                        throw new IllegalStateException("Unable to delete data from table corresponding to URI " + uri
                                + ". There seems to be no such table.");
                }
            } else {
                throw new IllegalStateException("Invalid path in content provider URI: " + uri);
            }
        } finally {
            database.endTransaction();
        }
    }

    /**
     * Cascadingly deletes all data for a single {@link Measurement} from the database. This only includes
     * {@link GeoLocation}s and {@link Event}s but not the {@link Point3dFile}s as they are not stored in database.
     *
     * @param database The database object to delete from.
     * @param measurementIdentifier The device wide unique identifier of the measurement to delete.
     * @return The number of rows deleted.
     */
    private int deleteDataForMeasurement(final @NonNull SQLiteDatabase database, final long measurementIdentifier) {
        final String[] identifierAsArgs = new String[] {Long.valueOf(measurementIdentifier).toString()};
        int ret = 0;

        ret += geoLocationsTable.deleteRow(database, GeoLocationsTable.COLUMN_MEASUREMENT_FK + "=?", identifierAsArgs);
        ret += eventTable.deleteRow(database, EventTable.COLUMN_MEASUREMENT_FK + "=?", identifierAsArgs);
        return ret;
    }

    /**
     * Inserts a single row into a table.
     *
     * @param uri The table to insert the row into.
     * @param values The values to insert in the new table row.
     * @return The identifier of the new row.
     */
    long insertRow(final @NonNull Uri uri, final @NonNull ContentValues values) {
        final CyfaceMeasurementTable table = matchTable(uri);
        return table.insertRow(getWritableDatabase(), values);
    }

    /**
     * Inserts a list of <code>ContentValues</code> as new rows into a table.
     *
     * @param uri The table to insert the new rows into.
     * @param values The values to insert.
     * @return An array of identifiers for the newly created table rows.
     */
    long[] bulkInsert(final @NonNull Uri uri, final @NonNull List<ContentValues> values) {
        CyfaceMeasurementTable table = matchTable(uri);
        return table.insertBatch(getWritableDatabase(), values);
    }

    /**
     * Sends a query to the database and provides the result of that query.
     *
     * @param uri The table to send the query to.
     * @param projection The projection is the part behind the SQL select statement without the <code>SELECT</code>.
     * @param selection A selection query used to select a set of rows to update. This is the part behind the SQL where
     *            statement without the 'WHERE'. You may use '?' placeholders to avoid SQL injection attacks.
     * @param selectionArgs The arguments to insert for the placeholders used in <code>selection</code>. The amount of
     *            '?' and arguments needs to match.
     * @param sortOrder This is either <code>ASC</code> for ascending or <code>DESC</code> for descending.
     * @return A <code>Cursor</code> over the resulting rows. Do not forget to close this cursor after you finished
     *         reading from it. Preferably use <code>finally</code> to close the cursor to avoid memory leaks.
     */
    public Cursor query(final @NonNull Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        final CyfaceMeasurementTable table = matchTable(uri);
        if (uri.getPathSegments().size() == 1) {
            return table.query(getReadableDatabase(), projection, selection, selectionArgs, sortOrder);
        } else if (uri.getPathSegments().size() == 2) {
            String adaptedSelection = BaseColumns._ID + "=" + uri.getLastPathSegment()
                    + (selection == null ? "" : " AND " + selection);
            return table.query(getReadableDatabase(), projection, adaptedSelection, selectionArgs, sortOrder);
        } else {
            throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }

    /**
     * Updates a row or rows in the database with new values.
     *
     * @param uri The table to update rows in.
     * @param values The new values to insert.
     * @param selection A selection query used to select a set of rows to update. This is the part behind the SQL where
     *            statement without the 'WHERE'. You may use '?' placeholders to avoid SQL injection attacks.
     * @param selectionArgs The arguments to insert for the placeholders used in <code>selection</code>. The amount of
     *            '?' and arguments needs to match.
     * @return The number of updated rows.
     */
    public int update(final @NonNull Uri uri, final @NonNull ContentValues values, final String selection,
            final String[] selectionArgs) {
        CyfaceMeasurementTable table = matchTable(uri);
        if (uri.getPathSegments().size() == 1) {
            return table.update(getWritableDatabase(), values, selection, selectionArgs);
        } else if (uri.getPathSegments().size() == 2) {
            final String id = uri.getLastPathSegment();
            final String adaptedSelection = BaseColumns._ID + "=" + id
                    + (TextUtils.isEmpty(selection) ? "" : " AND " + selection);
            return table.update(getWritableDatabase(), values, adaptedSelection, selectionArgs);
        } else {
            throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }

    /**
     * Provides the MIME type for the provided URI. This is either <code>vnd.android.cursor.item/de.cyface.data</code>
     * for an URI identifying a single item, or <code>vnd.android.cursor.dir/de.cyface.data</code> for an URI
     * identifying a collection of items.
     *
     * @param uri The URI to provide the MIME type for.
     * @return The MIME type corresponding to that URI.
     */
    String getType(final @NonNull Uri uri) {
        if (uri.getPathSegments().size() == 1) {
            return "vnd.android.cursor.item/de.cyface.data";
        } else if (uri.getPathSegments().size() == 2) {
            return "vnd.android.cursor.dir/de.cyface.data";
        } else {
            throw new IllegalStateException("Invalid content provider URI: " + uri);
        }
    }

    /**
     * Matches the provided <code>uri</code> with the corresponding table and returns that table.
     *
     * @param uri A content provider uri referring to a table within this content provider. if this <code>uri</code> is
     *            not valid an <code>IllegalArgumentException</code> is thrown.
     * @return The table corresponding to the provided <code>uri</code>.
     */
    private CyfaceMeasurementTable matchTable(final @NonNull Uri uri) {
        String firstPathSegment = uri.getPathSegments().get(0);

        switch (firstPathSegment) {
            case MeasurementTable.URI_PATH:
                return measurementTable;
            case GeoLocationsTable.URI_PATH:
                return geoLocationsTable;
            case IdentifierTable.URI_PATH:
                return identifierTable;
            case EventTable.URI_PATH:
                return eventTable;
            default:
                throw new IllegalStateException("Unknown table with URI: " + uri);
        }
    }
}
