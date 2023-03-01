/*
 * Copyright 2019-2023 Cyface GmbH
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
package de.cyface.persistence.content

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteQueryBuilder
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQueryBuilder
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.cyface.persistence.Database
import de.cyface.persistence.DatabaseMigrator
import de.cyface.persistence.model.ParcelableGeoLocation
import de.cyface.testutils.SharedTestUtils
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * This class tests the migration functionality of the [de.cyface.persistence.DatabaseMigrator].
 *
 * To create the database sample data SQL you can capture data with the app, export the SQLite database,
 * open it with *DB Browser for SQLite* and use File > Export > Database to SQL file.
 *
 * *Attention:*
 * It's important that the SQLite statements below only contains hardcoded Strings as the table and column names
 * should be the same as they were in that version to really test the migration as it would happen in real.
 *
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 4.0.0
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigratorTest {
    @Suppress("PrivatePropertyName")
    private val TEST_DB_NAME = "measures"

    /**
     * Creates the database & schema, opens & closes the database and runs the migrations.
     */
    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        Database::class.java,
        emptyList(), // No auto-migrations are used, yet
        FrameworkSQLiteOpenHelperFactory()
    )

    private var migrator: DatabaseMigrator? = null

    private var allMigrations: Array<Migration>? = null

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        migrator = DatabaseMigrator(context)
        allMigrations = arrayOf(
            DatabaseMigrator.MIGRATION_8_9,
            migrator!!.MIGRATION_9_10,
            DatabaseMigrator.MIGRATION_10_11,
            DatabaseMigrator.MIGRATION_11_12,
            DatabaseMigrator.MIGRATION_12_13,
            DatabaseMigrator.MIGRATION_13_14,
            DatabaseMigrator.MIGRATION_14_15,
            DatabaseMigrator.MIGRATION_15_16,
            DatabaseMigrator.MIGRATION_16_17,
            migrator!!.MIGRATION_17_18
        )
    }

    @Test
    fun testMigrationV17ToV18() {
        // FIXME: add test with and without `v6` database (possible when people upgrade e.g. 7.3 to 7.5)
        // and a case where only some newer measurements are as a copy in `v6`
        // ensure the accuracy is ported from cm to m and set to null when it was 0
    }

    /**
     * Test upgrading the [de.cyface.persistence.content.LocationTable] to Database V17.
     *
     * Ensure the `accuracy` is converted to meters.
     */
    @Test
    fun testMigrationV16ToV17() {
        // Arrange
        @Suppress("VARIABLE_WITH_REDUNDANT_INITIALIZER")
        // This is simpler than copying and adjusting the code from previous versions
        var db = helper.createDatabase(TEST_DB_NAME, 15).apply {
            this.execSQL("INSERT INTO identifiers (_id,device_id) VALUES (1,'61e112e1-548e-4a90-be28-9d5b31d6875b')")
            addDatabaseV15Measurement(this, 43L, 2)
            addDatabaseV15Measurement(this, 45L, 0)
            close() // Prepare for the next version
        }

        // Act
        // Re-open the database with target version and provide migrations
        // MigrationTestHelper automatically verifies the schema changes, but not the data validity
        db = helper.runMigrationsAndValidate(
            TEST_DB_NAME,
            16,
            true,
            DatabaseMigrator.MIGRATION_15_16
        )
        // Ensure the accuracy is in cm before upgrading to version 17
        db.query(
            SupportSQLiteQueryBuilder.builder("locations")
                .selection("measurement_fk = ?", arrayOf("43")).create()
        ).use { cursor ->
            MatcherAssert.assertThat(cursor.count, CoreMatchers.equalTo(2))
            cursor.moveToNext()
            MatcherAssert.assertThat(
                cursor.getDouble(cursor.getColumnIndexOrThrow("accuracy")),
                CoreMatchers.equalTo(100.0)
            )
            cursor.moveToNext()
            MatcherAssert.assertThat(
                cursor.getDouble(cursor.getColumnIndexOrThrow("accuracy")),
                CoreMatchers.equalTo(200.0)
            )
        }
        db = helper.runMigrationsAndValidate(
            TEST_DB_NAME,
            17,
            true,
            DatabaseMigrator.MIGRATION_16_17
        )

        // Assert
        db.query(
            SupportSQLiteQueryBuilder.builder("locations")
                .selection("measurement_fk = ?", arrayOf("43")).create()
        ).use { cursor ->
            MatcherAssert.assertThat(cursor.count, CoreMatchers.equalTo(2))
            cursor.moveToNext()
            MatcherAssert.assertThat(
                cursor.getDouble(cursor.getColumnIndexOrThrow("accuracy")),
                CoreMatchers.equalTo(1.0)
            )
            cursor.moveToNext()
            MatcherAssert.assertThat(
                cursor.getDouble(cursor.getColumnIndexOrThrow("accuracy")),
                CoreMatchers.equalTo(2.0)
            )
        }
    }

    /**
     * Test upgrading the [MeasurementTable] to Database V16.
     *
     * We test that the there are now Exceptions when renaming the former `vehicle` column to `modality`.
     */
    @Test
    fun testMigrationV15ToV16() {
        // Arrange
        @Suppress("VARIABLE_WITH_REDUNDANT_INITIALIZER")
        // This is simpler than copying and adjusting the code from previous versions
        var db = helper.createDatabase(TEST_DB_NAME, 15).apply {
            this.execSQL("INSERT INTO identifiers (_id,device_id) VALUES (1,'61e112e1-548e-4a90-be28-9d5b31d6875b')")
            addDatabaseV15Measurement(this, 43L, 1)
            close() // Prepare for the next version
        }

        // Act
        // Re-open the database with target version and provide migrations
        // MigrationTestHelper automatically verifies the schema changes, but not the data validity
        db = helper.runMigrationsAndValidate(
            TEST_DB_NAME,
            16,
            true,
            DatabaseMigrator.MIGRATION_15_16
        )

        // Assert
        db.query(
            SupportSQLiteQueryBuilder.builder("measurements")
                .selection("_id = ?", arrayOf("43"))
                .create()
        ).use { cursor ->
            // Measurements with GeoLocations should have the timestamp of the first GeoLocation as timestamp
            MatcherAssert.assertThat(cursor.count, CoreMatchers.equalTo(1))
            cursor.moveToNext()
            MatcherAssert.assertThat(
                cursor.getLong(cursor.getColumnIndexOrThrow("_id")),
                CoreMatchers.equalTo(43L)
            )
            MatcherAssert.assertThat(
                cursor.getString(cursor.getColumnIndexOrThrow("modality")),
                CoreMatchers.equalTo("BICYCLE")
            )
        }
    }

    /**
     * Test upgrading the [MeasurementTable] to Database V14.
     *
     * We test that the newly added timestamp column is calculated correctly for measurements with and without
     * [ParcelableGeoLocation]s
     */
    @Test
    fun testMigrationV13ToV14() {
        // Arrange
        @Suppress("VARIABLE_WITH_REDUNDANT_INITIALIZER")
        // This is simpler than copying and adjusting the code from previous versions
        var db = helper.createDatabase(TEST_DB_NAME, 11).apply {
            this.execSQL("INSERT INTO identifiers (_id,device_id) VALUES (1,'61e112e1-548e-4a90-be28-9d5b31d6875b')")
            addDatabaseV11Measurement(this, 43L, 2)
            addDatabaseV11Measurement(this, 44L, 0)
            close() // Prepare for the next version
        }

        // Act
        // Re-open the database with target version and provide migrations
        // MigrationTestHelper automatically verifies the schema changes, but not the data validity
        db = helper.runMigrationsAndValidate(
            TEST_DB_NAME,
            14,
            true,
            DatabaseMigrator.MIGRATION_11_12,
            DatabaseMigrator.MIGRATION_12_13,
            DatabaseMigrator.MIGRATION_13_14
        )

        // Assert
        // Ensure timestamp is calculated correctly
        // Make sure the relevant data from before the upgrade still exists
        var cursor: Cursor? = null
        // The unix timestamp in milliseconds which is used to generate the first [ParcelableGeoLocation],
        //for the next GeoLocations' timestamps 1L is added to this number.
        val defaultLocationTimestamp = 1551431485000L
        try {
            // Measurements with GeoLocations should have the timestamp of the first GeoLocation as timestamp
            cursor = db.query(
                SupportSQLiteQueryBuilder.builder("measurements")
                    .selection("_id = ?", arrayOf("43")).create()
            )
            MatcherAssert.assertThat(cursor.count, CoreMatchers.equalTo(1))
            cursor.moveToNext()
            MatcherAssert.assertThat(
                cursor.getLong(cursor.getColumnIndexOrThrow("_id")),
                CoreMatchers.equalTo(43L)
            )
            MatcherAssert.assertThat(
                cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                CoreMatchers.equalTo(defaultLocationTimestamp)
            )

            // Measurement without GeoLocations should have 0L als timestamp
            cursor = db.query(
                SupportSQLiteQueryBuilder.builder("measurements")
                    .selection("_id = ?", arrayOf("44")).create()
            )
            MatcherAssert.assertThat(cursor.count, CoreMatchers.equalTo(1))
            cursor.moveToNext()
            MatcherAssert.assertThat(
                cursor.getLong(cursor.getColumnIndexOrThrow("_id")),
                CoreMatchers.equalTo(44L)
            )
            MatcherAssert.assertThat(
                cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                CoreMatchers.equalTo(0L)
            )
        } finally {
            cursor?.close()
        }
    }

    /**
     * Test that upgrading the `measurements` table to Database V13 does not loose entries.
     */
    @Test
    fun testMigrationV12ToV13() {
        // Arrange
        @Suppress("VARIABLE_WITH_REDUNDANT_INITIALIZER")
        var db = helper.createDatabase(TEST_DB_NAME, 12).apply {
            insertV12TestData(this)
            close() // Prepare for the next version
        }

        // Act
        // Re-open the database with target version and provide migrations
        // MigrationTestHelper automatically verifies the schema changes, but not the data validity
        db = helper.runMigrationsAndValidate(
            TEST_DB_NAME,
            13,
            true,
            DatabaseMigrator.MIGRATION_12_13
        )

        // Assert
        db.query(SupportSQLiteQueryBuilder.builder("measurements").create()).use { cursor ->
            // Measurements must still exist after the upgrade
            MatcherAssert.assertThat(cursor.count, CoreMatchers.equalTo(3))
            cursor.moveToNext()
            MatcherAssert.assertThat(
                cursor.getLong(cursor.getColumnIndexOrThrow("_id")),
                CoreMatchers.equalTo(42L)
            )
            MatcherAssert.assertThat(
                cursor.getString(cursor.getColumnIndexOrThrow("status")),
                CoreMatchers.equalTo("SYNCED")
            )
            MatcherAssert.assertThat(
                cursor.getString(cursor.getColumnIndexOrThrow("vehicle")),
                CoreMatchers.equalTo("BICYCLE")
            )
            MatcherAssert.assertThat(
                cursor.getDouble(cursor.getColumnIndexOrThrow("distance")),
                CoreMatchers.equalTo(0.0)
            )
            MatcherAssert.assertThat(
                cursor.getInt(cursor.getColumnIndexOrThrow("file_format_version")),
                CoreMatchers.equalTo(1)
            )
        }
    }

    /**
     * Test that loading the [EventTable] which was introduced in the Database V12 Upgrade works.
     *
     * This database upgrade V12 was part of (STAD-6)!
     */
    @Test
    fun testMigrationV11ToV12() {
        // Arrange
        @Suppress("VARIABLE_WITH_REDUNDANT_INITIALIZER")
        var db = helper.createDatabase(TEST_DB_NAME, 11).apply {
            // Insert test data in the version 1 schema
            addDatabaseV11Measurement(this, 43L, 1)

            // Prepare for the next version.
            close()
        }

        // Act
        // Re-open the database with target version and provide migrations
        // MigrationTestHelper automatically verifies the schema changes, but not the data validity
        db = helper.runMigrationsAndValidate(
            TEST_DB_NAME,
            12,
            true,
            DatabaseMigrator.MIGRATION_11_12
        )

        // Assert
        // Loading from the newly added table must work (STAD-85)
        db!!.execSQL("SELECT * FROM events;")
    }

    /**
     * Test that loading the EventTable which was introduced in the Database V12 Upgrade works
     * after upgrading from the SDK 2.X which was included in the first official STAD release (#776b323540).
     *
     * The database upgrade V12 was part of (STAD-6)!
     */
    @Test
    fun testMigrationV8ToV12() {
        // Arrange
        // The distance between the [.location1] and [.location2].
        val expectedDistance1 = 2
        // The distance between the [.location2] and [.location3].
        val expectedDistance2 = 3

        @Suppress("VARIABLE_WITH_REDUNDANT_INITIALIZER")
        var db = helper.createDatabase(TEST_DB_NAME, 8).apply {
            // Insert test data in the version 8 schema
            insertV8TestData(
                this,
                expectedDistance1,
                expectedDistance2
            )

            // Prepare for the next version.
            close()
        }
        val expectedLocation123Distance = (expectedDistance1 + expectedDistance2).toDouble()

        // Act
        // Re-open the database with target version and provide migrations
        // MigrationTestHelper automatically verifies the schema changes, but not the data validity
        db = helper.runMigrationsAndValidate(
            TEST_DB_NAME,
            12,
            true,
            DatabaseMigrator.MIGRATION_8_9,
            migrator!!.MIGRATION_9_10,
            DatabaseMigrator.MIGRATION_10_11,
            DatabaseMigrator.MIGRATION_11_12
        )

        // Assert
        // Loading from the newly added table must work (STAD-85)
        db!!.execSQL("SELECT * FROM events;")

        // Make sure the relevant data from before the upgrade still exists
        var cursor: Cursor? = null
        try {
            // Measurements must still exist after the upgrade
            cursor = db.query(SupportSQLiteQueryBuilder.builder("measurements").create())
            MatcherAssert.assertThat(cursor.count, CoreMatchers.equalTo(3))
            cursor = db.query(
                SupportSQLiteQueryBuilder
                    .builder("measurements")
                    .selection(
                        "status = ? AND distance = ? AND file_format_version = ?",
                        arrayOf("SYNCED", "0.0", "1")
                    )
                    .create()
            )
            MatcherAssert.assertThat(cursor.count, CoreMatchers.equalTo(1))
            val builder2 = SQLiteQueryBuilder()
            builder2.tables = "measurements"
            cursor = db.query(
                SupportSQLiteQueryBuilder
                    .builder("measurements")
                    .selection(
                        "status = ? AND distance = ? AND file_format_version = ?",
                        arrayOf("OPEN", "0.0", "1")
                    )
                    .create()
            )
            MatcherAssert.assertThat(cursor.count, CoreMatchers.equalTo(1))

            // Here we also check that the distance is calculated as expected
            cursor = db.query(
                SupportSQLiteQueryBuilder
                    .builder("measurements")
                    .columns(arrayOf("distance"))
                    .selection("status = ? AND file_format_version = ?", arrayOf("FINISHED", "1"))
                    .create()
            )
            MatcherAssert.assertThat(cursor.count, CoreMatchers.equalTo(1))
            cursor.moveToNext()
            val distanceColumnIndex = cursor.getColumnIndexOrThrow("distance")
            val loadedDistance = cursor.getDouble(distanceColumnIndex)
            MatcherAssert.assertThat(
                loadedDistance,
                Matchers.closeTo(expectedLocation123Distance, 0.1)
            )

            // GeoLocations must still exist after the upgrade
            cursor = db.query(SupportSQLiteQueryBuilder.builder("locations").create())
            MatcherAssert.assertThat(cursor.count, CoreMatchers.equalTo(5))
        } finally {
            cursor?.close()
        }
    }

    /**
     * The SDK only contains migrations starting at version 8 which was introduced when we switched
     * to the binary format for sensor data in 2019. This has been the case since then, so there
     * should not be any installations with older databases out there, but if so, this test ensures
     * that such cases crash hard, so we see this and can add handling for such cases.
     */
    @Test(expected = IllegalStateException::class)
    fun testMigrationFromLowerThenV8Fails() {

        // Create earliest version of the database, which is not supported anymore
        helper.createDatabase(TEST_DB_NAME, 1).apply {
            close()
        }

        // Open latest database version. Room validates the schema once all migrations execute
        allMigrations?.let {
            Room.databaseBuilder(
                InstrumentationRegistry.getInstrumentation().targetContext,
                Database::class.java,
                TEST_DB_NAME
            ).addMigrations(*it).build().apply {
                openHelper.writableDatabase.close()
            }
        }
    }

    /**
     * Tests that all migrations work in sequence.
     */
    @Test
    fun testMigrateAll() {
        // Create earliest version of the database for which we have a valid Room schema
        helper.createDatabase(TEST_DB_NAME, 18).apply {
            close()
        }

        // Open latest version of the database. Room validates the schema once all migrations execute.
        allMigrations?.let {
            Room.databaseBuilder(
                InstrumentationRegistry.getInstrumentation().targetContext,
                Database::class.java,
                TEST_DB_NAME
            ).addMigrations(*it).build().apply {
                openHelper.writableDatabase.close()
            }
        }
    }

    /**
     * Adds a `measurements` entry with [locations] [ParcelableGeoLocation]s to a test database.
     *
     * @param db A clean [SQLiteDatabase] to use for testing.
     * @param measurementId the id of the measurement to generate
     * @param locations number of locations to generate for the measurement to be generated
     */
    private fun addDatabaseV15Measurement(
        db: SupportSQLiteDatabase,
        measurementId: Long,
        locations: Long
    ) {
        // # Insert V15 sample data: (exported from our V12 app and manually adjusted to V15)

        // Insert sample MeasurementTable entries - execSQL only supports one insert per commend
        db.execSQL(
            ("INSERT INTO measurements (_id,status,vehicle,file_format_version,distance,timestamp) VALUES "
                    + " (" + measurementId + ",'FINISHED','BICYCLE',1,5396.62473698979,1551431485000);")
        )
        // Insert sample GeoLocationsTable entries - execSQL only supports one insert per commend
        for (i in 0 until locations) {
            db.execSQL(
                ("INSERT INTO locations (_id,gps_time,lat,lon,speed,accuracy,measurement_fk) VALUES "
                        + " (" + (measurementId * 1000000 + i) + "," + (1551431485000L + i)
                        + ",51.05210394,13.72873203,0.0," + ((i + 1) * 100) + "," + measurementId
                        + ");")
            )
        }
    }

    /**
     * Adds a measurement with [locations] locations to a test database of in version 11.
     *
     * @param db A clean database to use for testing.
     * @param measurementId the id of the measurement to generate
     * @param locations number of locations to generate for the measurement to be generated
     */
    private fun addDatabaseV11Measurement(
        db: SupportSQLiteDatabase,
        @Suppress("SameParameterValue") measurementId: Long,
        @Suppress("SameParameterValue") locations: Long
    ) {
        // # Insert V11 sample data: (exported from our V12 app and manually adjusted to V11)

        // Insert sample MeasurementTable entries - execSQL only supports one insert per commend
        db.execSQL(
            ("INSERT INTO measurements (_id,status,vehicle,accelerations,rotations,directions,file_format_version,distance) VALUES "
                    + " (" + measurementId + ",'FINISHED','BICYCLE',690481,690336,166370,1,5396.62473698979);")
        )
        // Insert sample GeoLocationsTable entries - execSQL only supports one insert per commend
        for (i in 0 until locations) {
            db.execSQL(
                ("INSERT INTO locations (_id,gps_time,lat,lon,speed,accuracy,measurement_fk) VALUES "
                        + " (" + (1 + i) + "," + (1551431485000L + i) + ",51.05210394,13.72873203,0.0,1179," + measurementId
                        + ");")
            )
        }
    }

    private fun insertV12TestData(
        db: SupportSQLiteDatabase
    ) {
        // # Insert V12 sample data:
        // Insert identifier entry
        db.execSQL("INSERT INTO identifiers (_id,device_id) VALUES (1,'61e112e1-548e-4a90-be28-9d5b31d6875b')")
        // Exported from the end of the migrate8To12 test
        db.execSQL("INSERT INTO \"measurements\" (\"_id\",\"status\",\"vehicle\",\"accelerations\",\"rotations\",\"directions\",\"file_format_version\",\"distance\") VALUES (42,'SYNCED','BICYCLE',0,0,0,1,0.0)")
        db.execSQL("INSERT INTO \"measurements\" (\"_id\",\"status\",\"vehicle\",\"accelerations\",\"rotations\",\"directions\",\"file_format_version\",\"distance\") VALUES (43,'FINISHED','BICYCLE',0,0,0,1,5.09262943267822)")
        db.execSQL("INSERT INTO \"measurements\" (\"_id\",\"status\",\"vehicle\",\"accelerations\",\"rotations\",\"directions\",\"file_format_version\",\"distance\") VALUES (44,'OPEN','BICYCLE',0,0,0,1,0.0)")
        db.execSQL("INSERT INTO \"locations\" (\"_id\",\"gps_time\",\"lat\",\"lon\",\"speed\",\"accuracy\",\"measurement_fk\") VALUES (1,1551431421000,51.05,13.72,0.0,1000,42)")
        db.execSQL("INSERT INTO \"locations\" (\"_id\",\"gps_time\",\"lat\",\"lon\",\"speed\",\"accuracy\",\"measurement_fk\") VALUES (11,1000000000,51.1,13.1,38.015017922507,7.22285340527633,43)")
        db.execSQL("INSERT INTO \"locations\" (\"_id\",\"gps_time\",\"lat\",\"lon\",\"speed\",\"accuracy\",\"measurement_fk\") VALUES (12,1000000000,51.1000179864,13.1000000541394,17.7258170071012,3.36790523134923,43)")
        db.execSQL("INSERT INTO \"locations\" (\"_id\",\"gps_time\",\"lat\",\"lon\",\"speed\",\"accuracy\",\"measurement_fk\") VALUES (13,1000000000,51.100044966,13.1000001353485,71.273771897256,13.5420166604786,43)")
        db.execSQL("INSERT INTO \"locations\" (\"_id\",\"gps_time\",\"lat\",\"lon\",\"speed\",\"accuracy\",\"measurement_fk\") VALUES (20,1551431441000,51.05,13.728,0.0,1200,44)")
    }

    private fun insertV8TestData(
        db: SupportSQLiteDatabase,
        @Suppress("SameParameterValue") expectedDistance1: Int,
        @Suppress("SameParameterValue") expectedDistance2: Int
    ) {
        // The "base" from where the distance of generated nodes should be calculated, see
        // [SharedTestUtils.generateGeoLocation]. This is usually 0.
        val base = 0
        // The 1st test location of a test measurement.
        val location1 = SharedTestUtils.generateGeoLocation(base)
        // The 2nd test location of a test measurement.
        val location2 = SharedTestUtils.generateGeoLocation(expectedDistance1)
        // The 3rd test location of a test measurement.
        val location3 =
            SharedTestUtils.generateGeoLocation(expectedDistance1 + expectedDistance2)

        // # Insert V8 sample data: (sql manually written as we did not use > V6 in our own app)

        // Insert sample MeasurementTable entries - execSQL only supports one insert per commend
        db.execSQL("INSERT INTO measurement (_id,finished,vehicle,synced) VALUES (42,1,'BICYCLE',1);")
        db.execSQL("INSERT INTO measurement (_id,finished,vehicle,synced) VALUES (43,1,'BICYCLE',0);")
        db.execSQL("INSERT INTO measurement (_id,finished,vehicle,synced) VALUES (44,0,'BICYCLE',0);")
        // Insert sample GeoLocationsTable entries - execSQL only supports one insert per commend
        db.execSQL(
            ("INSERT INTO gps_points (_id,gps_time,lat,lon,speed,accuracy,measurement_fk,is_synced) VALUES "
                    + " (1,1551431421000,51.05,13.72,0.0,1000,42,1);")
        )
        // Sample GeoLocations for which we ensure that the distance is calculated correctly
        db.execSQL(
            ("INSERT INTO gps_points (_id,gps_time,lat,lon,speed,accuracy,measurement_fk,is_synced) VALUES "
                    + " (11," + location1.timestamp + "," + location1.lat + "," + location1.lon + ","
                    + location1.speed + "," + location1.accuracy + ",43,1);")
        )
        db.execSQL(
            ("INSERT INTO gps_points (_id,gps_time,lat,lon,speed,accuracy,measurement_fk,is_synced) VALUES "
                    + " (12," + location2.timestamp + "," + location2.lat + "," + location2.lon + ","
                    + location2.speed + "," + location2.accuracy + ",43,0);")
        )
        db.execSQL(
            ("INSERT INTO gps_points (_id,gps_time,lat,lon,speed,accuracy,measurement_fk,is_synced) VALUES "
                    + " (13," + location3.timestamp + "," + location3.lat + "," + location3.lon + ","
                    + location3.speed + "," + location3.accuracy + ",43,0);")
        )
        // Another sample geoLocation
        db.execSQL(
            ("INSERT INTO gps_points (_id,gps_time,lat,lon,speed,accuracy,measurement_fk,is_synced) VALUES "
                    + " (20,1551431441000,51.05,13.728,0.0,1200,44,0);")
        )
        // Insert sample SamplePointTable entries - execSQL only supports one insert per commend
        db.execSQL(
            ("INSERT INTO sample_points (_id,ax,ay,az,time,measurement_fk,is_synced) VALUES "
                    + " (101,0.123,0.234,-0.321,1552917550000,43,0);")
        )
        // Insert sample RotationPointTable entries - execSQL only supports one insert per commend
        db.execSQL(
            ("INSERT INTO rotation_points (_id,rx,ry,rz,time,measurement_fk,is_synced) VALUES "
                    + " (101,0.123,0.234,-0.321,1552917550000,43,0);")
        )
        // Insert sample MagneticValuePointTable entries - execSQL only supports one insert per commend
        db.execSQL(
            ("INSERT INTO magnetic_value_points (_id,mx,my,mz,time,measurement_fk,is_synced) VALUES "
                    + " (101,0.123,0.234,-0.321,1552917550000,43,0);")
        )

        // Make sure the relevant data exists
        db.query(SupportSQLiteQueryBuilder.builder("measurement").create()).use { cursor ->
            // Measurements must still exist after the upgrade
            MatcherAssert.assertThat(cursor.count, CoreMatchers.equalTo(3))
        }
    }
}