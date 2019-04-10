/*
 * Copyright 2018 Cyface GmbH
 * This file is part of the Cyface SDK for Android.
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.persistence.serialization;

import static de.cyface.persistence.AbstractCyfaceMeasurementTable.DATABASE_QUERY_LIMIT;
import static de.cyface.persistence.Constants.TAG;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import de.cyface.persistence.DefaultFileAccess;
import de.cyface.persistence.FileAccessLayer;
import de.cyface.persistence.GeoLocationsTable;
import de.cyface.persistence.MeasurementContentProviderClient;
import de.cyface.persistence.MeasurementTable;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.Point3d;
import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;

/**
 * This class implements the serialization from data stored in a <code>MeasuringPointContentProvider</code> and
 * Cyface {@link #PERSISTENCE_FILE_FORMAT_VERSION} binary format into the Cyface {@link #TRANSFER_FILE_FORMAT_VERSION}
 * binary format. The later consists of a header with the following information:
 * <ul>
 * <li>2 Bytes format version</li>
 * <li>4 Bytes amount of geo locations</li>
 * <li>4 Bytes amount of accelerations</li>
 * <li>4 Bytes amount of rotations</li>
 * <li>4 Bytes amount of directions</li>
 * <li>All geo locations as: 8 Bytes long timestamp, 8 Bytes double lat, 8 Bytes double lon, 8 Bytes double speed and 4
 * Bytes integer accuracy</li>
 * <li>All accelerations as: 8 Bytes long timestamp, 8 Bytes double x acceleration, 8 Bytes double y acceleration, 8
 * Bytes double z acceleration</li>
 * <li>All rotations as: 8 Bytes long timestamp, 8 Bytes double x rotation, 8 Bytes double y rotation, 8 Bytes double z
 * rotation</li>
 * <li>All directions as: 8 Bytes long timestamp, 8 Bytes double x direction, 8 Bytes double y direction, 8 Bytes double
 * z direction</li>
 * </ul>
 * WARNING: This implementation loads all data from one measurement into memory. So be careful with large measurements.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 4.2.0
 * @since 2.0.0
 */
public final class MeasurementSerializer {

    /**
     * A constant with the number of bytes for one uncompressed {@link Point3d} entry in the Cyface binary format.
     */
    public static final int BYTES_IN_ONE_POINT_3D_ENTRY = ByteSizes.LONG_BYTES + 3 * ByteSizes.DOUBLE_BYTES;
    /**
     * The current version of the transferred file. This is always specified by the first two bytes of the file
     * transferred and helps compatible APIs to process data from different client versions.
     */
    public final static short TRANSFER_FILE_FORMAT_VERSION = 1;
    /**
     * The current version of the file format used to persist {@link Point3d} data.
     * It's stored in each {@link Measurement}'s {@link MeasurementTable} entry and allows to have stored and process
     * measurements and files with different {@code #PERSISTENCE_FILE_FORMAT_VERSION} at the same time.
     */
    public final static short PERSISTENCE_FILE_FORMAT_VERSION = 1;
    /**
     * A constant with the number of bytes for the header of the {@link #TRANSFER_FILE_FORMAT_VERSION} file.
     */
    public final static int BYTES_IN_HEADER = 2 + 4 * 4;
    /**
     * A constant with the number of bytes for one uncompressed geo location entry in the Cyface binary format.
     */
    public final static int BYTES_IN_ONE_GEO_LOCATION_ENTRY = ByteSizes.LONG_BYTES + 3 * ByteSizes.DOUBLE_BYTES
            + ByteSizes.INT_BYTES;
    /**
     * In iOS there are no parameters to set nowrap to false as it is default in Android.
     * In order for the iOS and Android Cyface SDK to be compatible we set nowrap explicitly to true
     * <p>
     * <b>ATTENTION:</b> When decompressing in Android you need to pass this parameter to the {@link Inflater}'s
     * constructor.
     */
    public static final boolean COMPRESSION_NOWRAP = true;
    /**
     * The {@link FileAccessLayer} used to interact with files.
     */
    private final FileAccessLayer fileAccessLayer;

    /**
     * @param fileAccessLayer The {@link FileAccessLayer} used to interact with files.
     */
    public MeasurementSerializer(@NonNull final FileAccessLayer fileAccessLayer) {
        this.fileAccessLayer = fileAccessLayer;
    }

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
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    public File writeSerializedCompressed(@NonNull final MeasurementContentProviderClient loader,
            final long measurementId, @NonNull final PersistenceLayer persistenceLayer) throws CursorIsNullException {

        // Store the compressed bytes into a temp file to be able to read the byte size for transmission
        File compressedTempFile = null;
        final FileOutputStream fileOutputStream;
        try {
            compressedTempFile = File.createTempFile("compressedTransferFile", ".tmp");

            // As we create the DeflaterOutputStream with an FileOutputStream the compressed data is written to file
            fileOutputStream = new FileOutputStream(compressedTempFile);
        } catch (final IOException e) {
            if (compressedTempFile != null && compressedTempFile.exists()) {
                Validate.isTrue(compressedTempFile.delete());
            }

            // No need to close fileOutputStream as it failed to open and is null
            throw new IllegalStateException(e);
        }

        try {
            try {
                loadSerializedCompressed(fileOutputStream, loader, measurementId, persistenceLayer);
            } catch (final IOException e) {
                fileOutputStream.close();

                if (compressedTempFile.exists()) {
                    Validate.isTrue(compressedTempFile.delete());
                }
            }
        } catch (final IOException e) {
            if (compressedTempFile.exists()) {
                Validate.isTrue(compressedTempFile.delete());
            }

            // This catches, among others, the IOException thrown in the close
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
     * @param persistenceLayer The {@link PersistenceLayer} to load the file based {@code Measurement} data from
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     * @throws IOException When flushing or closing the {@link OutputStream} fails
     */
    private void loadSerializedCompressed(@NonNull final OutputStream fileOutputStream,
            @NonNull final MeasurementContentProviderClient loader, final long measurementId,
            @NonNull final PersistenceLayer persistenceLayer) throws CursorIsNullException, IOException {

        // These streams don't throw anything and, thus, it should be enough to close the outermost stream at the end

        // Wrapping the streams with Buffered streams for performance reasons
        final BufferedOutputStream bufferedFileOutputStream = new BufferedOutputStream(fileOutputStream);

        final int DEFLATER_LEVEL = 5; // 'cause Steve Jobs said so
        final Deflater compressor = new Deflater(DEFLATER_LEVEL, COMPRESSION_NOWRAP);
        // As we wrap the injected outputStream with Deflater the serialized data is automatically compressed
        final DeflaterOutputStream deflaterStream = new DeflaterOutputStream(bufferedFileOutputStream, compressor);

        // This architecture catches the IOException thrown by the close() called in the finally without IDE warning
        BufferedOutputStream bufferedDeflaterOutputStream = null;
        try {
            bufferedDeflaterOutputStream = new BufferedOutputStream(deflaterStream);

            // Injecting the outputStream into which the serialized (in this case compressed) data is written to
            loadSerialized(bufferedDeflaterOutputStream, loader, measurementId, persistenceLayer);
            bufferedDeflaterOutputStream.flush();
        } finally {
            if (bufferedDeflaterOutputStream != null) {
                bufferedDeflaterOutputStream.close();
            }
        }
    }

    /**
     * Serializes all the {@link GeoLocation}s from the {@link Measurement} identified by the provided
     * {@code measurementIdentifier}.
     *
     * @param geoLocationsCursor A {@link Cursor} returned by a {@link ContentResolver} to load {@code GeoLocation}s
     *            from.
     * @return A <code>byte</code> array containing all the data.
     */
    private byte[] serializeGeoLocations(final @NonNull Cursor geoLocationsCursor) {
        // Allocate enough space for all geo locations
        Log.v(TAG, String.format("Serializing %d GeoLocations for synchronization.", geoLocationsCursor.getCount()));
        final ByteBuffer buffer = ByteBuffer.allocate(geoLocationsCursor.getCount() * BYTES_IN_ONE_GEO_LOCATION_ENTRY);

        while (geoLocationsCursor.moveToNext()) {
            buffer.putLong(geoLocationsCursor
                    .getLong(geoLocationsCursor.getColumnIndex(GeoLocationsTable.COLUMN_GEOLOCATION_TIME)));
            buffer.putDouble(
                    geoLocationsCursor.getDouble(geoLocationsCursor.getColumnIndex(GeoLocationsTable.COLUMN_LAT)));
            buffer.putDouble(
                    geoLocationsCursor.getDouble(geoLocationsCursor.getColumnIndex(GeoLocationsTable.COLUMN_LON)));
            buffer.putDouble(
                    geoLocationsCursor.getDouble(geoLocationsCursor.getColumnIndex(GeoLocationsTable.COLUMN_SPEED)));
            buffer.putInt(
                    geoLocationsCursor.getInt(geoLocationsCursor.getColumnIndex(GeoLocationsTable.COLUMN_ACCURACY)));
        }

        byte[] payload = new byte[buffer.capacity()];
        ((ByteBuffer)buffer.duplicate().clear()).get(payload);
        // if we want to switch from write to read mode on the byte buffer we need to .flip() !!
        return payload;
    }

    /**
     * Serializes the provided {@link Point3d} points.
     *
     * @return A <code>byte</code> array containing all the data.
     */
    public static byte[] serialize(final @NonNull List<Point3d> dataPoints) {
        Log.v(TAG, String.format("Serializing %d Point3d points!", dataPoints.size()));

        final ByteBuffer buffer = ByteBuffer.allocate(dataPoints.size() * BYTES_IN_ONE_POINT_3D_ENTRY);
        for (final Point3d point : dataPoints) {
            buffer.putLong(point.getTimestamp());
            buffer.putDouble(point.getX());
            buffer.putDouble(point.getY());
            buffer.putDouble(point.getZ());
        }

        byte[] payload = new byte[buffer.capacity()];
        ((ByteBuffer)buffer.duplicate().clear()).get(payload);
        // if we want to switch from write to read mode on the byte buffer we need to .flip() !!
        return payload;
    }

    /**
     * Creates the header field for a serialized {@link Measurement} in big endian format for synchronization.
     *
     * (!) Attention: Changes to this format must be discussed with compatible API providers.
     *
     * @param geoLocationCount Number of {@link GeoLocation} in the serialized {@code Measurement}.
     * @param measurement the {@link Measurement} containing the number of {@link Point3d} points in the serialized
     *            {@code Measurement}.
     * @param accelerationsCount The number of accelerations stored for this {@code Measurement}.
     * @param rotationsCount The number of rotations stored for this {@code Measurement}.
     * @param directionsCount The number of directions stored for this {@code Measurement}.
     * @return The header byte array.
     */
    private byte[] serializeTransferFileHeader(final int geoLocationCount, final Measurement measurement,
            final int accelerationsCount, final int rotationsCount, final int directionsCount) {
        Validate.isTrue(measurement.getFileFormatVersion() == PERSISTENCE_FILE_FORMAT_VERSION, "Unsupported");

        byte[] ret = new byte[18];
        ret[0] = (byte)(TRANSFER_FILE_FORMAT_VERSION >> 8);
        ret[1] = (byte)TRANSFER_FILE_FORMAT_VERSION;
        ret[2] = (byte)(geoLocationCount >> 24);
        ret[3] = (byte)(geoLocationCount >> 16);
        ret[4] = (byte)(geoLocationCount >> 8);
        ret[5] = (byte)geoLocationCount;
        ret[6] = (byte)(accelerationsCount >> 24);
        ret[7] = (byte)(accelerationsCount >> 16);
        ret[8] = (byte)(accelerationsCount >> 8);
        ret[9] = (byte)accelerationsCount;
        ret[10] = (byte)(rotationsCount >> 24);
        ret[11] = (byte)(rotationsCount >> 16);
        ret[12] = (byte)(rotationsCount >> 8);
        ret[13] = (byte)rotationsCount;
        ret[14] = (byte)(directionsCount >> 24);
        ret[15] = (byte)(directionsCount >> 16);
        ret[16] = (byte)(directionsCount >> 8);
        ret[17] = (byte)directionsCount;
        return ret;
    }

    /**
     * Implements the core algorithm of loading a {@link Measurement} with its data from the {@link PersistenceLayer}
     * and serializing it into an array of bytes in the {@link MeasurementSerializer#TRANSFER_FILE_FORMAT_VERSION}
     * format, ready to be compressed.
     * <p>
     * We use the {@param loader} to access the measurement data.
     * <p>
     * We assemble the data using a buffer to avoid OOM exceptions.
     * <p>
     * <b>ATTENTION:</b> The caller must make sure the {@param bufferedOutputStream} is closed when no longer needed
     * or the app crashes.
     *
     * @param bufferedOutputStream The {@link OutputStream} to which the serialized data should be written. Injecting
     *            this allows us to compress the serialized data without the need to write it into a temporary file.
     *            We require a {@link BufferedOutputStream} for performance reasons.
     * @param loader The loader providing access to the {@link ContentProvider} storing all the {@link GeoLocation}s.
     * @param measurementIdentifier The id of the {@code Measurement} to load
     * @param persistence The {@code PersistenceLayer} to access the file based data
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    public void loadSerialized(@NonNull final BufferedOutputStream bufferedOutputStream,
            @NonNull final MeasurementContentProviderClient loader, final long measurementIdentifier,
            @NonNull final PersistenceLayer persistence) throws CursorIsNullException {

        // GeoLocations
        Cursor geoLocationsCursor = null;
        final byte[] serializedGeoLocations;
        final int geoLocationCount;
        try {
            final Uri geoLocationTableUri = loader.createGeoLocationTableUri();
            geoLocationCount = loader.countData(geoLocationTableUri, GeoLocationsTable.COLUMN_MEASUREMENT_FK);

            // Serialize GeoLocations
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            for (int startIndex = 0; startIndex < geoLocationCount; startIndex += DATABASE_QUERY_LIMIT) {
                geoLocationsCursor = loader.loadGeoLocations(startIndex, DATABASE_QUERY_LIMIT);
                outputStream.write(serializeGeoLocations(geoLocationsCursor));
            }
            serializedGeoLocations = outputStream.toByteArray();

        } catch (final RemoteException | IOException e) {
            throw new IllegalStateException(e);
        } finally {
            if (geoLocationsCursor != null) {
                geoLocationsCursor.close();
            }
        }

        // Get already serialized Point3dFiles
        final File accelerationFile = fileAccessLayer.getFilePath(persistence.getContext(), measurementIdentifier,
                Point3dFile.ACCELERATIONS_FOLDER_NAME, Point3dFile.ACCELERATIONS_FILE_EXTENSION);
        final File rotationFile = fileAccessLayer.getFilePath(persistence.getContext(), measurementIdentifier,
                Point3dFile.ROTATIONS_FOLDER_NAME, Point3dFile.ROTATION_FILE_EXTENSION);
        final File directionFile = fileAccessLayer.getFilePath(persistence.getContext(), measurementIdentifier,
                Point3dFile.DIRECTIONS_FOLDER_NAME, Point3dFile.DIRECTION_FILE_EXTENSION);

        // Calculate how many points the files contain (for the binary header)
        int accelerationsCount = 0;
        int rotationsCount = 0;
        int directionsCount = 0;
        // noinspection ConstantConditions // can happen in tests
        if (accelerationFile != null && accelerationFile.exists()) {
            accelerationsCount = (int)(accelerationFile.length() / BYTES_IN_ONE_POINT_3D_ENTRY);
            Validate.isTrue(accelerationsCount * BYTES_IN_ONE_POINT_3D_ENTRY == accelerationFile.length());
        }
        // noinspection ConstantConditions // can happen in tests
        if (rotationFile != null && rotationFile.exists()) {
            rotationsCount = (int)(rotationFile.length() / BYTES_IN_ONE_POINT_3D_ENTRY);
            Validate.isTrue(rotationsCount * BYTES_IN_ONE_POINT_3D_ENTRY == rotationFile.length());
        }
        // noinspection ConstantConditions // can happen in tests
        if (directionFile != null && directionFile.exists()) {
            directionsCount = (int)(directionFile.length() / BYTES_IN_ONE_POINT_3D_ENTRY);
            Validate.isTrue(directionsCount * BYTES_IN_ONE_POINT_3D_ENTRY == directionFile.length());
        }

        // Generate transfer file header
        final Measurement measurement = persistence.loadMeasurement(measurementIdentifier);
        final byte[] transferFileHeader = serializeTransferFileHeader(geoLocationCount, measurement, accelerationsCount,
                rotationsCount, directionsCount);

        // Assemble bytes to transfer via buffered stream to avoid OOM
        try {
            // The stream must be closed by the called in a finally catch
            bufferedOutputStream.write(transferFileHeader);
            bufferedOutputStream.write(serializedGeoLocations);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }

        if (accelerationsCount > 0) {
            Log.v(TAG, String.format("Serializing %s accelerations for synchronization.",
                    DefaultFileAccess.humanReadableByteCount(accelerationFile.length(), true)));
            fileAccessLayer.writeToOutputStream(accelerationFile, bufferedOutputStream);
        }
        if (rotationsCount > 0) {
            Log.v(TAG, String.format("Serializing %s rotations for synchronization.",
                    DefaultFileAccess.humanReadableByteCount(rotationFile.length(), true)));
            fileAccessLayer.writeToOutputStream(rotationFile, bufferedOutputStream);
        }
        if (directionsCount > 0) {
            Log.v(TAG, String.format("Serializing %s directions for synchronization.",
                    DefaultFileAccess.humanReadableByteCount(directionFile.length(), true)));
            fileAccessLayer.writeToOutputStream(directionFile, bufferedOutputStream);
        }

        try {
            bufferedOutputStream.flush();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
