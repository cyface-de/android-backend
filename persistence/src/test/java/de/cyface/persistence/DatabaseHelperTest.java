/*
 * Copyright 2019 Cyface GmbH
 * This file is part of the Cyface SDK for Android.
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.persistence;

import static de.cyface.testutils.SharedTestUtils.generateGeoLocation;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQuery;

import androidx.test.core.app.ApplicationProvider;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.testutils.SharedTestUtils;

/**
 * This class tests the migration functionality of {@link DatabaseHelper}.
 * <p>
 * To create database the sample data SQL you can capture data with the app, export the SQLite database,
 * open it with *DB Browser for SQLite* and use File > Export > Database to SQL file.
 *
 * @author Armin Schnabel
 * @version 1.1.0
 * @since 4.0.0
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE) // To avoid warning
public class DatabaseHelperTest {

    /**
     * We require Mockito to avoid calling Android system functions. This rule is responsible for the initialization of
     * the Spies and Mocks.
     */
    private SQLiteDatabase db;
    /**
     * The object of the class under test
     */
    private DatabaseHelper oocut;
    /**
     * The "base" from where the distance of generated nodes should be calculated, see
     * {@link SharedTestUtils#generateGeoLocation(int)}. This is usually 0.
     */
    private final int base = 0;
    /**
     * The distance between the {@link #location1} and {@link #location2}.
     */
    private final int expectedDistance1 = 2;
    /**
     * The distance between the {@link #location2} and {@link #location3}.
     */
    private final int expectedDistance2 = 3;
    /**
     * The 1st test location of a test measurement.
     */
    private final GeoLocation location1 = generateGeoLocation(base);
    /**
     * The 2nd test location of a test measurement.
     */
    private final GeoLocation location2 = generateGeoLocation(expectedDistance1);
    /**
     * The 3rd test location of a test measurement.
     */
    private final GeoLocation location3 = generateGeoLocation(expectedDistance1 + expectedDistance2);

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        oocut = new DatabaseHelper(context);

        // Create a memory-backed database which is destroyed on close
        SQLiteDatabase.CursorFactory cursorFactory = new SQLiteDatabase.CursorFactory() {
            @Override
            public Cursor newCursor(final SQLiteDatabase db, final SQLiteCursorDriver masterQuery,
                    final String editTable, final SQLiteQuery query) {
                return new SQLiteCursor(masterQuery, editTable, query);
            }
        };
        db = SQLiteDatabase.create(cursorFactory);
    }

    /**
     * Clean the database after each test.
     */
    @After
    public void tearDown() {
        db.close();
    }

    /**
     * Test that loading the EventTable which was introduced in the Database V12 Upgrade works.
     * <p>
     * This database upgrade V12 was part of (STAD-6)!
     */
    @Test
    public void testMigrationV11ToV12() {

        // Arrange
        createV11DatabaseWithData(db);

        // Act - This is how the method is called by the system (not incrementally!)
        oocut.onUpgrade(db, 11, 12);

        // Assert
        // Loading from the newly added table must work (STAD-85)
        db.execSQL("SELECT * FROM events;");
    }

    /**
     * Test that loading the EventTable which was introduced in the Database V12 Upgrade works
     * after upgrading from the SDK 2.X which was included in the first official STAD release (#776b323540).
     * <p>
     * The database upgrade V12 was part of (STAD-6)!
     */
    @Test
    public void testMigrationV8ToV12() {

        // Arrange
        createV8DatabaseWithData(db);
        double expectedLocation123Distance = expectedDistance1 + expectedDistance2;

        // Act - This is how the method is called by the system (not incrementally!)
        oocut.onUpgrade(db, 8, 12);

        // Assert
        // Loading from the newly added table must work (STAD-85)
        db.execSQL("SELECT * FROM events;");

        // Make sure the relevant data from before the upgrade still exists
        Cursor cursor = null;
        try {
            // Measurements must still exist after the upgrade
            cursor = db.query("measurements", null, null, null, null, null, null, null);
            assertThat(cursor.getCount(), is(equalTo(3)));
            cursor = db.query("measurements", null, "status = ? AND distance = ? AND file_format_version = ?",
                    new String[] {"SYNCED", "0.0", "1"}, null, null, null, null);
            assertThat(cursor.getCount(), is(equalTo(1)));
            cursor = db.query("measurements", null, "status = ? AND distance = ? AND file_format_version = ?",
                    new String[] {"OPEN", "0.0", "1"}, null, null, null, null);
            assertThat(cursor.getCount(), is(equalTo(1)));

            // Here we also check that the distance is calculated as expected
            cursor = db.query("measurements", new String[] {"distance"}, "status = ? AND file_format_version = ?",
                    new String[] {"FINISHED", "1"}, null, null, null, null);
            assertThat(cursor.getCount(), is(equalTo(1)));
            cursor.moveToNext();
            final int distanceColumnIndex = cursor.getColumnIndex("distance");
            final double loadedDistance = cursor.getDouble(distanceColumnIndex);
            assertThat(loadedDistance, is(closeTo(expectedLocation123Distance, 0.1)));

            // GeoLocations must still exist after the upgrade
            cursor = db.query("locations", null, null, null, null, null, null, null);
            assertThat(cursor.getCount(), is(equalTo(5)));
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

    }

    /**
     * Test that creating a fresh database for the current database version works as expected.
     * <p>
     * Should be okay to only test this (always) for the current version at the time of execution
     * as an older database version is never created when there is already a newer version.
     */
    @Test
    public void testCreateCurrentVersion() {

        // Arrange - nothing to do

        // Act
        oocut.onCreate(db);

        // Assert - currently only checking that there is not exception
    }

    private void createV12Database(SQLiteDatabase db) {
        throw new IllegalStateException("no yet implemented");
        // This is simpler than duplicating the code from last version
        // createV11DatabaseWithData(db);
        // oocut.onUpgrade(db, 11, 12);

        // Here the sample code from an V12 export for the next migration test
        /*
         * CREATE TABLE events (
         * _id INTEGER PRIMARY KEY AUTOINCREMENT,
         * timestamp INTEGER NOT NULL,
         * type TEXT NOT NULL,
         * measurement_fk INTEGER
         * );
         * INSERT INTO events (_id,timestamp,type,measurement_fk) VALUES (7,1552322118501,'LIFECYCLE_START',25),
         * (8,1552323369059,'LIFECYCLE_STOP',25),
         * (17,1552412961053,'LIFECYCLE_START',30),
         */
    }

    /**
     * Creates a database as it would have been created with {@link DatabaseHelper#DATABASE_VERSION} 11.
     * <p>
     * <b>Attention:</b>
     * It's important that the create statements only contains hardcoded Strings as the table and column names
     * should be the same as they were in that version to really test the migration as it would happen in real.
     *
     * @param db A clean {@link SQLiteDatabase} to use for testing.
     */
    private void createV11DatabaseWithData(SQLiteDatabase db) {

        // # Create V11 Tables:

        // Create android_metadata table (exists in SQLite export)
        db.execSQL("DROP TABLE IF EXISTS android_metadata");
        db.execSQL("CREATE TABLE android_metadata (locale TEXT);");
        // Create IdentifierTable
        db.execSQL("CREATE TABLE identifiers (_id INTEGER PRIMARY KEY AUTOINCREMENT, device_id TEXT NOT NULL);");
        // Create MeasurementTable
        db.execSQL("CREATE TABLE measurements (_id INTEGER PRIMARY KEY AUTOINCREMENT, status TEXT NOT NULL, "
                + "vehicle TEXT NOT NULL, accelerations INTEGER NOT NULL, rotations INTEGER NOT NULL, "
                + "directions INTEGER NOT NULL, file_format_version INTEGER NOT NULL, distance REAL NOT NULL);");
        // Create GeoLocationsTable
        db.execSQL("CREATE TABLE locations (_id INTEGER PRIMARY KEY AUTOINCREMENT, gps_time INTEGER NOT NULL, "
                + "lat REAL NOT NULL, lon REAL NOT NULL, speed REAL NOT NULL, accuracy INTEGER NOT NULL, "
                + "measurement_fk INTEGER NOT NULL);");

        // # Insert V11 sample data: (exported from our V12 app and manually adjusted to V11)

        // Insert sample android_metadata table entry (exists in SQLite export)
        db.execSQL("INSERT INTO android_metadata (locale) VALUES ('de_DE');");
        // Insert sample IdentifierTable entry
        db.execSQL("INSERT INTO identifiers (_id,device_id) VALUES (1,'61e112e1-548e-4a90-be28-9d5b31d6875b');");
        // Insert sample MeasurementTable entries - execSQL only supports one insert per commend
        db.execSQL(
                "INSERT INTO measurements (_id,status,vehicle,accelerations,rotations,directions,file_format_version,distance) VALUES "
                        + " (43,'FINISHED','BICYCLE',690481,690336,166370,1,5396.62473698979);");
        // Insert sample GeoLocationsTable entries - execSQL only supports one insert per commend
        db.execSQL("INSERT INTO locations (_id,gps_time,lat,lon,speed,accuracy,measurement_fk) VALUES "
                + " (3,1551431485000,51.05210394,13.72873203,0.0,1179,43);");
    }

    /**
     * Creates a database as it would have been created with {@link DatabaseHelper#DATABASE_VERSION} 8.
     * <p>
     * <b>Attention:</b>
     * It's important that the create statements only contains hardcoded Strings as the table and column names
     * should be the same as they were in that version to really test the migration as it would happen in real.
     *
     * @param db A clean {@link SQLiteDatabase} to use for testing.
     */
    private void createV8DatabaseWithData(SQLiteDatabase db) {

        // # Create V8 Tables:

        // Create android_metadata table (exists in SQLite export)
        db.execSQL("DROP TABLE IF EXISTS android_metadata");
        db.execSQL("CREATE TABLE android_metadata (locale TEXT);");
        // Create MeasurementTable
        // In the V8 MeasurementTable.onUpgrade code there is a bug but it is never executed
        // as a fresh V8 is installed for early 2019 STAD users and the old upgrade code is never executed.
        db.execSQL("DROP TABLE IF EXISTS measurement");
        db.execSQL(
                "CREATE TABLE measurement(_id INTEGER PRIMARY KEY AUTOINCREMENT, finished INTEGER NOT NULL DEFAULT 1, "
                        + "vehicle TEXT, synced INTEGER NOT NULL DEFAULT 0);");

        // Create GpsPointTable
        db.execSQL("CREATE TABLE gps_points(_id INTEGER PRIMARY KEY AUTOINCREMENT, gps_time INTEGER NOT NULL, "
                + "lat REAL NOT NULL, lon REAL NOT NULL, speed REAL NOT NULL, accuracy INTEGER NOT NULL, "
                + "measurement_fk INTEGER NOT NULL, is_synced INTEGER NOT NULL DEFAULT 0);");
        // Create SamplePointTable
        db.execSQL("CREATE TABLE sample_points(_id INTEGER PRIMARY KEY AUTOINCREMENT, ax REAL NOT NULL, "
                + "ay REAL NOT NULL, az REAL NOT NULL, time INTEGER NOT NULL, measurement_fk INTEGER NOT NULL, "
                + "is_synced INTEGER NOT NULL DEFAULT 0);");
        // Create RotationPointTable
        db.execSQL("CREATE TABLE rotation_points(_id INTEGER PRIMARY KEY AUTOINCREMENT, rx REAL NOT NULL, "
                + "ry REAL NOT NULL, rz REAL NOT NULL, time INTEGER NOT NULL, measurement_fk INTEGER NOT NULL, "
                + "is_synced INTEGER NOT NULL DEFAULT 0);");
        // Create MagneticValuePointTable
        db.execSQL("CREATE TABLE magnetic_value_points(_id INTEGER PRIMARY KEY AUTOINCREMENT, mx REAL NOT NULL, "
                + "my REAL NOT NULL, mz REAL NOT NULL, time INTEGER NOT NULL, measurement_fk INTEGER NOT NULL, "
                + "is_synced INTEGER NOT NULL DEFAULT 0);");

        // # Insert V8 sample data: (sql manually written as we did not use > V6 in our own app)

        // Insert sample android_metadata table entry (exists in SQLite export)
        db.execSQL("INSERT INTO android_metadata (locale) VALUES ('de_DE');");
        // Insert sample MeasurementTable entries - execSQL only supports one insert per commend
        db.execSQL("INSERT INTO measurement (_id,finished,vehicle,synced) VALUES (42,1,'BICYCLE',1);");
        db.execSQL("INSERT INTO measurement (_id,finished,vehicle,synced) VALUES (43,1,'BICYCLE',0);");
        db.execSQL("INSERT INTO measurement (_id,finished,vehicle,synced) VALUES (44,0,'BICYCLE',0);");
        // Insert sample GeoLocationsTable entries - execSQL only supports one insert per commend
        db.execSQL("INSERT INTO gps_points (_id,gps_time,lat,lon,speed,accuracy,measurement_fk,is_synced) VALUES "
                + " (1,1551431421000,51.05,13.72,0.0,1000,42,1);");
        // Sample GeoLocations for which we ensure that the distance is calculated correctly
        db.execSQL("INSERT INTO gps_points (_id,gps_time,lat,lon,speed,accuracy,measurement_fk,is_synced) VALUES "
                + " (11," + location1.getTimestamp() + "," + location1.getLat() + "," + location1.getLon() + ","
                + location1.getSpeed() + "," + location1.getAccuracy() + ",43,1);");
        db.execSQL("INSERT INTO gps_points (_id,gps_time,lat,lon,speed,accuracy,measurement_fk,is_synced) VALUES "
                + " (12," + location2.getTimestamp() + "," + location2.getLat() + "," + location2.getLon() + ","
                + location2.getSpeed() + "," + location2.getAccuracy() + ",43,0);");
        db.execSQL("INSERT INTO gps_points (_id,gps_time,lat,lon,speed,accuracy,measurement_fk,is_synced) VALUES "
                + " (13," + location3.getTimestamp() + "," + location3.getLat() + "," + location3.getLon() + ","
                + location3.getSpeed() + "," + location3.getAccuracy() + ",43,0);");
        // Another sample geoLocation
        db.execSQL("INSERT INTO gps_points (_id,gps_time,lat,lon,speed,accuracy,measurement_fk,is_synced) VALUES "
                + " (20,1551431441000,51.05,13.728,0.0,1200,44,0);");
        // Insert sample SamplePointTable entries - execSQL only supports one insert per commend
        db.execSQL("INSERT INTO sample_points (_id,ax,ay,az,time,measurement_fk,is_synced) VALUES "
                + " (101,0.123,0.234,-0.321,1552917550000,43,0);");
        // Insert sample RotationPointTable entries - execSQL only supports one insert per commend
        db.execSQL("INSERT INTO rotation_points (_id,rx,ry,rz,time,measurement_fk,is_synced) VALUES "
                + " (101,0.123,0.234,-0.321,1552917550000,43,0);");
        // Insert sample MagneticValuePointTable entries - execSQL only supports one insert per commend
        db.execSQL("INSERT INTO magnetic_value_points (_id,mx,my,mz,time,measurement_fk,is_synced) VALUES "
                + " (101,0.123,0.234,-0.321,1552917550000,43,0);");

        // Make sure the relevant data exists
        Cursor cursor = null;
        try {
            // Measurements must still exist after the upgrade
            cursor = db.query("measurement", null, null, null, null, null, null, null);
            assertThat(cursor.getCount(), is(equalTo(3)));
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
}
