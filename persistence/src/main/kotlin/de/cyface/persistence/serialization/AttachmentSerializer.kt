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
import de.cyface.persistence.model.Attachment
import de.cyface.protos.model.File.FileType
import java.io.IOException
import java.nio.file.Files

/**
 * Serializes a [Attachment] in the [MeasurementSerializer.TRANSFER_FILE_FORMAT_VERSION].
 *
 * @author Armin Schnabel
 */
class AttachmentSerializer {
    /**
     * The serialized data.
     */
    private lateinit var serialized: de.cyface.protos.model.File

    /**
     * Loads the binary of a [Attachment] and parses the [Attachment] info with the binary data.
     *
     * @param attachment the [Attachment] to load and parse the data for
     */
    fun readFrom(attachment: Attachment) {
        val builder = de.cyface.protos.model.File.newBuilder()
        require(attachment.type == FileType.CSV || attachment.type == FileType.JSON || attachment.type == FileType.JPG) { "Unsupported type: ${attachment.type}" }

        // Ensure we only inject bytes from the correct file format version
        // The current version of the file format used to persist attachment data. It's stored in each attachment
        // database entry and allows to have stored and process attachments with different file format versions
        // at the same time.
        // Check fileFormatVersion for the specific type (right now both types only support 1)
        require(attachment.fileFormatVersion == 1.toShort()) { "Unsupported format version (${attachment.fileFormatVersion}) for type ${attachment.type}" }

        builder.timestamp = attachment.timestamp
        builder.type = attachment.type
        try {
            val bytes = Files.readAllBytes(attachment.path)
            builder.bytes = ByteString.copyFrom(bytes)
        } catch (e: IOException) {
            throw IllegalStateException("Could not read attachment (id ${attachment.id} at ${attachment.path}", e)
        }

        this.serialized = builder.build()
    }

    /**
     * @return the data in the serialized format.
     */
    fun result(): de.cyface.protos.model.File {
        return serialized
    }
}