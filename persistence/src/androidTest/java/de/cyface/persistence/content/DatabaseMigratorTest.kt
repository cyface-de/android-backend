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
import android.content.Context.MODE_PRIVATE
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteQueryBuilder
import androidx.core.database.getDoubleOrNull
import androidx.core.database.getStringOrNull
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
import de.cyface.utils.Validate
import de.cyface.utils.ValidationException
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

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

    private var context: Context? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        migrator = DatabaseMigrator(context!!)
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

    /**
     * This test ensures that the migration code fails in case the secondary database `v6` is not still
     * in version `1` at the time the migration code is executed.
     */
    @Test(expected = ValidationException::class)
    fun testMigrationV17ToV18_withUnsupportedV6DatabaseVersion_fails() {
        // Arrange
        // Generate first bytes of a database file which contains a version > 1 at byte 60...64
        val dbVersion = 2
        val dbV6File = context!!.getDatabasePath("v6")
        val versionBytes = ByteBuffer.allocate(4).putInt(dbVersion).array()
        dbV6File.writeBytes(ByteArray(60) + versionBytes)
        try {
            // Create main database
            helper.createDatabase(TEST_DB_NAME, 17)

            // Act
            // Re-open the database with target version and provide migrations
            // MigrationTestHelper automatically verifies the schema changes, but not the data validity
            helper.runMigrationsAndValidate(
                TEST_DB_NAME,
                18,
                true,
                migrator!!.MIGRATION_17_18
            )
        } finally {
            dbV6File.delete()
        }
    }

    /**
     * Tests the migration when a user has installed SDK 6.3 or SDK 7.4 and upgrades to SDK 7.5.
     *
     * In this case a secondary database `v6` version `1` was created next to `measures` db.
     * - locations of measurements captured in < 6.3 and < 7.4 only exist in `measures`
     * - locations of measurements captured in 6.3 and 7.4 exists in both,`v6` and `measures`
     *   but in `v6` the locations are annotated with the GNSS altitude and vertical accuracy.
     * I.e. we want to import the `v6` locations when they exist, and else the `measures` locations.
     */
    @Test
    fun testMigrationV17ToV18_withV6DatabaseMerge() {
        // Arrange
        val v6DatabaseName = "v6"
        val v6Db = createV6Database(context!!, v6DatabaseName, 1)
        try {
            createV6Tables(v6Db)
            // Only insert data for the second measurement `id=44` into `v6` database
            addV6LocationsAndPressures(v6Db, 44L, 1, 1, 0.0)
            // Database `v6` still contained `accuracy` in cm but as `Double`
            addV6LocationsAndPressures(v6Db, 45L, 1, 1, 500.0)
            v6Db.close()
            // Create main database
            @Suppress("VARIABLE_WITH_REDUNDANT_INITIALIZER")
            var db = helper.createDatabase(TEST_DB_NAME, 17).apply {
                this.execSQL("INSERT INTO identifiers (_id,device_id) VALUES (1,'61e112e1-548e-4a90-be28-9d5b31d6875b')")
                // The measurement `43L` has no `v6` data as it was "captured with SDK < 6.3/7.4"
                addDatabaseV17Measurement(this, 43L, 1, 5.0)
                // `measures.locations.accuracy = 0.0` is expected to be set to `null`
                addDatabaseV17Measurement(this, 44L, 1, 0.0)
                addDatabaseV17Measurement(this, 45L, 1, 5.0)
                close() // Prepare for the next version
            }

            // Act
            // Re-open the database with target version and provide migrations
            // MigrationTestHelper automatically verifies the schema changes, but not the data validity
            db = helper.runMigrationsAndValidate(
                TEST_DB_NAME,
                18,
                true,
                migrator!!.MIGRATION_17_18
            )

            // Assert
            checkV18IdentifierTable(db)
            checkV18MeasurementTable(db, 3)
            checkV18EventTable(db)
            // Location table
            // Ensure measurement 43 contains location where `altitude` and `verticalAccuracy` is `null`
            db.query(
                SupportSQLiteQueryBuilder.builder("Location")
                    .selection("measurementId = ?", arrayOf("43"))
                    .create()
            ).use { cursor ->
                MatcherAssert.assertThat(cursor.count, CoreMatchers.equalTo(1))
                cursor.moveToNext()
                MatcherAssert.assertThat(
                    cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                    CoreMatchers.equalTo(1551431485000L)
                )
                MatcherAssert.assertThat(
                    cursor.getDouble(cursor.getColumnIndexOrThrow("lat")),
                    CoreMatchers.equalTo(51.05210394)
                )
                MatcherAssert.assertThat(
                    cursor.getDouble(cursor.getColumnIndexOrThrow("lon")),
                    CoreMatchers.equalTo(13.72873203)
                )
                MatcherAssert.assertThat(
                    cursor.getDoubleOrNull(cursor.getColumnIndexOrThrow("altitude")),
                    CoreMatchers.equalTo(null)
                )
                MatcherAssert.assertThat(
                    cursor.getDouble(cursor.getColumnIndexOrThrow("speed")),
                    CoreMatchers.equalTo(1.01)
                )
                MatcherAssert.assertThat(
                    cursor.getDoubleOrNull(cursor.getColumnIndexOrThrow("accuracy")),
                    CoreMatchers.equalTo(5.0)
                )
                MatcherAssert.assertThat(
                    cursor.getDoubleOrNull(cursor.getColumnIndexOrThrow("verticalAccuracy")),
                    CoreMatchers.equalTo(null)
                )
                MatcherAssert.assertThat(
                    cursor.getLong(cursor.getColumnIndexOrThrow("measurementId")),
                    CoreMatchers.equalTo(43L)
                )
            }
            // Ensure measurement 44 contains locations with altitude from `v6` and that `accuracy` is `null`
            db.query(
                SupportSQLiteQueryBuilder.builder("Location")
                    .selection("measurementId = ?", arrayOf("44"))
                    .create()
            ).use { cursor ->
                MatcherAssert.assertThat(cursor.count, CoreMatchers.equalTo(1))
                cursor.moveToNext()
                MatcherAssert.assertThat(
                    cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                    CoreMatchers.equalTo(1551431485000L)
                )
                MatcherAssert.assertThat(
                    cursor.getDouble(cursor.getColumnIndexOrThrow("lat")),
                    CoreMatchers.equalTo(51.05210394)
                )
                MatcherAssert.assertThat(
                    cursor.getDouble(cursor.getColumnIndexOrThrow("lon")),
                    CoreMatchers.equalTo(13.72873203)
                )
                MatcherAssert.assertThat(
                    cursor.getDoubleOrNull(cursor.getColumnIndexOrThrow("altitude")),
                    CoreMatchers.equalTo(400.0)
                )
                MatcherAssert.assertThat(
                    cursor.getDouble(cursor.getColumnIndexOrThrow("speed")),
                    CoreMatchers.equalTo(1.01)
                )
                MatcherAssert.assertThat(
                    cursor.getDoubleOrNull(cursor.getColumnIndexOrThrow("accuracy")),
                    CoreMatchers.equalTo(null)
                )
                MatcherAssert.assertThat(
                    cursor.getDoubleOrNull(cursor.getColumnIndexOrThrow("verticalAccuracy")),
                    CoreMatchers.equalTo(20.0)
                )
                MatcherAssert.assertThat(
                    cursor.getLong(cursor.getColumnIndexOrThrow("measurementId")),
                    CoreMatchers.equalTo(44L)
                )
            }
            // Ensure measurement 45 contains locations with altitude from `v6` and the accuracy in meters
            db.query(
                SupportSQLiteQueryBuilder.builder("Location")
                    .selection("measurementId = ?", arrayOf("45"))
                    .create()
            ).use { cursor ->
                MatcherAssert.assertThat(cursor.count, CoreMatchers.equalTo(1))
                cursor.moveToNext()
                MatcherAssert.assertThat(
                    cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                    CoreMatchers.equalTo(1551431485000L)
                )
                MatcherAssert.assertThat(
                    cursor.getDouble(cursor.getColumnIndexOrThrow("lat")),
                    CoreMatchers.equalTo(51.05210394)
                )
                MatcherAssert.assertThat(
                    cursor.getDouble(cursor.getColumnIndexOrThrow("lon")),
                    CoreMatchers.equalTo(13.72873203)
                )
                MatcherAssert.assertThat(
                    cursor.getDoubleOrNull(cursor.getColumnIndexOrThrow("altitude")),
                    CoreMatchers.equalTo(400.0)
                )
                MatcherAssert.assertThat(
                    cursor.getDouble(cursor.getColumnIndexOrThrow("speed")),
                    CoreMatchers.equalTo(1.01)
                )
                MatcherAssert.assertThat(
                    cursor.getDoubleOrNull(cursor.getColumnIndexOrThrow("accuracy")),
                    CoreMatchers.equalTo(5.0)
                )
                MatcherAssert.assertThat(
                    cursor.getDoubleOrNull(cursor.getColumnIndexOrThrow("verticalAccuracy")),
                    CoreMatchers.equalTo(20.0)
                )
                MatcherAssert.assertThat(
                    cursor.getLong(cursor.getColumnIndexOrThrow("measurementId")),
                    CoreMatchers.equalTo(45L)
                )
            }
            // Pressure table
            // Ensure measurement 43 contains no pressures
            db.query(
                SupportSQLiteQueryBuilder.builder("Pressure")
                    .selection("measurementId = ?", arrayOf("43")).create()
            ).use { cursor ->
                MatcherAssert.assertThat(cursor.count, CoreMatchers.equalTo(0))
            }
            // Ensure measurement 44 contains pressures from `v6´
            db.query(
                SupportSQLiteQueryBuilder.builder("Pressure")
                    .selection("measurementId = ?", arrayOf("44")).create()
            ).use { cursor ->
                MatcherAssert.assertThat(cursor.count, CoreMatchers.equalTo(1))
                cursor.moveToNext()
                MatcherAssert.assertThat(
                    cursor.getDouble(cursor.getColumnIndexOrThrow("pressure")),
                    CoreMatchers.equalTo(1013.25)
                )
                MatcherAssert.assertThat(
                    cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                    CoreMatchers.equalTo(1551431485000L)
                )
                MatcherAssert.assertThat(
                    cursor.getLong(cursor.getColumnIndexOrThrow("measurementId")),
                    CoreMatchers.equalTo(44L)
                )
            }
            // Ensure measurement 45 contains pressures from `v6´
            db.query(
                SupportSQLiteQueryBuilder.builder("Pressure")
                    .selection("measurementId = ?", arrayOf("45")).create()
            ).use { cursor ->
                MatcherAssert.assertThat(cursor.count, CoreMatchers.equalTo(1))
                cursor.moveToNext()
                MatcherAssert.assertThat(
                    cursor.getDouble(cursor.getColumnIndexOrThrow("pressure")),
                    CoreMatchers.equalTo(1013.25)
                )
                MatcherAssert.assertThat(
                    cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                    CoreMatchers.equalTo(1551431485000L)
                )
                MatcherAssert.assertThat(
                    cursor.getLong(cursor.getColumnIndexOrThrow("measurementId")),
                    CoreMatchers.equalTo(45L)
                )
            }
            // `runMigrationsAndValidate()` above ensures old tables `locations` are deleted.
        } finally {
            context!!.deleteDatabase(v6DatabaseName)
        }
    }

    /**
     * Creates the tables which existed in the secondary database `v6` version `1`.
     *
     * @param db The database to create the tables in.
     */
    private fun createV6Tables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `Pressure` (`pressure` REAL NOT NULL, `measurement_fk` INTEGER NOT NULL, `uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `Location` (`lat` REAL NOT NULL, `lon` REAL NOT NULL, `altitude` REAL, `speed` REAL NOT NULL, `accuracy` REAL NOT NULL, `vertical_accuracy` REAL, `measurement_fk` INTEGER NOT NULL, `uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL)")
    }

    /**
     * Tests the migration when a user has installed e.g. SDK 7.3 and upgrades directly to 7.5.
     *
     * In this case no secondary database `v6` was created like in SDK 7.4.
     */
    @Test
    fun testMigrationV17ToV18() {
        // Arrange
        @Suppress("VARIABLE_WITH_REDUNDANT_INITIALIZER")
        var db = helper.createDatabase(TEST_DB_NAME, 17).apply {
            this.execSQL("INSERT INTO identifiers (_id,device_id) VALUES (1,'61e112e1-548e-4a90-be28-9d5b31d6875b')")
            addDatabaseV17Measurement(this, 43L, 1, 5.0)
            addDatabaseV17Measurement(this, 44L, 1, 0.0)
            close() // Prepare for the next version
        }

        // Act
        // Re-open the database with target version and provide migrations
        // MigrationTestHelper automatically verifies the schema changes, but not the data validity
        db = helper.runMigrationsAndValidate(
            TEST_DB_NAME,
            18,
            true,
            migrator!!.MIGRATION_17_18
        )

        // Assert
        checkV18IdentifierTable(db)
        checkV18MeasurementTable(db, 2)
        checkV18EventTable(db)
        // Location table
        // With a normal accuracy. Also ensures altitude and verticalAccuracy is set to null.
        db.query(
            SupportSQLiteQueryBuilder.builder("Location")
                .selection("measurementId = ?", arrayOf("43"))
                .create()
        ).use { cursor ->
            MatcherAssert.assertThat(cursor.count, CoreMatchers.equalTo(1))
            cursor.moveToNext()
            MatcherAssert.assertThat(
                cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                CoreMatchers.equalTo(1551431485000L)
            )
            MatcherAssert.assertThat(
                cursor.getDouble(cursor.getColumnIndexOrThrow("lat")),
                CoreMatchers.equalTo(51.05210394)
            )
            MatcherAssert.assertThat(
                cursor.getDouble(cursor.getColumnIndexOrThrow("lon")),
                CoreMatchers.equalTo(13.72873203)
            )
            MatcherAssert.assertThat(
                cursor.getDoubleOrNull(cursor.getColumnIndexOrThrow("altitude")),
                CoreMatchers.equalTo(null)
            )
            MatcherAssert.assertThat(
                cursor.getDouble(cursor.getColumnIndexOrThrow("speed")),
                CoreMatchers.equalTo(1.01)
            )
            MatcherAssert.assertThat(
                cursor.getDoubleOrNull(cursor.getColumnIndexOrThrow("accuracy")),
                CoreMatchers.equalTo(5.0)
            )
            MatcherAssert.assertThat(
                cursor.getDoubleOrNull(cursor.getColumnIndexOrThrow("verticalAccuracy")),
                CoreMatchers.equalTo(null)
            )
            MatcherAssert.assertThat(
                cursor.getLong(cursor.getColumnIndexOrThrow("measurementId")),
                CoreMatchers.equalTo(43L)
            )
        }
        // Ensure accuracy of `0.0` is set to `null`
        db.query(
            SupportSQLiteQueryBuilder.builder("Location")
                .selection("measurementId = ?", arrayOf("44"))
                .create()
        ).use { cursor ->
            MatcherAssert.assertThat(cursor.count, CoreMatchers.equalTo(1))
            cursor.moveToNext()
            MatcherAssert.assertThat(
                cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                CoreMatchers.equalTo(1551431485000L)
            )
            MatcherAssert.assertThat(
                cursor.getDouble(cursor.getColumnIndexOrThrow("lat")),
                CoreMatchers.equalTo(51.05210394)
            )
            MatcherAssert.assertThat(
                cursor.getDouble(cursor.getColumnIndexOrThrow("lon")),
                CoreMatchers.equalTo(13.72873203)
            )
            MatcherAssert.assertThat(
                cursor.getDoubleOrNull(cursor.getColumnIndexOrThrow("altitude")),
                CoreMatchers.equalTo(null)
            )
            MatcherAssert.assertThat(
                cursor.getDouble(cursor.getColumnIndexOrThrow("speed")),
                CoreMatchers.equalTo(1.01)
            )
            MatcherAssert.assertThat(
                cursor.getDoubleOrNull(cursor.getColumnIndexOrThrow("accuracy")),
                CoreMatchers.equalTo(null)
            )
            MatcherAssert.assertThat(
                cursor.getDoubleOrNull(cursor.getColumnIndexOrThrow("verticalAccuracy")),
                CoreMatchers.equalTo(null)
            )
            MatcherAssert.assertThat(
                cursor.getLong(cursor.getColumnIndexOrThrow("measurementId")),
                CoreMatchers.equalTo(44L)
            )
        }
        // Pressure table
        db.query(
            SupportSQLiteQueryBuilder.builder("Pressure").create()
        ).use { cursor ->
            MatcherAssert.assertThat(cursor.count, CoreMatchers.equalTo(0))
        }
        // `runMigrationsAndValidate()` above ensures old tables like `locations` are deleted.
    }

    private fun checkV18EventTable(db: SupportSQLiteDatabase) {
        db.query(
            SupportSQLiteQueryBuilder.builder("Event")
                .selection("measurementId = ?", arrayOf("43"))
                .create()
        ).use { cursor ->
            MatcherAssert.assertThat(cursor.count, CoreMatchers.equalTo(2))
            cursor.moveToNext()
            MatcherAssert.assertThat(
                cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                CoreMatchers.equalTo(1551431485000L)
            )
            MatcherAssert.assertThat(
                cursor.getString(cursor.getColumnIndexOrThrow("type")),
                CoreMatchers.equalTo("MODALITY_TYPE_CHANGE")
            )
            MatcherAssert.assertThat(
                cursor.getStringOrNull(cursor.getColumnIndexOrThrow("value")),
                CoreMatchers.equalTo("CAR")
            )
            MatcherAssert.assertThat(
                cursor.getLong(cursor.getColumnIndexOrThrow("measurementId")),
                CoreMatchers.equalTo(43L)
            )
            cursor.moveToNext()
            MatcherAssert.assertThat(
                cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                CoreMatchers.equalTo(1551431485000L)
            )
            MatcherAssert.assertThat(
                cursor.getString(cursor.getColumnIndexOrThrow("type")),
                CoreMatchers.equalTo("LIFECYCLE_START")
            )
            MatcherAssert.assertThat(
                cursor.getStringOrNull(cursor.getColumnIndexOrThrow("value")),
                CoreMatchers.equalTo(null)
            )
            MatcherAssert.assertThat(
                cursor.getLong(cursor.getColumnIndexOrThrow("measurementId")),
                CoreMatchers.equalTo(43L)
            )
        }
    }

    private fun checkV18MeasurementTable(db: SupportSQLiteDatabase, measurements: Int) {
        Validate.isTrue(measurements > 0)
        db.query(
            SupportSQLiteQueryBuilder.builder("Measurement").create()
        ).use { cursor ->
            MatcherAssert.assertThat(cursor.count, CoreMatchers.equalTo(measurements))
            cursor.moveToNext()
            MatcherAssert.assertThat(
                cursor.getString(cursor.getColumnIndexOrThrow("status")),
                CoreMatchers.equalTo("FINISHED")
            )
            MatcherAssert.assertThat(
                cursor.getString(cursor.getColumnIndexOrThrow("modality")),
                CoreMatchers.equalTo("BICYCLE")
            )
            MatcherAssert.assertThat(
                cursor.getShort(cursor.getColumnIndexOrThrow("fileFormatVersion")),
                CoreMatchers.equalTo(1)
            )
            MatcherAssert.assertThat(
                cursor.getDouble(cursor.getColumnIndexOrThrow("distance")),
                CoreMatchers.equalTo(5396.62473698979)
            )
            MatcherAssert.assertThat(
                cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                CoreMatchers.equalTo(1551431485000L)
            )
        }
    }

    private fun checkV18IdentifierTable(db: SupportSQLiteDatabase) {
        db.query(
            SupportSQLiteQueryBuilder.builder("Identifier").create()
        ).use { cursor ->
            MatcherAssert.assertThat(cursor.count, CoreMatchers.equalTo(1))
            cursor.moveToNext()
            MatcherAssert.assertThat(
                cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                CoreMatchers.equalTo(1L)
            )
            MatcherAssert.assertThat(
                cursor.getString(cursor.getColumnIndexOrThrow("deviceId")),
                CoreMatchers.equalTo("61e112e1-548e-4a90-be28-9d5b31d6875b")
            )
        }
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
     *
     * Back in 2019 the SR app switched to another package in the Play Store, so the users had to install
     * a new app, and the old app was renamed to "SR-2018" or so. I.e. it should be impossible that
     * users of the current SR app have an older database version than 8.
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
     * @param db A clean [SQLiteDatabase] to use for testing
     * @param measurementId the id of the measurement to generate
     * @param locations number of locations to generate for the measurement to be generated
     * @param accuracy The accuracy to be used in the locations
     */
    private fun addDatabaseV17Measurement(
        db: SupportSQLiteDatabase,
        @Suppress("SameParameterValue") measurementId: Long,
        @Suppress("SameParameterValue") locations: Long,
        accuracy: Double
    ) {
        db.execSQL(
            ("INSERT INTO measurements (_id,status,modality,file_format_version,distance,timestamp) VALUES "
                    + " ($measurementId,'FINISHED','BICYCLE',1,5396.62473698979,1551431485000)")
        )
        val idOffset = measurementId * 1000000
        db.execSQL(
            ("INSERT INTO events (_id,timestamp,type,value,measurement_fk) VALUES "
                    + " (${idOffset + 1},1551431485000,'MODALITY_TYPE_CHANGE','CAR',$measurementId)")
        )
        db.execSQL(
            ("INSERT INTO events (_id,timestamp,type,value,measurement_fk) VALUES "
                    + " (${idOffset + 2},1551431485000,'LIFECYCLE_START',null,$measurementId)")
        )
        // Insert locations - execSQL only supports one insert per commend
        for (i in 0 until locations) {
            val id = measurementId * 1000000 + i
            val timestamp = 1551431485000L + i
            val speed = 1.01
            db.execSQL(
                ("INSERT INTO locations (_id,gps_time,lat,lon,speed,accuracy,measurement_fk) VALUES "
                        + " ($id,$timestamp,51.05210394,13.72873203,$speed,$accuracy,$measurementId)")
            )
        }
    }

    /**
     * Creates a secondary database with the name `v6` which existed in SDK 6.3 and 7.4.
     *
     * @param context The context required to get the database folder path.
     * @param v6DatabaseName The file name of the database to create.
     * @param version The database version to set.
     */
    private fun createV6Database(
        context: Context,
        @Suppress("SameParameterValue") v6DatabaseName: String,
        @Suppress("SameParameterValue") version: Int
    ): SQLiteDatabase {
        val file = context.getDatabasePath(v6DatabaseName)
        try {
            val db = context.openOrCreateDatabase(v6DatabaseName, MODE_PRIVATE, null)
            db.version = 1
            return db
        } catch (e: RuntimeException) {
            throw java.lang.IllegalStateException("Unable to open database at ${file.path}")
        }
    }

    /**
     * Adds database `v6` sample data for a measurement as it would exist for measurements captured
     * with SDK 6.3 or 7.4 which stored `Pressure` into `v6` only and stored a copy of the locations
     * in `v6`'s `Location` table which was annotated with `altitude` and `verticalAccuracy`.
     *
     * @param db A clean [SQLiteDatabase] to use for testing
     * @param measurementId the id of the measurement to generate
     * @param locations number of locations to generate for the measurement to be generated
     * @param pressures number of pressures to generate for the measurement to be generated
     * @param accuracyInCm The accuracy to be used in the locations
     */
    private fun addV6LocationsAndPressures(
        db: SQLiteDatabase,
        @Suppress("SameParameterValue") measurementId: Long,
        @Suppress("SameParameterValue") locations: Int,
        @Suppress("SameParameterValue") pressures: Int,
        accuracyInCm: Double
    ) {
        // Insert Location - execSQL only supports one insert per commend
        for (i in 0 until locations) {
            val id = measurementId * 1000000 + i
            val timestamp = 1551431485000L + i
            val speed = 1.01
            val verticalAccuracy = 20.0
            db.execSQL(
                ("INSERT INTO Location (uid,lat,lon,altitude,speed,accuracy,vertical_accuracy,measurement_fk,timestamp) "
                        + "VALUES ($id,51.05210394,13.72873203,400.0,$speed,$accuracyInCm,$verticalAccuracy,$measurementId,$timestamp)")
            )
        }
        // Insert Pressure
        for (i in 0 until pressures) {
            val id = measurementId * 1000000 + i
            val timestamp = 1551431485000L + i
            db.execSQL(
                ("INSERT INTO Pressure (uid,pressure,timestamp,measurement_fk) VALUES "
                        + " ($id,1013.25,$timestamp,$measurementId)")
            )
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