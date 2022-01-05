/*
 * Copyright 2021 Cyface GmbH
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
package de.cyface.persistence;

import static de.cyface.persistence.Constants.TAG;
import static de.cyface.serializer.DataSerializable.humanReadableSize;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import de.cyface.utils.Validate;

/**
 * Implementation of the {@link FileAccessLayer} which accesses the real file system.
 *
 * @author Armin Schnabel
 * @version 4.0.0
 * @since 3.0.0
 */
public final class DefaultFileAccess implements FileAccessLayer {

    @Override
    public void writeToOutputStream(@NonNull final File file,
            @NonNull final BufferedOutputStream bufferedOutputStream) {

        final FileInputStream fileInputStream;
        int bytesRead, bytesAvailable, bufferSize;
        byte[] buffer;
        // noinspection PointlessArithmeticExpression - makes semantically more sense
        int maxBufferSize = 1 * 1024 * 1024; // from sample code, optimize if performance problems
        try {
            fileInputStream = new FileInputStream(file);
            try {
                bytesAvailable = fileInputStream.available();
                bufferSize = Math.min(bytesAvailable, maxBufferSize);
                buffer = new byte[bufferSize];

                bytesRead = fileInputStream.read(buffer, 0, bufferSize);
                while (bytesRead > 0) {
                    bufferedOutputStream.write(buffer, 0, bufferSize);
                    bytesAvailable = fileInputStream.available();
                    bufferSize = Math.min(bytesAvailable, maxBufferSize);
                    bytesRead = fileInputStream.read(buffer, 0, bufferSize);
                }
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            } finally {
                fileInputStream.close();
            }
        } catch (final IOException e) {
            // This catches, among others, the IOException thrown in the close
            throw new IllegalStateException(e);
        }
    }

    @Override
    @NonNull
    public byte[] loadBytes(File file) {
        Validate.isTrue(file.exists());
        final byte[] bytes = new byte[(int)file.length()];

        try {
            try (final BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(file))) {
                try (final DataInputStream inputStream = new DataInputStream(bufferedInputStream)) {
                    try {
                        inputStream.readFully(bytes);
                        Log.d(Constants.TAG, "Read " + humanReadableSize(bytes.length, true) + " (from "
                                + file.getName() + ")");
                    } finally {
                        inputStream.close();
                        bufferedInputStream.close();
                    }
                }
                return bytes;
            }
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to read file.");
        }
    }

    @Override
    @NonNull
    public File getFolderPath(@NonNull Context context, @NonNull String folderName) {
        return new File(context.getFilesDir() + File.separator + folderName);
    }

    @Override
    @NonNull
    public File getFilePath(@NonNull Context context, long measurementId, String folderName, String fileExtension) {
        final File folder = getFolderPath(context, folderName);
        return new File(folder.getPath() + File.separator + measurementId + "." + fileExtension);
    }

    @Override
    @NonNull
    public File createFile(@NonNull Context context, long measurementId, String folderName, String fileExtension) {
        final File file = getFilePath(context, measurementId, folderName, fileExtension);
        if (file.exists()) {
            // Before we threw an Exception which we saw in PlayStore. This happens because we call this method
            // also when we resume a measurement in which case the files usually already exist.
            Log.d(TAG, "CreateFile ignored as it already exists (probably resuming): " + file.getPath());
            return file;
        }

        try {
            if (!file.createNewFile()) {
                throw new IOException("Failed to createFile: " + file.getPath());
            }
        } catch (final IOException e) {
            throw new IllegalStateException("Failed createFile: " + file.getPath());
        }
        return file;
    }

    @Override
    public void write(File file, byte[] data, boolean append) {
        Validate.isTrue(file.exists(), "Failed to write to file as it does not exist: " + file.getPath());
        try {
            try (final BufferedOutputStream outputStream = new BufferedOutputStream(
                    new FileOutputStream(file, append))) {
                try {
                    outputStream.write(data);
                } finally {
                    outputStream.close();
                }
            }
        } catch (final IOException e) {
            // TODO [MOV-566]: Soft catch the no space left scenario
            throw new IllegalStateException("Failed to append data to file. Is there space left on the device?");
        }
    }
}
