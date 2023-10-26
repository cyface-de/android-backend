/*
 * Copyright 2023 Cyface GmbH
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

import com.google.protobuf.ByteString
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.model.File
import de.cyface.protos.model.File.FileType
import java.io.IOException
import java.nio.file.Files

/**
 * Serializes a [File] in the [MeasurementSerializer.TRANSFER_FILE_FORMAT_VERSION].
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.10.0
 */
class FileSerializer {
    /**
     * The serialized data.
     */
    private lateinit var file: de.cyface.protos.model.File

    /**
     * Loads the binary of a [File] and parses the [File] info with the binary data.
     *
     * @param file the [File] to load and parse the data for
     */
    fun readFrom(file: File) {
        val builder = de.cyface.protos.model.File.newBuilder()
        require(file.type == FileType.CSV || file.type == FileType.JPG) { "Unsupported type: ${file.type}" }

        // Ensure we only inject bytes from the correct file format version
        // The current version of the file format used to persist File data. It's stored in each File
        // database entry and allows to have stored and process files with different file format versions
        // at the same time.
        // Check fileFormatVersion for the specific type (right now both types only support 1)
        require(file.fileFormatVersion == 1.toShort()) { "Unsupported format version (${file.fileFormatVersion}) for type ${file.type}" }

        builder.timestamp = file.timestamp
        builder.type = file.type
        try {
            val bytes = Files.readAllBytes(file.path)
            builder.bytes = ByteString.copyFrom(bytes)
        } catch (e: IOException) {
            throw IllegalStateException("Could not read file (id ${file.id} at ${file.path}", e)
        }

        this.file = builder.build()
    }

    /**
     * @return the data in the serialized format.
     */
    fun result(): de.cyface.protos.model.File {
        return file
    }
}