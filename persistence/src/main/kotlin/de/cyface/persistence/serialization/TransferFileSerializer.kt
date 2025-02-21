/*
 * Copyright 2019-2025 Cyface GmbH
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
import androidx.sqlite.db.SimpleSQLiteQuery
import com.google.protobuf.ByteString
import de.cyface.persistence.Constants.TAG
import de.cyface.persistence.Database
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.PersistenceLayer
import de.cyface.persistence.content.AbstractCyfaceTable.Companion.DATABASE_QUERY_LIMIT
import de.cyface.persistence.content.BaseColumns
import de.cyface.persistence.content.LocationTable
import de.cyface.persistence.model.Attachment
import de.cyface.persistence.model.Measurement
import de.cyface.protos.model.Event
import de.cyface.protos.model.LocationRecords
import de.cyface.protos.model.MeasurementBytes
import de.cyface.serializer.DataSerializable
import de.cyface.utils.CursorIsNullException
import de.cyface.utils.Validate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.IOException
import java.util.Locale

/**
 * Serializes [MeasurementSerializer.TRANSFER_FILE_FORMAT_VERSION] files.
 *
 * @author Armin Schnabel
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
        val accelerationFile = persistence.fileIOHandler.getFilePath(
            persistence.context!!,
            measurementIdentifier,
            Point3DFile.ACCELERATIONS_FOLDER_NAME,
            Point3DFile.ACCELERATIONS_FILE_EXTENSION
        )
        val rotationFile = persistence.fileIOHandler.getFilePath(
            persistence.context!!,
            measurementIdentifier,
            Point3DFile.ROTATIONS_FOLDER_NAME,
            Point3DFile.ROTATION_FILE_EXTENSION
        )
        val directionFile = persistence.fileIOHandler.getFilePath(
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
                TAG,
                String.format(
                    Locale.getDefault(),
                    "Serializing %s accelerations for synchronization.",
                    DataSerializable.humanReadableSize(accelerationFile.length(), true)
                )
            )
            val bytes = persistence.fileIOHandler.loadBytes(accelerationFile)
            builder.accelerationsBinary = ByteString.copyFrom(bytes)
        }
        if (rotationFile.exists()) {
            Log.v(
                TAG,
                String.format(
                    Locale.getDefault(),
                    "Serializing %s rotations for synchronization.",
                    DataSerializable.humanReadableSize(rotationFile.length(), true)
                )
            )
            val bytes = persistence.fileIOHandler.loadBytes(rotationFile)
            builder.rotationsBinary = ByteString.copyFrom(bytes)
        }
        if (directionFile.exists()) {
            Log.v(
                TAG,
                String.format(
                    Locale.getDefault(),
                    "Serializing %s directions for synchronization.",
                    DataSerializable.humanReadableSize(directionFile.length(), true)
                )
            )
            val bytes = persistence.fileIOHandler.loadBytes(directionFile)
            builder.directionsBinary = ByteString.copyFrom(bytes)
        }

        // Currently loading the whole measurement into memory (~ 5MB / hour serialized).
        // - To add high-res image data in the future we cannot use the pre-compiled builder but
        // have to stream the image data without loading it into memory to avoid an OOM exception.
        val transferFileHeader = DataSerializable.transferFileHeader()
        val measurementBytes = builder.build().toByteArray()
        try {
            // The stream must be closed by the caller in a finally catch
            withContext(Dispatchers.IO) {
                bufferedOutputStream.write(transferFileHeader)
                bufferedOutputStream.write(measurementBytes)
                bufferedOutputStream.flush()
            }
        } catch (e: IOException) {
            throw IllegalStateException(e)
        }
        Log.d(
            TAG,
            String.format(
                Locale.getDefault(),
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
                    persistence.database!!,
                    measurementId,
                    startIndex,
                    DATABASE_QUERY_LIMIT
                ).use { cursor ->
                    //if (cursor == null) throw CursorIsNullException()
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
    ): LocationRecords = runBlocking {
        val serializer = LocationSerializer()
        var cursor: Cursor? = null
        try {
            val count = persistence.locationDao!!.countByMeasurementId(measurementId)
            var startIndex = 0
            while (startIndex < count) {
                cursor = getLocationCursor(
                    persistence.database!!,
                    measurementId,
                    startIndex,
                    DATABASE_QUERY_LIMIT,
                )
                //if (cursor == null) throw CursorIsNullException()
                serializer.readFrom(cursor)
                startIndex += DATABASE_QUERY_LIMIT
            }
        } catch (e: RemoteException) {
            throw java.lang.IllegalStateException(e)
        } finally {
            cursor?.close()
        }
        return@runBlocking serializer.result()
    }

    /**
     * Returns a `Cursor` which points to a specific page defined by [limit] and [offset] of all
     * locations of a measurement with a specified the [measurementId].
     *
     * This way we can reuse the code in `SyncAdapter` > `TransferFileSerializer` which queries and
     * serializes only 10_000 entries at a time which fixed performance issues with large
     * measurements. This could be replaced by room-paging, but it's not straight forward.
     *
     * The locations are ordered by timestamp.
     */
    internal fun getLocationCursor(
        database: Database,
        measurementId: Long,
        offset: Int,
        limit: Int
    ): Cursor {
        val query = SimpleSQLiteQuery(
            "SELECT * FROM ${LocationTable.URI_PATH} " +
                    "WHERE ${BaseColumns.MEASUREMENT_ID} = ? " +
                    "ORDER BY ${BaseColumns.TIMESTAMP} ASC " +
                    "LIMIT ? " +
                    "OFFSET ?",
            arrayOf(measurementId, limit, offset)
        )
        // Executing `Cursor` based query directly on the `database` without `dao` layer as Room
        // does not support `Cursor` return types with `KSP` properly.
        // This way we are not be forced to change the currently working code.
        return database.query(query)
    }

    /**
     * Loads and serializes a [Attachment] from the persistence layer.
     *
     * @param attachment The reference of the entry to load
     */
    @Throws(CursorIsNullException::class)
    private fun loadAttachment(
        attachment: Attachment
    ): de.cyface.protos.model.File {
        val serializer = AttachmentSerializer()
        try {
            serializer.readFrom(attachment)
        } catch (e: RemoteException) {
            throw java.lang.IllegalStateException(e)
        }
        return serializer.result()
    }

    /**
     * Implements the core algorithm of loading data of a [Attachment] from the [PersistenceLayer]
     * and serializing it into an array of bytes, ready to be transferred.
     *
     * We assemble the data using a buffer to avoid OOM exceptions.
     *
     * **ATTENTION:** The caller must make sure the {@param bufferedOutputStream} is closed when no longer needed
     * or the app crashes.
     *
     * Attention:
     * We don't wrap the attachments in the `cyf` wrapper, as:
     * - Most our project currently prefer the plain JPG, CSV, ZIP, etc. formats
     * - We have a version in meta data, and currently have version 1 for attachment files format.
     *
     * @param bufferedOutputStream The `OutputStream` to which the serialized data should be written. Injecting
     * this allows us to compress the serialized data without the need to write it into a temporary file.
     * We require a [BufferedOutputStream] for performance reasons.
     * @param reference The [de.cyface.persistence.model.Attachment] to load
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @JvmStatic
    @Throws(CursorIsNullException::class)
    suspend fun loadSerializedAttachment(
        bufferedOutputStream: BufferedOutputStream,
        reference: Attachment,
    ) {
        val attachment = loadAttachment(reference)

        // In case we switch back to the cyf wrapper for attachments, we need to adjust the code:
        // Out Protobuf format only supports one `capturing_log` file, but we collect multiple
        // files which do not fit the "images" or "video" categories (i.e. CSV, JSON). If we would
        // upload all attachments in one request, we would need to compress them into one ZIP files.
        // That is why we added the file format "ZIP" to the Protobuf message definition.
        // But as we upload each attachment separately, even with the cyf wrapper we should be fine
        // with one `capturing_log` support, as we can just add this one file as such.
        // So if you enable the cyf wrapping code below again, make sure all log files are uploaded.
        /*val builder = de.cyface.protos.model.Measurement.newBuilder()
            .setFormatVersion(MeasurementSerializer.TRANSFER_FILE_FORMAT_VERSION.toInt())
        when (reference.type) {
            FileType.JSON, FileType.CSV -> {
                builder.capturingLog = attachment
            }

            FileType.JPG -> {
                builder.addAllImages(mutableListOf(attachment))
            }

            else -> {
                throw IllegalArgumentException("Unsupported type: ${reference.type}")
            }
        }*/

        // Currently loading one image per transfer file into memory (~ 2-5 MB / image).
        // - To load add all high-res image data or video data in the future we cannot use the pre-compiled
        // builder but have to stream the data without loading it into memory to avoid an OOM exception.
        //val transferFileHeader = DataSerializable.transferFileHeader()
        //val uploadBytes = builder.build().toByteArray()
        val uploadBytes = attachment.bytes.toByteArray()
        try {
            // The stream must be closed by the caller in a finally catch
            withContext(Dispatchers.IO) {
                //bufferedOutputStream.write(transferFileHeader)
                bufferedOutputStream.write(uploadBytes)
                bufferedOutputStream.flush()
            }
        } catch (e: IOException) {
            throw IllegalStateException(e)
        }
        Log.d(
            TAG,
            String.format(
                Locale.getDefault(),
                "Serialized attachment: %s",
                DataSerializable.humanReadableSize(
                    (/*transferFileHeader.size +*/ uploadBytes.size).toLong(),
                    true
                )
            )
        )
    }
}