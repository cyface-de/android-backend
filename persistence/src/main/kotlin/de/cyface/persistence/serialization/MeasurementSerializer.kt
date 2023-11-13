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
package de.cyface.persistence.serialization

import android.util.Log
import de.cyface.persistence.Constants.TAG
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.PersistenceLayer
import de.cyface.persistence.serialization.TransferFileSerializer.loadSerialized
import de.cyface.utils.CursorIsNullException
import de.cyface.utils.Validate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/**
 * This class implements the serialization from data stored in a `MeasuringPointContentProvider` and
 * Cyface [DefaultPersistenceLayer.PERSISTENCE_FILE_FORMAT_VERSION] binary format into the Cyface
 * [.TRANSFER_FILE_FORMAT_VERSION]
 * binary format. The later consists of a header with the following information:
 *
 * - 2 Bytes which contain the `#TRANSFER_FILE_FORMAT_VERSION`
 * - followed by data in this format: https://github.com/cyface-de/protos (version 1)
 *
 * WARNING: This implementation loads all data from one measurement into memory. So be careful with large measurements.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 9.1.0
 * @since 2.0.0
 */
class MeasurementSerializer {
    /**
     * Loads the [de.cyface.persistence.model.Measurement] with the provided identifier from the persistence layer serialized and compressed
     * in the [MeasurementSerializer.TRANSFER_FILE_FORMAT_VERSION] format and writes it to a temp file, ready to
     * be transferred.
     *
     * **ATTENTION**: The caller needs to delete the file which is referenced by the returned `FileInputStream`
     * when no longer needed or on program crash!
     *
     * @param measurementId The id of the [de.cyface.persistence.model.Measurement] to load
     * @param persistenceLayer The [PersistenceLayer] to load the `Measurement` data from
     * @return A [File] pointing to a temporary file containing the serialized compressed data for transfer.
     */
    @Throws(CursorIsNullException::class)
    suspend fun writeSerializedCompressed(
        measurementId: Long,
        persistenceLayer: PersistenceLayer<*>
    ): File? {

        // Store the compressed bytes into a temp file to be able to read the byte size for transmission
        val cacheDir = persistenceLayer.cacheDir
        var compressedTempFile: File? = null
        try {
            compressedTempFile =
                withContext(Dispatchers.IO) {
                    File.createTempFile(COMPRESSED_TRANSFER_FILE_PREFIX, ".tmp", cacheDir)
                }
            withContext(Dispatchers.IO) {
                FileOutputStream(compressedTempFile).use { fileOutputStream ->
                    // As we create the DeflaterOutputStream with an FileOutputStream the compressed data is written to file
                    loadSerializedCompressed(
                        fileOutputStream,
                        measurementId,
                        persistenceLayer
                    )
                }
            }
        } catch (e: IOException) {
            if (compressedTempFile != null && compressedTempFile.exists()) {
                Validate.isTrue(compressedTempFile.delete())
            }
            throw IllegalStateException(e)
        }
        return compressedTempFile
    }

    /**
     * Loads the [de.cyface.persistence.model.Attachment] with the provided identifier from the persistence
     * layer serialized in the [MeasurementSerializer.TRANSFER_FILE_FORMAT_VERSION] format and writes
     * it to a temp file, ready to be transferred.
     *
     * **ATTENTION**: The caller needs to delete the file which is referenced by the returned `FileInputStream`
     * when no longer needed or on program crash!
     *
     * @param file The [de.cyface.persistence.model.Attachment] to load
     * @param persistenceLayer The [PersistenceLayer] to load the data from
     * @return A [File] pointing to a temporary file containing the serialized data for transfer.
     */
    @Throws(CursorIsNullException::class)
    suspend fun writeSerializedFile(
        file: de.cyface.persistence.model.Attachment,
        persistenceLayer: PersistenceLayer<*>
    ): File? {

        // Store the compressed bytes into a temp file to be able to read the byte size for transmission
        val cacheDir = persistenceLayer.cacheDir
        var tempFile: File? = null
        try {
            tempFile =
                withContext(Dispatchers.IO) {
                    File.createTempFile(TRANSFER_FILE_PREFIX, ".tmp", cacheDir)
                }
            withContext(Dispatchers.IO) {
                FileOutputStream(tempFile).use { fileOutputStream ->
                    loadSerializedFile(
                        fileOutputStream,
                        file
                    )
                }
            }
        } catch (e: IOException) {
            if (tempFile != null && tempFile.exists()) {
                Validate.isTrue(tempFile.delete())
            }
            throw IllegalStateException(e)
        }
        return tempFile
    }

    /**
     * Writes the [de.cyface.persistence.model.Measurement] with the provided identifier from the persistence layer serialized and compressed
     * in the [MeasurementSerializer.TRANSFER_FILE_FORMAT_VERSION] format, ready to be transferred.
     *
     * The Deflater ZLIB (RFC-1950) compression is used.
     *
     * @param fileOutputStream the `FileInputStream` to write the compressed data to
     * @param measurementId The id of the [de.cyface.persistence.model.Measurement] to load
     * @param persistenceLayer The [PersistenceLayer] to load the `Measurement` data
     * @throws IOException When flushing or closing the [OutputStream] fails
     */
    @Throws(IOException::class)
    private suspend fun loadSerializedCompressed(
        fileOutputStream: OutputStream,
        measurementId: Long,
        persistenceLayer: PersistenceLayer<*>
    ) {
        Log.d(TAG, "loadSerializedCompressed: start")
        val startTimestamp = System.currentTimeMillis()
        // These streams don't throw anything and, thus, it should be enough to close the outermost stream at the end

        // Wrapping the streams with Buffered streams for performance reasons
        val bufferedFileOutputStream = BufferedOutputStream(fileOutputStream)
        val deflaterLevel = 5 // 'cause Steve Jobs said so
        val compressor = Deflater(deflaterLevel, COMPRESSION_NOWRAP)
        // As we wrap the injected outputStream with Deflater the serialized data is automatically compressed
        val deflaterStream = DeflaterOutputStream(bufferedFileOutputStream, compressor)
        BufferedOutputStream(deflaterStream).use { outputStream ->
            // Injecting the outputStream into which the serialized (in this case compressed) data is written to
            loadSerialized(outputStream, measurementId, persistenceLayer)
            outputStream.flush()
        }
        compressor.end()
        Log.d(
            TAG,
            "loadSerializedCompressed: finished after " + (System.currentTimeMillis() - startTimestamp) / 1000
                    + " s with Deflater Level: " + deflaterLevel
        )
    }

    /**
     * Writes the [de.cyface.persistence.model.Attachment] with the provided identifier from the persistence
     * layer serialized in the [MeasurementSerializer.TRANSFER_FILE_FORMAT_VERSION] format, ready to be
     * transferred.
     *
     * No compression is used as we're mostly transferring JPG files right now which are pre-compressed.
     *
     * @param fileOutputStream the `FileInputStream` to write the compressed data to
     * @param file The [de.cyface.persistence.model.Attachment] to load
     * @throws IOException When flushing or closing the [OutputStream] fails
     */
    @Throws(IOException::class)
    private suspend fun loadSerializedFile(
        fileOutputStream: OutputStream,
        file: de.cyface.persistence.model.Attachment
    ) {
        // These streams don't throw anything and, thus, it should be enough to close the outermost stream at the end

        // Wrapping the streams with Buffered streams for performance reasons
        BufferedOutputStream(fileOutputStream).use { outputStream ->
            // Injecting the outputStream into which the serialized data is written to
            TransferFileSerializer.loadSerializedAttachment(outputStream, file)
            outputStream.flush()
        }
    }

    companion object {
        /**
         * The current version of the transferred file. This is always specified by the first two bytes of the file
         * transferred and helps compatible APIs to process data from different client versions.
         */
        const val TRANSFER_FILE_FORMAT_VERSION: Short = 3

        /**
         * Since our current API Level does not support `Short.Bytes`.
         */
        private const val SHORT_BYTES = java.lang.Short.SIZE / java.lang.Byte.SIZE

        /**
         * A constant with the number of bytes for the header of the [.TRANSFER_FILE_FORMAT_VERSION] file.
         */
        const val BYTES_IN_HEADER = SHORT_BYTES

        /**
         * In iOS there are no parameters to set nowrap to false as it is default in Android.
         * In order for the iOS and Android Cyface SDK to be compatible we set nowrap explicitly to true
         *
         *
         * **ATTENTION:** When decompressing in Android you need to pass this parameter to the `Inflater`'s
         * constructor.
         */
        const val COMPRESSION_NOWRAP = true

        /**
         * The prefix of the filename used to store compressed files for serialization.
         */
        private const val COMPRESSED_TRANSFER_FILE_PREFIX = "compressedTransferFile"

        /**
         * The prefix of the filename used to store temp files for serialization.
         */
        private const val TRANSFER_FILE_PREFIX = "transferFile"
    }
}