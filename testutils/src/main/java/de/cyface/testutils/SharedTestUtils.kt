/*
 * Copyright 2018-2023 Cyface GmbH
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
package de.cyface.testutils

import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import de.cyface.model.Point3DImpl
import de.cyface.model.RequestMetaData
import de.cyface.persistence.Constants
import de.cyface.persistence.Database
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.PersistenceBehaviour
import de.cyface.persistence.PersistenceLayer
import de.cyface.persistence.content.EventTable
import de.cyface.persistence.content.LocationTable
import de.cyface.persistence.content.MeasurementTable
import de.cyface.persistence.dao.DefaultFileDao
import de.cyface.persistence.dao.FileDao
import de.cyface.persistence.dao.LocationDao
import de.cyface.persistence.exception.NoSuchMeasurementException
import de.cyface.persistence.model.GeoLocation
import de.cyface.persistence.model.MeasurementStatus
import de.cyface.persistence.model.Modality
import de.cyface.persistence.model.ParcelableGeoLocation
import de.cyface.persistence.serialization.Point3DFile
import de.cyface.persistence.strategy.DefaultLocationCleaning
import de.cyface.protos.model.Measurement
import de.cyface.protos.model.MeasurementBytes
import de.cyface.serializer.model.Point3DType
import de.cyface.utils.CursorIsNullException
import de.cyface.utils.Validate
import org.hamcrest.CoreMatchers
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import java.io.File

/**
 * This class (and the module test-utils) exist to be able to share test code between modules.
 * It's located in the main folder to be compiled and imported as dependency in the testImplementations.
 *
 * @author Armin Schnabel
 * @version 7.0.0
 * @since 3.0.0
 */
object SharedTestUtils {
    /**
     * The following constants were selected so that adding each base+constant results in coordinates with approximately
     * 1 meter distance between base coordinates and base+1*constant coordinates
     */
    private const val BASE_LAT = 51.100

    /**
     * see [.BASE_LAT]
     */
    private const val BASE_LON = 13.100

    /**
     * see [.BASE_LAT]
     */
    private const val LAT_CONSTANT = 0.000008993199995

    /**
     * see [.BASE_LAT]
     */
    private const val LON_CONSTANT = 0.0000000270697

    /**
     * To make integration tests reproducible we need to ensure old account (after stopped tests) are cleaned up.
     *
     * @param accountManager The [AccountManager] to be used to access accounts.
     * @param accountType The account type to search for.
     * @param authority The authority to access the accounts.
     */
    // Used by the cyface flavour tests
    @JvmStatic
    fun cleanupOldAccounts(
        accountManager: AccountManager,
        accountType: String, authority: String
    ) {

        // To make these tests reproducible make sure we don't reuse old sync accounts
        for (account in accountManager.getAccountsByType(accountType)) {
            ContentResolver.removePeriodicSync(account, authority, Bundle.EMPTY)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
                accountManager.removeAccount(account, null, null)
            } else {
                Validate.isTrue(accountManager.removeAccountExplicitly(account))
            }
        }
        // To ensure reproducibility make sure there is no old account registered
        val oldAccounts = accountManager.getAccountsByType(accountType)
        assertThat(oldAccounts.size, equalTo(0))
    }

    /**
     * Generates [ParcelableGeoLocation]s with coordinates for testing.
     *
     * See {@param relativeDistance} if you need locations with a specific distance from each other.
     *
     * @param distanceFromBase an integer which defines how much the generated `GeoLocation`s are away from each
     * other. E.g.: If you generate `GeoLocation`s using {@param relativeDistance} 1, 3, 5 the
     * generated locations will be approximately 1 meter per {@param relativeDistance}-difference away from
     * each other. In this case (1, 5) = 4m distance and (1, 3) or (3, 5) = 2m distance.
     * @return the generated `GeoLocation`
     */
    @JvmStatic
    fun generateGeoLocation(distanceFromBase: Int): ParcelableGeoLocation {
        return generateGeoLocation(distanceFromBase, 1000000000L)
    }


    /**
     * Generates [ParcelableGeoLocation]s with coordinates for testing.
     *
     * See {@param relativeDistance} if you need locations with a specific distance from each other.
     *
     * @param distanceFromBase an integer which defines how much the generated `GeoLocation`s are away from each
     * other. E.g.: If you generate `GeoLocation`s using {@param relativeDistance} 1, 3, 5 the
     * generated locations will be approximately 1 meter per {@param relativeDistance}-difference away from
     * each other. In this case (1, 5) = 4m distance and (1, 3) or (3, 5) = 2m distance.
     * @return the generated `GeoLocation`
     */
    @JvmStatic
    fun generateGeoLocation(distanceFromBase: Int, timestamp: Long): ParcelableGeoLocation {
        val salt = Math.random()
        return ParcelableGeoLocation(
            timestamp,
            BASE_LAT + distanceFromBase * LAT_CONSTANT,
            BASE_LON + distanceFromBase * LON_CONSTANT,
            400.0,
            DefaultLocationCleaning.LOWER_SPEED_THRESHOLD.coerceAtLeast(salt * DefaultLocationCleaning.UPPER_SPEED_THRESHOLD),
            salt * (DefaultLocationCleaning.UPPER_ACCURACY_THRESHOLD - 1),
            20.0
        )
    }

    @JvmStatic
    fun generateRequestMetaDataGeoLocation(
        distanceFromBase: Int,
        timestamp: Long
    ): RequestMetaData.GeoLocation {
        val location = generateGeoLocation(distanceFromBase, timestamp)
        return RequestMetaData.GeoLocation(location.timestamp, location.lat, location.lon)
    }

    /**
     * Inserts a test [de.cyface.persistence.model.ParcelablePoint3D] into the database content provider accessed by the test.
     *
     * @param point3DFile existing file to append the data to
     * @param timestamp A fake test timestamp of the `Point3D`.
     * @param x A fake test x coordinate of the `Point3D`.
     * @param y A fake test y coordinate of the `Point3D`.
     * @param z A fake test z coordinate of the `Point3D`.
     */
    // Used by the cyface flavour tests
    @JvmStatic
    fun insertPoint3D(
        point3DFile: Point3DFile, timestamp: Long, x: Double,
        y: Double, z: Double
    ) {
        val points = ArrayList<Point3DImpl?>()
        points.add(Point3DImpl(x.toFloat(), y.toFloat(), z.toFloat(), timestamp))
        insertPoint3Ds(point3DFile, points)
    }

    /**
     * Inserts [de.cyface.persistence.model.ParcelablePoint3D]s into the database content provider accessed by the test.
     *
     *
     * This increases the performance of large tests.
     *
     * @param point3DFile existing file to append the data to
     * @param point3Ds Test fake `Point3D`s.
     */
    private fun insertPoint3Ds(point3DFile: Point3DFile, point3Ds: List<Point3DImpl?>) {

        // Avoid OOM when adding too much data at once
        val insertLimit = 100000
        var nextInsertedIndex = 0
        while (nextInsertedIndex < point3Ds.size) {
            val sublist = point3Ds.subList(
                nextInsertedIndex,
                Math.min(nextInsertedIndex + insertLimit, point3Ds.size)
            )
            point3DFile.append(sublist)
            nextInsertedIndex += sublist.size
            Log.v(Constants.TAG, "Inserted $nextInsertedIndex")
        }
    }

    /**
     * This deserializes a [Point3DFile] for testing.
     *
     * @param fileDao The [FileDao] used to access the files.
     * @param file The `Point3DFile` to access
     * @param type The [Point3DType] for the `file` passed as parameter
     * @return the data restored from the `Point3DFile`
     * @throws InvalidProtocolBufferException if the `Point3DFile` format is unknown
     */
    @JvmStatic
    @Throws(InvalidProtocolBufferException::class)
    fun deserialize(
        fileDao: FileDao, file: File?,
        type: Point3DType
    ): Measurement {
        val bytes = fileDao.loadBytes(file)
        val measurementBytes = MeasurementBytes.newBuilder()
            .setFormatVersion(DefaultPersistenceLayer.PERSISTENCE_FILE_FORMAT_VERSION.toInt())
        when (type) {
            Point3DType.ACCELERATION -> measurementBytes.accelerationsBinary =
                ByteString.copyFrom(bytes)
            Point3DType.ROTATION -> measurementBytes.rotationsBinary = ByteString.copyFrom(bytes)
            Point3DType.DIRECTION -> measurementBytes.directionsBinary = ByteString.copyFrom(bytes)
            else -> throw IllegalArgumentException("Unknown type: $type")
        }
        val data = measurementBytes.build()
        return Measurement.parseFrom(data.toByteArray())
    }

    /**
     * Removes everything from the local persistent data storage to allow reproducible test results.
     *
     * (!) This removes both the data from file persistence and the database which will also reset the device id.
     * This is not part of the persistence layer as we want to avoid that this is used outside the test code.
     *
     * This method mut be in the [SharedTestUtils] to ensure multiple modules can access it in androidTests!
     *
     * @param context The [Context] required to access the file persistence layer
     * @param persistence The [PersistenceLayer] to remove the data from
     * @return number of rows removed from the database and number of **FILES** (not points) deleted. The earlier
     * includes [Measurement]s, [ParcelableGeoLocation]s and [de.cyface.persistence.model.Event]s.
     * The later includes the [Point3DFile]s.
     */
    @JvmStatic
    fun clearPersistenceLayer(
        context: Context,
        persistence: PersistenceLayer<PersistenceBehaviour>
    ): Int {
        // To be able to inject the database, the persistence layer has to be created before but if we
        // remove the file folders, they are not create again, as this is done in persistence construction.
        val removedFiles = clearFileLayer(context, false)

        // Remove database entries
        val removedGeoLocations = persistence.locationDao!!.deleteAll()
        val removedPressures = persistence.pressureDao!!.deleteAll()
        val removedEvents = persistence.eventDao!!.deleteAll()
        val removedMeasurements = persistence.measurementDao!!.deleteAll()
        // Unclear why this breaks the life-cycle tests in DataCapturingServiceTest.
        // However this should be okay to ignore for now as the identifier table should never be reset unless the
        // database itself is removed when the app is uninstalled or the app data is deleted.
        // final int removedIdentifierRows = resolver.delete(getIdentifierUri(authority), null, null);
        return (removedFiles +  /* removedIdentifierRows + */removedGeoLocations + removedPressures + removedEvents
                + removedMeasurements)
    }

    /**
     * Removes everything from the local persistent data storage to allow reproducible test results.
     *
     * (!) This removes both the data from file persistence and the database which will also reset the device id.
     * This is not part of the persistence layer as we want to avoid that this is used outside the test code.
     *
     * This method mut be in the [SharedTestUtils] to ensure multiple modules can access it in androidTests!
     *
     * @param context The [Context] required to access the file persistence layer
     * @param resolver The [ContentResolver] required to access the database
     * @return number of rows removed from the database and number of **FILES** (not points) deleted. The earlier
     * includes [Measurement]s, [ParcelableGeoLocation]s and [de.cyface.persistence.model.Event]s.
     * The later includes the [Point3DFile]s.
     */
    @JvmStatic
    fun clearPersistenceLayer(context: Context, resolver: ContentResolver, authority: String): Int {
        val removedFiles = clearFileLayer(context, true)

        // Remove database entries
        val removedGeoLocations = resolver.delete(LocationTable.getUri(authority), null, null)
        val removedEvents = resolver.delete(EventTable.getUri(authority), null, null)
        val removedMeasurements = resolver.delete(MeasurementTable.getUri(authority), null, null)
        // Unclear why this breaks the life-cycle tests in DataCapturingServiceTest.
        // However this should be okay to ignore for now as the identifier table should never be reset unless the
        // database itself is removed when the app is uninstalled or the app data is deleted.
        // final int removedIdentifierRows = resolver.delete(getIdentifierUri(authority), null, null);
        return removedFiles +  /* removedIdentifierRows + */removedGeoLocations + removedEvents + removedMeasurements
    }

    private fun clearFileLayer(context: Context, removeFolder: Boolean = true): Int {
        val fileDao: FileDao = DefaultFileDao()

        // Remove {@code Point3DFile}s and their parent folders
        var removedFiles = 0
        val accelerationFolder =
            fileDao.getFolderPath(context, Point3DFile.ACCELERATIONS_FOLDER_NAME)
        if (accelerationFolder.exists()) {
            Validate.isTrue(accelerationFolder.isDirectory)
            val accelerationFiles = accelerationFolder.listFiles()
            if (accelerationFiles != null) {
                for (file in accelerationFiles) {
                    Validate.isTrue(file.delete())
                }
                removedFiles += accelerationFiles.size
            }
            if (removeFolder) {
                Validate.isTrue(accelerationFolder.delete())
            }
        }
        val rotationFolder =
            fileDao.getFolderPath(context, Point3DFile.ROTATIONS_FOLDER_NAME)
        if (rotationFolder.exists()) {
            Validate.isTrue(rotationFolder.isDirectory)
            val rotationFiles = rotationFolder.listFiles()
            if (rotationFiles != null) {
                for (file in rotationFiles) {
                    Validate.isTrue(file.delete())
                }
                removedFiles += rotationFiles.size
            }
            if (removeFolder) {
                Validate.isTrue(rotationFolder.delete())
            }
        }
        val directionFolder =
            fileDao.getFolderPath(context, Point3DFile.DIRECTIONS_FOLDER_NAME)
        if (directionFolder.exists()) {
            Validate.isTrue(directionFolder.isDirectory)
            val directionFiles = directionFolder.listFiles()
            if (directionFiles != null) {
                for (file in directionFiles) {
                    Validate.isTrue(file.delete())
                }
                removedFiles += directionFiles.size
            }
            if (removeFolder) {
                Validate.isTrue(directionFolder.delete())
            }
        }
        return removedFiles
    }

    /**
     * This method inserts a [Measurement] into the persistence layer. Does not use the
     * `CapturingPersistenceBehaviour` but the [de.cyface.persistence.DefaultPersistenceBehaviour].
     *
     * @param point3DCount The number of point3Ds to insert (of each sensor type).
     * @param locationCount The number of location points to insert.
     * @throws NoSuchMeasurementException – if there was no measurement with the id .
     * @throws CursorIsNullException – If ContentProvider was inaccessible
     */
    @JvmStatic
    @Throws(NoSuchMeasurementException::class, CursorIsNullException::class)
    fun insertSampleMeasurementWithData(
        context: Context,
        status: MeasurementStatus,
        persistence: DefaultPersistenceLayer<*>,
        point3DCount: Int,
        locationCount: Int
    ): de.cyface.persistence.model.Measurement {
        Validate.isTrue(point3DCount > 0)
        Validate.isTrue(locationCount > 0)
        val geoLocations: MutableList<ParcelableGeoLocation> = ArrayList()
        val measurement = insertMeasurementEntry(persistence, Modality.UNKNOWN)
        val measurementIdentifier = measurement.id
        for (i in 0 until locationCount) {
            // We add some salt to make sure the compression of the data is realistic
            // This is required as the testOnPerformSyncWithLargeData test requires large data
            val salt = Math.random()
            geoLocations
                .add(
                    ParcelableGeoLocation(
                        1503055141000L + i, 49.9304133333333 + salt, 8.82831833333333 + salt,
                        400.0, 0.0 + salt, 9.4 + salt, 19.99
                    )
                )
        }
        insertGeoLocations(Database.build(context), measurement.id, geoLocations)

        // Insert file base data
        val accelerationsFile =
            Point3DFile(context, measurementIdentifier, Point3DType.ACCELERATION)
        val rotationsFile = Point3DFile(context, measurementIdentifier, Point3DType.ROTATION)
        val directionsFile = Point3DFile(context, measurementIdentifier, Point3DType.DIRECTION)
        val aPoints = ArrayList<Point3DImpl?>()
        val rPoints = ArrayList<Point3DImpl?>()
        val dPoints = ArrayList<Point3DImpl?>()
        val createLimit = 100000
        var alreadyInserted = 0
        var i = 0
        while (i + alreadyInserted < point3DCount) {

            // We add some salt to make sure the compression of the data is realistic
            // This is required as the testOnPerformSyncWithLargeData test requires large data
            val salt = Math.random().toFloat()
            aPoints.add(
                Point3DImpl(
                    10.1189575f + salt,
                    -0.15088624f + salt,
                    0.2921924f + salt,
                    1501662635973L + i
                )
            )
            rPoints.add(
                Point3DImpl(
                    0.001524045f + salt, 0.0025423833f + salt, -0.0010279021f + salt,
                    1501662635981L + i
                )
            )
            dPoints.add(Point3DImpl(7.65f + salt, -32.4f + salt, -71.4f + salt, 1501662636010L + i))

            // Avoid OOM when creating too much test data at once
            if (i >= createLimit - 1) {
                insertPoint3Ds(accelerationsFile, aPoints)
                insertPoint3Ds(rotationsFile, rPoints)
                insertPoint3Ds(directionsFile, dPoints)
                alreadyInserted += aPoints.size
                aPoints.clear()
                rPoints.clear()
                dPoints.clear()
                i =
                    -1 // because "i" is incremented just after this statement (end of loop iteration)
            }
            i++
        }
        insertPoint3Ds(accelerationsFile, aPoints)
        insertPoint3Ds(rotationsFile, rPoints)
        insertPoint3Ds(directionsFile, dPoints)
        if (status === MeasurementStatus.FINISHED || status === MeasurementStatus.SYNCED || status === MeasurementStatus.SKIPPED) {
            persistence.storePersistenceFileFormatVersion(
                DefaultPersistenceLayer.PERSISTENCE_FILE_FORMAT_VERSION,
                measurementIdentifier
            )
            persistence.setStatus(measurementIdentifier, MeasurementStatus.FINISHED, false)
        }
        if (status === MeasurementStatus.SYNCED) {
            persistence.markFinishedAs(MeasurementStatus.SYNCED, measurement.id)
        } else if (status === MeasurementStatus.SKIPPED) {
            persistence.markFinishedAs(MeasurementStatus.SKIPPED, measurement.id)
        }

        // Check the measurement entry
        val loadedMeasurement = persistence.loadMeasurement(measurementIdentifier)
        assertThat(loadedMeasurement, CoreMatchers.notNullValue())
        assertThat(persistence.loadMeasurementStatus(measurementIdentifier), equalTo(status))
        assertThat(
            loadedMeasurement!!.fileFormatVersion,
            equalTo(DefaultPersistenceLayer.PERSISTENCE_FILE_FORMAT_VERSION)
        )

        // Check the Tracks
        // noinspection unchecked
        val loadedTracks = persistence.loadTracks(measurementIdentifier)
        assertThat(loadedTracks[0].geoLocations.size, equalTo(locationCount))
        return measurement
    }

    /**
     * Inserts a test `Measurement` into the database content provider accessed by the test. To add data to the
     * `Measurement` use some or all of
     * [.insertGeoLocation]
     * [SharedTestUtils.insertPoint3D] (Context, long, long, double,
     * double, double)},
     *
     * @param modality The [Modality] type of the `Measurement`. A common value is [Modality.UNKNOWN]
     * if
     * you do not care.
     * @return The database identifier of the created [Measurement].
     */
    @JvmStatic
    @Throws(CursorIsNullException::class)
    fun insertMeasurementEntry(
        persistence: DefaultPersistenceLayer<*>,
        modality: Modality
    ): de.cyface.persistence.model.Measurement {

        // usually called in DataCapturingService#Constructor
        persistence.restoreOrCreateDeviceId()
        return persistence.newMeasurement(modality)
    }

    /**
     * Inserts a test [ParcelableGeoLocation] into the database content provider accessed by the test.
     *
     * @param dao The [LocationDao] to insert the data into
     * @param measurementIdentifier The identifier of the test [Measurement].
     * @param timestamp A fake test timestamp of the `GeoLocation`.
     * @param lat The fake test latitude of the `GeoLocation`.
     * @param lon The fake test longitude of the `GeoLocation`.
     * @param speed The fake test speed of the `GeoLocation`.
     * @param accuracy The fake test accuracy of the `GeoLocation`.
     */
    // Used by the cyface flavour tests
    @JvmStatic
    fun insertGeoLocation(
        dao: LocationDao, measurementIdentifier: Long,
        timestamp: Long, lat: Double, lon: Double, altitude: Double, speed: Double,
        accuracy: Double, verticalAccuracy: Double
    ) {
        dao.insertAll(
            GeoLocation(
                timestamp, lat, lon, altitude, speed, accuracy,
                verticalAccuracy, measurementIdentifier
            )
        )
    }

    /**
     * Inserts test [ParcelableGeoLocation]s into the database content provider accessed by the test.
     *
     *
     * This increases the performance of large tests and avoids "failed binder transaction - parcel size ..." error.
     *
     * @param database The [Database] to access the data
     * @param measurementIdentifier The identifier of the test [Measurement].
     * @param geoLocations Test fake `GeoLocation`s to add.
     */
    private fun insertGeoLocations(
        database: Database,
        measurementIdentifier: Long, geoLocations: List<ParcelableGeoLocation>
    ) {
        val locations = ArrayList<GeoLocation>()
        for (geoLocation in geoLocations) {
            locations.add(GeoLocation(geoLocation, measurementIdentifier))
        }
        database.locationDao().insertAll(*locations.toTypedArray())

        // This avoids "failed binder transaction - parcel size ..."
        /*final int maxBatchSize = 2_000;
        int nextInsertIndex = 0;
        while (nextInsertIndex < list.size()) {
            final List<ContentValues> sublist = list.subList(nextInsertIndex,
                    Math.min(nextInsertIndex + maxBatchSize, list.size()));
            ContentValues[] subArray = new ContentValues[sublist.size()];
            subArray = sublist.toArray(subArray);
            resolver.bulkInsert(getGeoLocationsUri(authority), subArray);
            nextInsertIndex += subArray.length;
            Log.v(TAG, "Inserted " + nextInsertIndex);
        }*/
    }
}