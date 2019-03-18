package de.cyface.persistence;

import static de.cyface.persistence.TestUtils.AUTHORITY;
import static de.cyface.persistence.Utils.getEventUri;
import static de.cyface.persistence.Utils.getGeoLocationsUri;
import static de.cyface.persistence.Utils.getIdentifierUri;
import static de.cyface.persistence.Utils.getMeasurementUri;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQuery;

import androidx.test.core.app.ApplicationProvider;

/**
 * This class tests the migration functionality of {@link DatabaseHelper}.
 * <p>
 * To create database the sample data SQL you can capture data with the app, export the SQLite database,
 * open it with *DB Browser for SQLite* and use File > Export > Database to SQL file.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 4.0.0
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE) // Or do we need one?
// @Config(constants = BuildConfig.class, sdk = DefaultConfig.EMULATE_SDK)
public class DatabaseHelperTest {

    /**
     * We require Mockito to avoid calling Android system functions. This rule is responsible for the initialization of
     * the Spies and Mocks.
     */
    // @Rule
    // public MockitoRule mockitoRule = MockitoJUnit.rule();
    private SQLiteDatabase db;
    /**
     * The object of the class under test
     */
    private DatabaseHelper oocut;
    private ContentResolver resolver;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        resolver = context.getContentResolver();
        oocut = new DatabaseHelper(context);

        // Clearing database just in case
        resolver.delete(getIdentifierUri(AUTHORITY), null, null);
        resolver.delete(getGeoLocationsUri(AUTHORITY), null, null);
        resolver.delete(getMeasurementUri(AUTHORITY), null, null);
        resolver.delete(getEventUri(AUTHORITY), null, null);

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
        resolver.delete(getGeoLocationsUri(AUTHORITY), null, null);
        resolver.delete(getMeasurementUri(AUTHORITY), null, null);
        resolver.delete(getEventUri(AUTHORITY), null, null);
        resolver.delete(getIdentifierUri(AUTHORITY), null, null);
        db.close();
    }

    /**
     * Test that changing a single column value for a geo location works as expected.
     */
    @Test
    public void testMigrationV11ToV12() {

        // Arrange
        createV11DatabaseWithData(db);

        // Act
        oocut.onUpgrade(db, 11, 12);

        // Assert - currently only checking that there is not exception
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

        // Create V11 Tables:
        // Create android_metadata table (exists in SQLite export)
        db.execSQL("DROP TABLE IF EXISTS `android_metadata`");
        db.execSQL("CREATE TABLE android_metadata (locale TEXT);");
        // Create IdentifierTable
        db.execSQL("CREATE TABLE identifiers (_id INTEGER PRIMARY KEY AUTOINCREMENT, device_id TEXT NOT NULL);");
        // Create MeasurementTable
        db.execSQL("CREATE TABLE measurements (_id INTEGER PRIMARY KEY AUTOINCREMENT, status TEXT NOT NULL, "
                + "vehicle TEXT NOT NULL, accelerations INTEGER NOT NULL, rotations INTEGER NOT NULL, "
                + "directions INTEGER NOT NULL, file_format_version SHORT INTEGER NOT NULL, distance REAL NOT NULL);");
        // Create GeoLocationsTable
        db.execSQL("CREATE TABLE locations (_id INTEGER PRIMARY KEY AUTOINCREMENT, gps_time INTEGER NOT NULL, "
                + "lat REAL NOT NULL, lon REAL NOT NULL, speed REAL NOT NULL, accuracy INTEGER NOT NULL, "
                + "measurement_fk INTEGER NOT NULL);");

        // Insert V11 sample data:
        // Insert sample android_metadata table entry (exists in SQLite export)
        db.execSQL("INSERT INTO android_metadata (locale) VALUES ('de_DE');");
        // Insert sample IdentifierTable entry
        db.execSQL("INSERT INTO identifiers (_id,device_id) VALUES (1,'61e112e1-548e-4a90-be28-9d5b31d6875b');");
        // Insert sample MeasurementTable entries - execSQL only supports one insert per commend
        db.execSQL(
                "INSERT INTO `measurements` (_id,status,vehicle,accelerations,rotations,directions,file_format_version,distance) VALUES "
                        + " (43,'FINISHED','BICYCLE',690481,690336,166370,1,5396.62473698979);");
        // Insert sample GeoLocationsTable entries - execSQL only supports one insert per commend
        db.execSQL("INSERT INTO locations (_id,gps_time,lat,lon,speed,accuracy,measurement_fk) VALUES "
                + " (3,1551431485000,51.05210394,13.72873203,0.0,1179,43);");
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
}
