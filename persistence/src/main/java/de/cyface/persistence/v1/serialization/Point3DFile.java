/*
 * Copyright 2018-2021 Cyface GmbH
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
package de.cyface.persistence.v1.serialization;

import java.io.File;
import java.util.List;

import android.content.Context;

import androidx.annotation.NonNull;

import de.cyface.model.Point3D;
import de.cyface.persistence.v1.DefaultFileAccess;
import de.cyface.persistence.v1.FileAccessLayer;
import de.cyface.persistence.v1.model.Measurement;
import de.cyface.serializer.Point3DSerializer;
import de.cyface.serializer.model.Point3DType;

/**
 * The file format to persist {@link Point3D}s such as accelerations, rotations and directions.
 *
 * @author Armin Schnabel
 * @version 5.0.0
 * @since 3.0.0
 */
public class Point3DFile {

    /**
     * The name of the folder containing acceleration data. Separating the files of each {@code Point3DFile} type should
     * improve performance when searching for files.
     */
    public static final String ACCELERATIONS_FOLDER_NAME = "accelerations";
    /**
     * The name of the folder containing rotation data. Separating the files of each {@code Point3DFile} type should
     * improve performance when searching for files.
     */
    public static final String ROTATIONS_FOLDER_NAME = "rotations";
    /**
     * The name of the folder containing direction data. Separating the files of each {@code Point3DFile} type should
     * improve performance when searching for files.
     */
    public static final String DIRECTIONS_FOLDER_NAME = "directions";
    /**
     * The file extension of files containing acceleration data. This makes sure no system-generated files in the
     * {@link #ACCELERATIONS_FOLDER_NAME} are identified as {@link Point3DFile}s.
     */
    @SuppressWarnings("SpellCheckingInspection")
    public final static String ACCELERATIONS_FILE_EXTENSION = "cyfa";
    /**
     * The file extension of files containing rotation data. This makes sure no system-generated files in the
     * {@link #ROTATIONS_FOLDER_NAME} are identified as {@link Point3DFile}s.
     */
    @SuppressWarnings("SpellCheckingInspection")
    public final static String ROTATION_FILE_EXTENSION = "cyfr";
    /**
     * The file extension of files containing direction data. This makes sure no system-generated files in the
     * {@link #DIRECTIONS_FOLDER_NAME} are identified as {@link Point3DFile}s.
     */
    @SuppressWarnings("SpellCheckingInspection")
    public final static String DIRECTION_FILE_EXTENSION = "cyfd";
    /**
     * The {@link File} pointer to the actual file.
     */
    private final File file;
    /**
     * The {@link FileAccessLayer} used to interact with files.
     */
    private FileAccessLayer fileAccessLayer;
    /**
     * The sensor data type of the {@link Point3D} data.
     */
    private final Point3DType type;

    /**
     * Constructor which actually creates a new {@link File} in the persistence layer.
     *
     * @param context The {@link Context} required to access the underlying persistence layer.
     * @param measurementId the identifier of the {@link Measurement} for which the file is to be created
     * @param type The sensor data type of the {@code Point3D} data.
     */
    public Point3DFile(@NonNull final Context context, final long measurementId, final Point3DType type) {
        this.type = type;
        this.fileAccessLayer = new DefaultFileAccess();
        this.file = fileAccessLayer.createFile(context, measurementId, folderName(type), fileExtension(type));
    }

    /**
     * Constructor to reference an existing {@link Point3DFile}.
     *
     * @param file The already existing file which represents the {@code Point3DFile}
     * @param type The sensor data type of the {@link Point3D} data.
     */
    private Point3DFile(@NonNull final File file, Point3DType type) {
        this.file = file;
        this.type = type;
    }

    /**
     * Appends data to a file for a certain measurement.
     *
     * @param dataPoints The data to append.
     */
    public void append(final List<? extends Point3D> dataPoints) {
        final byte[] data = serialize(dataPoints);
        fileAccessLayer.write(file, data, true);
    }

    /**
     * Creates a data representation from some serializable object.
     *
     * @param dataPoints A valid object to create a data in Cyface binary format representation for.
     * @return The data in the Cyface binary format.
     */
    public byte[] serialize(final List<? extends Point3D> dataPoints) {
        return Point3DSerializer.serialize(dataPoints, getType());
    }

    /**
     * Loads an existing {@link Point3DFile} for a specified {@link Measurement} if it exists.
     *
     * @param context The {@link Context} required to access the underlying persistence layer.
     * @param fileAccessLayer The {@link FileAccessLayer} used to access the file;
     * @param measurementId the identifier of the measurement for which the file is to be found
     * @param type The sensor data type of the {@link Point3D} data.
     * @return the {@link Point3DFile} link to the file
     * @throws NoSuchFileException if there is no such file
     */
    public static Point3DFile loadFile(@NonNull final Context context, @NonNull FileAccessLayer fileAccessLayer,
                                       final long measurementId, @NonNull final Point3DType type)
            throws NoSuchFileException {

        final File file = fileAccessLayer.getFilePath(context, measurementId, folderName(type), fileExtension(type));
        if (!file.exists()) {
            throw new NoSuchFileException("The follow file could not be loaded: " + file.getPath());
        }

        return new Point3DFile(file, type);
    }

    /**
     * @param type the {@link Point3DType} to get the extension for
     * @return the file extension used for files containing data of this sensor type.
     */
    private static String fileExtension(final Point3DType type) {
        switch (type) {
            case ACCELERATION:
                return ACCELERATIONS_FILE_EXTENSION;
            case ROTATION:
                return ROTATION_FILE_EXTENSION;
            case DIRECTION:
                return DIRECTION_FILE_EXTENSION;
            default:
                throw new IllegalArgumentException("Unknown type: " + type);
        }
    }

    /**
     * @param type the {@link Point3DType} to get the folder name for
     * @return the folder name used for files containing data of this sensor type.
     */
    private static String folderName(final Point3DType type) {
        switch (type) {
            case ACCELERATION:
                return ACCELERATIONS_FOLDER_NAME;
            case ROTATION:
                return ROTATIONS_FOLDER_NAME;
            case DIRECTION:
                return DIRECTIONS_FOLDER_NAME;
            default:
                throw new IllegalArgumentException("Unknown type: " + type);
        }
    }

    public Point3DType getType() {
        return type;
    }

    public File getFile() {
        return file;
    }
}