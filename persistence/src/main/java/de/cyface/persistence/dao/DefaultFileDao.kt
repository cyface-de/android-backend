/*
 * Copyright 2021-2023 Cyface GmbH
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
package de.cyface.persistence.dao

import android.content.Context
import android.util.Log
import de.cyface.persistence.Constants.TAG
import de.cyface.serializer.DataSerializable
import de.cyface.utils.Validate
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.min

/**
 * Implementation of the [FileDao] which accesses the real file system.
 *
 * @author Armin Schnabel
 * @version 4.0.0
 * @since 3.0.0
 */
class DefaultFileDao : FileDao {
    override fun writeToOutputStream(
        file: File,
        bufferedOutputStream: BufferedOutputStream
    ) {
        val fileInputStream: FileInputStream
        var bytesRead: Int
        var bytesAvailable: Int
        var bufferSize: Int
        val buffer: ByteArray
        // noinspection PointlessArithmeticExpression - makes semantically more sense
        val maxBufferSize = 1 * 1024 * 1024 // from sample code, optimize if performance problems
        try {
            fileInputStream = FileInputStream(file)
            try {
                bytesAvailable = fileInputStream.available()
                bufferSize = min(bytesAvailable, maxBufferSize)
                buffer = ByteArray(bufferSize)
                bytesRead = fileInputStream.read(buffer, 0, bufferSize)
                while (bytesRead > 0) {
                    bufferedOutputStream.write(buffer, 0, bufferSize)
                    bytesAvailable = fileInputStream.available()
                    bufferSize = min(bytesAvailable, maxBufferSize)
                    bytesRead = fileInputStream.read(buffer, 0, bufferSize)
                }
            } catch (e: IOException) {
                throw IllegalStateException(e)
            } finally {
                fileInputStream.close()
            }
        } catch (e: IOException) {
            // This catches, among others, the IOException thrown in the close
            throw IllegalStateException(e)
        }
    }

    override fun loadBytes(file: File?): ByteArray {
        Validate.isTrue(file!!.exists())
        val bytes = ByteArray(file.length().toInt())
        try {
            BufferedInputStream(FileInputStream(file)).use { bufferedInputStream ->
                DataInputStream(bufferedInputStream).use { inputStream ->
                    try {
                        inputStream.readFully(bytes)
                    } finally {
                        inputStream.close()
                        bufferedInputStream.close()
                    }
                }
                return bytes
            }
        } catch (e: IOException) {
            throw IllegalStateException("Failed to read file.")
        }
    }

    override fun getFolderPath(context: Context, folderName: String): File {
        return File(context.filesDir.toString() + File.separator + folderName)
    }

    override fun getFilePath(
        context: Context,
        measurementId: Long,
        folderName: String?,
        fileExtension: String?
    ): File {
        val folder = getFolderPath(context, folderName!!)
        return File(folder.path + File.separator + measurementId + "." + fileExtension)
    }

    override fun createFile(
        context: Context,
        measurementId: Long,
        folderName: String?,
        fileExtension: String?
    ): File {
        val file = getFilePath(context, measurementId, folderName, fileExtension)
        if (file.exists()) {
            // Before we threw an Exception which we saw in PlayStore. This happens because we call this method
            // also when we resume a measurement in which case the files usually already exist.
            Log.d(TAG, "CreateFile ignored as it already exists (probably resuming): " + file.path)
            return file
        }
        try {
            if (!file.createNewFile()) {
                throw IOException("Failed to createFile: " + file.path)
            }
        } catch (e: IOException) {
            throw IllegalStateException("Failed createFile: " + file.path)
        }
        return file
    }

    override fun write(file: File?, data: ByteArray?, append: Boolean) {
        Validate.isTrue(file!!.exists(), "Failed to write to file as it does not exist: " + file.path)
        try {
            BufferedOutputStream(
                FileOutputStream(file, append)
            ).use { outputStream ->
                outputStream.write(data)
            }
        } catch (e: IOException) {
            // TODO [MOV-566]: Soft catch the no space left scenario
            throw IllegalStateException("Failed to append data to file. Is there space left on the device?")
        }
    }
}