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
package de.cyface.persistence.serialization

import android.database.Cursor
import android.os.RemoteException
import android.util.Log
import com.google.protobuf.ByteString
import de.cyface.persistence.Constants.TAG
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.PersistenceLayer
import de.cyface.persistence.content.AbstractCyfaceTable.Companion.DATABASE_QUERY_LIMIT
import de.cyface.persistence.model.Measurement
import de.cyface.protos.model.Event
import de.cyface.protos.model.LocationRecords
import de.cyface.protos.model.MeasurementBytes
import de.cyface.serializer.DataSerializable
import de.cyface.utils.CursorIsNullException
import de.cyface.utils.Validate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.IOException

/**
 * Serializes [MeasurementSerializer.TRANSFER_FILE_FORMAT_VERSION] files.
 *
 * @author Armin Schnabel
 * @version 3.0.0
 * @since 5.0.0
 */
object TransferFileSerializer {
    /**
     * Implements the core algorithm of loading data of a [Measurement] from the [PersistenceLayer]
     * and serializing it into an array of bytes, ready to be compressed.
     *
     * We use the {@param loader} to access the measurement data.
     *
     * We assemble the data using a buffer to avoid OOM exceptions.
     *
     * **ATTENTION:** The caller must make sure the {@param bufferedOutputStream} is closed when no longer needed
     * or the app crashes.
     *
     * @param bufferedOutputStream The `OutputStream` to which the serialized data should be written. Injecting
     * this allows us to compress the serialized data without the need to write it into a temporary file.
     * We require a [BufferedOutputStream] for performance reasons.
     * @param measurementIdentifier The id of the `Measurement` to load
     * @param persistence The `PersistenceLayer` to load the `Measurement` data from
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @JvmStatic
    @Throws(CursorIsNullException::class)
    suspend fun loadSerialized(
        bufferedOutputStream: BufferedOutputStream,
        measurementIdentifier: Long,
        persistence: PersistenceLayer<*>
    ) {
        // Load data from ContentProvider
        val events = loadEvents(measurementIdentifier, persistence)
        val locationRecords = loadLocations(measurementIdentifier, persistence)

        // Using the modified `MeasurementBytes` class to inject the sensor bytes without parsing
        val builder = MeasurementBytes.newBuilder()
            .setFormatVersion(MeasurementSerializer.TRANSFER_FILE_FORMAT_VERSION.toInt())
            .addAllEvents(events)
            .setLocationRecords(locationRecords)

        // Get already serialized Point3DFiles
        val accelerationFile = persistence.fileDao.getFilePath(
            persistence.context!!,
            measurementIdentifier,
            Point3DFile.ACCELERATIONS_FOLDER_NAME,
            Point3DFile.ACCELERATIONS_FILE_EXTENSION
        )
        val rotationFile = persistence.fileDao.getFilePath(
            persistence.context!!,
            measurementIdentifier,
            Point3DFile.ROTATIONS_FOLDER_NAME,
            Point3DFile.ROTATION_FILE_EXTENSION
        )
        val directionFile = persistence.fileDao.getFilePath(
            persistence.context!!,
            measurementIdentifier,
            Point3DFile.DIRECTIONS_FOLDER_NAME,
            Point3DFile.DIRECTION_FILE_EXTENSION
        )

        // Ensure we only inject bytes from the correct persistence format version
        val measurement: Measurement? = persistence.loadMeasurement(measurementIdentifier)
        Validate.isTrue(measurement!!.fileFormatVersion == DefaultPersistenceLayer.PERSISTENCE_FILE_FORMAT_VERSION)
        if (accelerationFile.exists()) {
            Log.v(
                TAG, String.format(
                    "Serializing %s accelerations for synchronization.",
                    DataSerializable.humanReadableSize(accelerationFile.length(), true)
                )
            )
            val bytes = persistence.fileDao.loadBytes(accelerationFile)
            builder.accelerationsBinary = ByteString.copyFrom(bytes)
        }
        if (rotationFile.exists()) {
            Log.v(
                TAG, String.format(
                    "Serializing %s rotations for synchronization.",
                    DataSerializable.humanReadableSize(rotationFile.length(), true)
                )
            )
            val bytes = persistence.fileDao.loadBytes(rotationFile)
            builder.rotationsBinary = ByteString.copyFrom(bytes)
        }
        if (directionFile.exists()) {
            Log.v(
                TAG, String.format(
                    "Serializing %s directions for synchronization.",
                    DataSerializable.humanReadableSize(directionFile.length(), true)
                )
            )
            val bytes = persistence.fileDao.loadBytes(directionFile)
            builder.directionsBinary = ByteString.copyFrom(bytes)
        }

        // Currently loading the whole measurement into memory (~ 5MB / hour serialized).
        // - To add high-res image data in the future we cannot use the pre-compiled builder but
        // have to stream the image data without loading it into memory to avoid an OOM exception.
        val transferFileHeader = DataSerializable.transferFileHeader()
        val measurementBytes = builder.build().toByteArray()
        try {
            // The stream must be closed by the called in a finally catch
            withContext(Dispatchers.IO) {
                bufferedOutputStream.write(transferFileHeader)
                bufferedOutputStream.write(measurementBytes)
                bufferedOutputStream.flush()
            }
        } catch (e: IOException) {
            throw IllegalStateException(e)
        }
        Log.d(
            TAG, String.format(
                "Serialized %s",
                DataSerializable.humanReadableSize(
                    (transferFileHeader.size + measurementBytes.size).toLong(),
                    true
                )
            )
        )
    }

    /**
     * Loads and serializes [Event]s from the persistence layer.
     *
     * @param measurementId The id of the `Measurement` to load
     * @param persistence The `PersistenceLayer` to load the `Measurement` data from
     */
    @Throws(CursorIsNullException::class)
    private suspend fun loadEvents(
        measurementId: Long,
        persistence: PersistenceLayer<*>
    ): List<Event?> {
        val serializer = EventSerializer()
        try {
            val count = persistence.eventRepository!!.countByMeasurementId(measurementId)
            var startIndex = 0
            while (startIndex < count) {
                persistence.eventRepository!!.selectAllByMeasurementId(
                    measurementId,
                    startIndex,
                    DATABASE_QUERY_LIMIT
                ).use { cursor ->
                    if (cursor == null) throw CursorIsNullException()
                    serializer.readFrom(cursor)
                }
                startIndex += DATABASE_QUERY_LIMIT
            }
        } catch (e: RemoteException) {
            throw java.lang.IllegalStateException(e)
        }
        return serializer.result()
    }

    /**
     * Loads and serializes [LocationRecords] from the persistence layer.
     *
     * @param measurementId The id of the `Measurement` to load
     * @param persistence The `PersistenceLayer` to load the `Measurement` data from
     */
    @Throws(CursorIsNullException::class)
    private fun loadLocations(
        measurementId: Long,
        persistence: PersistenceLayer<*>
    ): LocationRecords {
        val serializer = LocationSerializer()
        var cursor: Cursor? = null
        try {
            val count = persistence.locationDao!!.countByMeasurementId(measurementId)
            var startIndex = 0
            while (startIndex < count) {
                cursor =
                    persistence.locationDao!!.selectAllByMeasurementId(
                        measurementId,
                        startIndex,
                        DATABASE_QUERY_LIMIT
                    )
                if (cursor == null) throw CursorIsNullException()
                serializer.readFrom(cursor)
                startIndex += DATABASE_QUERY_LIMIT
            }
        } catch (e: RemoteException) {
            throw java.lang.IllegalStateException(e)
        } finally {
            cursor?.close()
        }
        return serializer.result()
    }
}