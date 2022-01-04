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
package de.cyface.persistence.serialization;

import static de.cyface.persistence.Constants.TAG;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;

import android.util.Log;

import androidx.annotation.NonNull;

import de.cyface.persistence.MeasurementContentProviderClient;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.Measurement;
import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;

/**
 * This class implements the serialization from data stored in a {@code MeasuringPointContentProvider} and
 * Cyface {@link PersistenceLayer#PERSISTENCE_FILE_FORMAT_VERSION} binary format into the Cyface
 * {@link #TRANSFER_FILE_FORMAT_VERSION}
 * binary format. The later consists of a header with the following information:
 * <p>
 * - 2 Bytes which contain the {@code #TRANSFER_FILE_FORMAT_VERSION}
 * - followed by data in this format: https://github.com/cyface-de/protos (version 1)
 * <p>
 * WARNING: This implementation loads all data from one measurement into memory. So be careful with large measurements.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 8.0.0
 * @since 2.0.0
 */
public final class MeasurementSerializer {

    /**
     * The current version of the transferred file. This is always specified by the first two bytes of the file
     * transferred and helps compatible APIs to process data from different client versions.
     */
    public final static short TRANSFER_FILE_FORMAT_VERSION = 2;
    /**
     * Since our current API Level does not support {@code Short.Bytes}.
     */
    public final static int SHORT_BYTES = Short.SIZE / Byte.SIZE;
    /**
     * A constant with the number of bytes for the header of the {@link #TRANSFER_FILE_FORMAT_VERSION} file.
     */
    public final static int BYTES_IN_HEADER = SHORT_BYTES;
    /**
     * In iOS there are no parameters to set nowrap to false as it is default in Android.
     * In order for the iOS and Android Cyface SDK to be compatible we set nowrap explicitly to true
     * <p>
     * <b>ATTENTION:</b> When decompressing in Android you need to pass this parameter to the {@link Inflater}'s
     * constructor.
     */
    public static final boolean COMPRESSION_NOWRAP = true;
    /**
     * The prefix of the filename used to store compressed files for serialization.
     */
    private static final String COMPRESSED_TRANSFER_FILE_PREFIX = "compressedTransferFile";

    /**
     * Loads the {@link Measurement} with the provided identifier from the persistence layer serialized and compressed
     * in the {@link MeasurementSerializer#TRANSFER_FILE_FORMAT_VERSION} format and writes it to a temp file, ready to
     * be transferred.
     * <p>
     * <b>ATTENTION</b>: The caller needs to delete the file which is referenced by the returned {@code FileInputStream}
     * when no longer needed or on program crash!
     *
     * @param loader {@link MeasurementContentProviderClient} to load the {@code Measurement} data from the database.
     * @param measurementId The id of the {@link Measurement} to load
     * @param persistenceLayer The {@link PersistenceLayer} to load the file based {@code Measurement} data from
     * @return A {@link File} pointing to a temporary file containing the serialized compressed data for transfer.
     * @throws CursorIsNullException If {@code ContentProvider} was inaccessible.
     */
    public File writeSerializedCompressed(@NonNull final MeasurementContentProviderClient loader,
            final long measurementId, @SuppressWarnings("rawtypes") @NonNull final PersistenceLayer persistenceLayer)
            throws CursorIsNullException {

        // Store the compressed bytes into a temp file to be able to read the byte size for transmission
        final File cacheDir = persistenceLayer.getCacheDir();

        File compressedTempFile = null;
        try {
            compressedTempFile = File.createTempFile(COMPRESSED_TRANSFER_FILE_PREFIX, ".tmp", cacheDir);
            try (FileOutputStream fileOutputStream = new FileOutputStream(compressedTempFile)) {
                // As we create the DeflaterOutputStream with an FileOutputStream the compressed data is written to file
                loadSerializedCompressed(fileOutputStream, loader, measurementId, persistenceLayer);
            }
        } catch (final IOException e) {
            if (compressedTempFile != null && compressedTempFile.exists()) {
                Validate.isTrue(compressedTempFile.delete());
            }
            throw new IllegalStateException(e);
        }
        return compressedTempFile;
    }

    /**
     * Writes the {@link Measurement} with the provided identifier from the persistence layer serialized and compressed
     * in the {@link MeasurementSerializer#TRANSFER_FILE_FORMAT_VERSION} format, ready to be transferred.
     * <p>
     * The Deflater ZLIB (RFC-1950) compression is used.
     *
     * @param fileOutputStream the {@link FileInputStream} to write the compressed data to
     * @param loader {@link MeasurementContentProviderClient} to load the {@code Measurement} data from the database.
     * @param measurementId The id of the {@link Measurement} to load
     * @param persistenceLayer The {@link PersistenceLayer} to load the file based {@code Measurement} data
     * @throws CursorIsNullException If {@code ContentProvider} was inaccessible.
     * @throws IOException When flushing or closing the {@link OutputStream} fails
     */
    @SuppressWarnings("SpellCheckingInspection")
    private void loadSerializedCompressed(@NonNull final OutputStream fileOutputStream,
            @NonNull final MeasurementContentProviderClient loader, final long measurementId,
            @SuppressWarnings("rawtypes") @NonNull final PersistenceLayer persistenceLayer)
            throws CursorIsNullException, IOException {

        Log.d(TAG, "loadSerializedCompressed: start");
        final long startTimestamp = System.currentTimeMillis();
        // These streams don't throw anything and, thus, it should be enough to close the outermost stream at the end

        // Wrapping the streams with Buffered streams for performance reasons
        final BufferedOutputStream bufferedFileOutputStream = new BufferedOutputStream(fileOutputStream);

        final int DEFLATER_LEVEL = 5; // 'cause Steve Jobs said so
        final Deflater compressor = new Deflater(DEFLATER_LEVEL, COMPRESSION_NOWRAP);
        // As we wrap the injected outputStream with Deflater the serialized data is automatically compressed
        final DeflaterOutputStream deflaterStream = new DeflaterOutputStream(bufferedFileOutputStream, compressor);

        // This architecture catches the IOException thrown by the close() called in the finally without IDE warning
        try (BufferedOutputStream outputStream = new BufferedOutputStream(deflaterStream)) {
            // Injecting the outputStream into which the serialized (in this case compressed) data is written to
            TransferFileSerializer.loadSerialized(outputStream, loader, measurementId, persistenceLayer);
            outputStream.flush();
        }
        Log.d(TAG, "loadSerializedCompressed: finished after " + ((System.currentTimeMillis() - startTimestamp) / 1000)
                + " s with Deflater Level: " + DEFLATER_LEVEL);
    }
}
