/*
 * Copyright 2020 Cyface GmbH
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
package de.cyface.synchronization;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import de.cyface.utils.Validate;

/**
 * Encapsulates a data file that is transferred together with its meta data.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 5.0.0
 */
class FilePart {
    /**
     * Used to identify log messages from objects of this class.
     */
    private static final String TAG = "de.cyface.sync.FilePart";
    /**
     * The multipart name of the file to transfer.
     */
    private final String fileName;
    /**
     * The name of the part in a multi part request.
     */
    private final String partName;
    /**
     * The file to transfer itself
     */
    private final File file;
    /**
     * The multi part header of this part.
     */
    private final String header;

    /**
     * Creates a new completely initialized object of this class.
     *
     * @param fileName The multipart name of the file to transfer
     * @param file The file to transfer itself
     * @param partName The name of the part in a multi part request
     */
    public FilePart(@NonNull final String fileName, @NonNull final File file, @NonNull final String partName) {
        Validate.notEmpty(fileName);
        Validate.notNull(file);
        Validate.notEmpty(partName);
        Validate.isTrue(file.exists());

        this.fileName = fileName;
        this.file = file;
        this.partName = partName;
        this.header = generateHeaderPart();
    }

    /**
     * @return the length of this part in bytes. This is the sum of the file size and the header size
     */
    long partLength() {
        return file.length() + header.getBytes().length;
    }

    /**
     * Generates a valid Multipart header entry for a file part.
     *
     * @return the generated part entry
     */
    private String generateHeaderPart() {
        return "--" + Http.BOUNDARY + Http.LINE_FEED
                + "Content-Disposition: form-data; name=\"" + partName + "\"; filename=\"" + fileName + "\""
                + Http.LINE_FEED
                + "Content-Type: application/octet-stream" + Http.LINE_FEED + "Content-Transfer-Encoding: binary"
                + Http.LINE_FEED
                + Http.LINE_FEED;
    }

    /**
     * Writes to an {@code OutputStream} in the MultiPart format.
     *
     * @param outputStream the {@code HttpURLConnection} to write to
     * @param progressListener the {@link UploadProgressListener} to inform about the upload progress
     * @return the total number of {@code Byte}s written to the stream
     * @throws IOException when an I/O operation fails
     */
    public long writeTo(@NonNull final BufferedOutputStream outputStream,
            @NonNull final UploadProgressListener progressListener) throws IOException {
        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(file);

            final BufferedInputStream bufferedFileInputStream = new BufferedInputStream(fileInputStream);

            byte[] headerBytes = header.getBytes();
            outputStream.write(headerBytes);
            long bytesWrittenToOutputStream = headerBytes.length;

            bytesWrittenToOutputStream += writeToOutputStream(outputStream, bufferedFileInputStream, file.length(),
                    progressListener);
            outputStream.write(Http.LINE_FEED.getBytes());
            bytesWrittenToOutputStream += Http.LINE_FEED.getBytes().length;

            return bytesWrittenToOutputStream;
        } catch (final FileNotFoundException e) {
            throw new IllegalStateException(e);
        } finally {
            if (fileInputStream != null) {
                fileInputStream.close();
            }
        }
    }

    /**
     * Writes the content of {@param bufferedFileInputStream} to the {@param outputStream} and informs the
     * {@param progressListener} about the progress.
     *
     * @param outputStream the {@code BufferedOutputStream} to write the data to
     * @param bufferedFileInputStream the {@code BufferedInputStream} to read the data from
     * @param binarySize the {code Byte} size of the data to read
     * @param progressListener the {@code UploadProgressListener} to inform about the progress
     * @return the number of {@code Byte}s written to the {@param outputStream}
     * @throws IOException when an I/O operation fails
     */
    private int writeToOutputStream(@NonNull final BufferedOutputStream outputStream,
            @NonNull final BufferedInputStream bufferedFileInputStream, final long binarySize,
            @NonNull final UploadProgressListener progressListener) throws IOException {
        // Create file upload buffer
        // noinspection PointlessArithmeticExpression - makes semantically more sense
        final int maxBufferSize = 1 * 1_024 * 1_024;
        int bytesAvailable, bufferSize;
        byte[] buffer;
        bytesAvailable = bufferedFileInputStream.available();
        bufferSize = Math.min(bytesAvailable, maxBufferSize);
        buffer = new byte[bufferSize];

        // Write the binaries to the OutputStream
        int bytesWritten = 0;
        int bytesRead;
        bytesRead = bufferedFileInputStream.read(buffer, 0, bufferSize);
        while (bytesRead > 0) {
            outputStream.write(buffer, 0, bufferSize);
            bytesWritten += bytesRead; // Here progress is total uploaded bytes
            progressListener.updatedProgress((bytesWritten * 100.0f) / binarySize);

            bytesAvailable = bufferedFileInputStream.available();
            bufferSize = Math.min(bytesAvailable, maxBufferSize);
            bytesRead = bufferedFileInputStream.read(buffer, 0, bufferSize);
        }
        Log.d(TAG, "writeToOutputStream() file -> " + bytesWritten);
        return bytesWritten;
    }
}
