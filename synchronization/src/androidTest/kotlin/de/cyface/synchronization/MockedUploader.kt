/*
 * Copyright 2023-2024 Cyface GmbH
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
package de.cyface.synchronization

import de.cyface.uploader.Result
import de.cyface.uploader.UploadProgressListener
import de.cyface.uploader.Uploader
import de.cyface.uploader.model.Attachment
import de.cyface.uploader.model.Measurement
import de.cyface.uploader.model.Uploadable
import java.io.File
import java.net.MalformedURLException
import java.net.URL

/**
 * An [Uploader] that does not actually connect to the server, for testing.
 *
 * @author Armin Schnabel
 */
internal class MockedUploader : Uploader {

    override fun measurementsEndpoint(uploadable: Uploadable): URL {
        return try {
            URL("https://mocked.cyface.de/api/v123/measurements")
        } catch (e: MalformedURLException) {
            throw IllegalStateException(e)
        }
    }

    override fun attachmentsEndpoint(uploadable: Uploadable): URL {
        return try {
            URL("https://mocked.cyface.de/api/v123/measurements/did/mid/attachments")
        } catch (e: MalformedURLException) {
            throw IllegalStateException(e)
        }
    }

    override fun uploadMeasurement(
        jwtToken: String,
        uploadable: Measurement,
        file: File,
        progressListener: UploadProgressListener
    ): Result {
        progressListener.updatedProgress(1.0f) // 100%
        return Result.UPLOAD_SUCCESSFUL
    }

    override fun uploadAttachment(
        jwtToken: String,
        uploadable: Attachment,
        file: File,
        fileName: String,
        progressListener: UploadProgressListener
    ): Result {
        progressListener.updatedProgress(1.0f) // 100%
        return Result.UPLOAD_SUCCESSFUL
    }
}
