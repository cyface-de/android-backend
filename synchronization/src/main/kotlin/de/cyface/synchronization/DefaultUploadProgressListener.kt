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
package de.cyface.synchronization

import android.util.Log
import de.cyface.synchronization.Constants.TAG
import de.cyface.uploader.UploadProgressListener

/**
 * Upload progress listener which receives the upload progress from an upload (either measurement
 * or attachment file) and calculates the total progress.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.10.0
 */
class DefaultUploadProgressListener(
    private val measurementCount: Int,
    private val measurementIndex: Int,
    private val measurementId: Long,
    private val fileCount: Int,
    private val fileIndex: Int,
    private val progressListener: MutableCollection<ConnectionStatusListener>
) : UploadProgressListener {

    override fun updatedProgress(percent: Float) {
        val filesInMeasurement = 1 + fileCount // 1 for the core measurement file
        val progressPerAttachment = 1.0 / filesInMeasurement

        val progressPerMeasurement = 1.0 / measurementCount.toDouble()

        val totalProgressForCurrentFile = percent * progressPerAttachment * progressPerMeasurement

        val progressBeforeThisMeasurement = measurementIndex.toDouble() * progressPerMeasurement
        val progressWithinCurrentMeasurement =
            fileIndex.toDouble() * progressPerAttachment * progressPerMeasurement

        val totalProgress =
            progressBeforeThisMeasurement + progressWithinCurrentMeasurement + totalProgressForCurrentFile

        for (listener in progressListener) {
            listener.onProgress((totalProgress * 100).toFloat(), measurementId)
        }
    }
}
