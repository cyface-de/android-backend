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

import android.content.Context
import de.cyface.model.Point3D
import de.cyface.persistence.DefaultFileAccess
import de.cyface.persistence.FileAccessLayer
import de.cyface.serializer.Point3DSerializer
import de.cyface.serializer.model.Point3DType
import java.io.File

/**
 * The file format to persist [Point3D]s such as accelerations, rotations and directions.
 *
 * @author Armin Schnabel
 * @version 5.0.1
 * @since 3.0.0
 */
class Point3DFile {
    /**
     * The [File] pointer to the actual file.
     */
    val file: File

    /**
     * The [FileAccessLayer] used to interact with files.
     */
    private var fileAccessLayer: FileAccessLayer? = null

    /**
     * The sensor data type of the [Point3D] data.
     */
    val type: Point3DType

    /**
     * Constructor which actually creates a new [File] in the persistence layer.
     *
     * @param context The [Context] required to access the underlying persistence layer.
     * @param measurementId the identifier of the [de.cyface.persistence.model.Measurement] for which the file is to be created
     * @param type The sensor data type of the `Point3D` data.
     */
    constructor(context: Context, measurementId: Long, type: Point3DType) {
        this.type = type
        fileAccessLayer = DefaultFileAccess()
        file = (fileAccessLayer as DefaultFileAccess).createFile(
            context,
            measurementId,
            folderName(type),
            fileExtension(type)
        )
    }

    /**
     * Constructor to reference an existing [Point3DFile].
     *
     * @param file The already existing file which represents the `Point3DFile`
     * @param type The sensor data type of the [Point3D] data.
     */
    private constructor(file: File, type: Point3DType) {
        this.file = file
        this.type = type
    }

    /**
     * Appends data to a file for a certain measurement.
     *
     * @param dataPoints The data to append.
     */
    fun append(dataPoints: List<Point3D?>?) {
        val data = serialize(dataPoints)
        fileAccessLayer!!.write(file, data, true)
    }

    /**
     * Creates a data representation from some serializable object.
     *
     * @param dataPoints A valid object to create a data in Cyface binary format representation for.
     * @return The data in the Cyface binary format.
     */
    fun serialize(dataPoints: List<Point3D?>?): ByteArray {
        return Point3DSerializer.serialize(dataPoints, type)
    }

    companion object {
        /**
         * The name of the folder containing acceleration data. Separating the files of each `Point3DFile` type should
         * improve performance when searching for files.
         */
        const val ACCELERATIONS_FOLDER_NAME = "accelerations"

        /**
         * The name of the folder containing rotation data. Separating the files of each `Point3DFile` type should
         * improve performance when searching for files.
         */
        const val ROTATIONS_FOLDER_NAME = "rotations"

        /**
         * The name of the folder containing direction data. Separating the files of each `Point3DFile` type should
         * improve performance when searching for files.
         */
        const val DIRECTIONS_FOLDER_NAME = "directions"

        /**
         * The file extension of files containing acceleration data. This makes sure no system-generated files in the
         * [.ACCELERATIONS_FOLDER_NAME] are identified as [Point3DFile]s.
         */
        const val ACCELERATIONS_FILE_EXTENSION = "cyfa"

        /**
         * The file extension of files containing rotation data. This makes sure no system-generated files in the
         * [.ROTATIONS_FOLDER_NAME] are identified as [Point3DFile]s.
         */
        const val ROTATION_FILE_EXTENSION = "cyfr"

        /**
         * The file extension of files containing direction data. This makes sure no system-generated files in the
         * [.DIRECTIONS_FOLDER_NAME] are identified as [Point3DFile]s.
         */
        const val DIRECTION_FILE_EXTENSION = "cyfd"

        /**
         * Loads an existing [Point3DFile] for a specified [de.cyface.persistence.model.Measurement] if it exists.
         *
         * @param context The [Context] required to access the underlying persistence layer.
         * @param fileAccessLayer The [FileAccessLayer] used to access the file;
         * @param measurementId the identifier of the measurement for which the file is to be found
         * @param type The sensor data type of the [Point3D] data.
         * @return the [Point3DFile] link to the file
         * @throws NoSuchFileException if there is no such file
         */
        @JvmStatic
        @Throws(NoSuchFileException::class)
        fun loadFile(
            context: Context, fileAccessLayer: FileAccessLayer,
            measurementId: Long, type: Point3DType
        ): Point3DFile {
            val file = fileAccessLayer.getFilePath(
                context,
                measurementId,
                folderName(type),
                fileExtension(type)
            )
            if (!file.exists()) {
                throw NoSuchFileException("The follow file could not be loaded: " + file.path)
            }
            return Point3DFile(file, type)
        }

        /**
         * @param type the [Point3DType] to get the extension for
         * @return the file extension used for files containing data of this sensor type.
         */
        private fun fileExtension(type: Point3DType): String {
            return when (type) {
                Point3DType.ACCELERATION -> ACCELERATIONS_FILE_EXTENSION
                Point3DType.ROTATION -> ROTATION_FILE_EXTENSION
                Point3DType.DIRECTION -> DIRECTION_FILE_EXTENSION
                else -> throw IllegalArgumentException("Unknown type: $type")
            }
        }

        /**
         * @param type the [Point3DType] to get the folder name for
         * @return the folder name used for files containing data of this sensor type.
         */
        private fun folderName(type: Point3DType): String {
            return when (type) {
                Point3DType.ACCELERATION -> ACCELERATIONS_FOLDER_NAME
                Point3DType.ROTATION -> ROTATIONS_FOLDER_NAME
                Point3DType.DIRECTION -> DIRECTIONS_FOLDER_NAME
                else -> throw IllegalArgumentException("Unknown type: $type")
            }
        }
    }
}