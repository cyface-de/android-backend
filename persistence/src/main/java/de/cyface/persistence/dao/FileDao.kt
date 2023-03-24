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
import java.io.BufferedOutputStream
import java.io.File

/**
 * Interface access [File]s. This helps to mock the file access away during testing.
 *
 * @author Amin Schnabel
 * @version 1.1.0
 * @since 3.0.0
 */
interface FileDao {
    /**
     * Writes the content of the {@param file} to the provided {@param bufferedOutputStream} using a buffer for
     * performance reasons.
     *
     * @param file the [File] which content should be written to the output stream
     * @param bufferedOutputStream the [BufferedOutputStream] the {@param file} content should be written to
     */
    fun writeToOutputStream(file: File, bufferedOutputStream: BufferedOutputStream)

    /**
     * Loads the bytes form a file.
     *
     *
     * This code is only used in the test to deserialize the serialized data for testing.
     * However, it's in here to be able to inject a mocked file access layer in the tests.
     *
     * @param file The [File] to load the bytes from.
     * @return The bytes.
     */
    fun loadBytes(file: File?): ByteArray?

    /**
     * Returns the path of the parent directory containing all [de.cyface.persistence.serialization.Point3DFile]s of a specified type.
     * This directory is deleted when the app is uninstalled and can only be accessed by the app.
     *
     * @param context The [Context] required to access the underlying persistence layer.
     * @param folderName The folder name defining the type of point 3d
     */
    fun getFolderPath(context: Context, folderName: String): File

    /**
     * Generates the path to a specific binary file.
     *
     * @param context The [Context] required to access the underlying persistence layer.
     * @param measurementId the identifier of the measurement for which the file is to be found
     * @param folderName The folder name defining the point 3d type of the file
     * @param fileExtension the extension of the file type
     * @return The [File]
     */
    fun getFilePath(
        context: Context, measurementId: Long, folderName: String?,
        fileExtension: String?
    ): File

    /**
     * Creates a [File] for Cyface binary data.
     *
     * @param context The [Context] required to access the underlying persistence layer.
     * @param measurementId the identifier of the measurement for which the file is to be found
     * @param folderName The folder name defining the point 3d type of the file
     * @param fileExtension the extension of the file type
     * @return The create `File`.
     * @throws IllegalStateException when the measurement folder does not exist.
     */
    fun createFile(
        context: Context, measurementId: Long, folderName: String?,
        fileExtension: String?
    ): File

    /**
     * Method to write data to a file.
     *
     * @param file The [File] referencing the path to write the data to
     * @param data The bytes to write to the file
     * @param append True if the data should be appended to an existing file.
     */
    fun write(file: File?, data: ByteArray?, append: Boolean)
}