/*
 * Copyright 2019 Cyface GmbH
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

import static de.cyface.persistence.AbstractCyfaceMeasurementTable.DATABASE_QUERY_LIMIT;
import static de.cyface.persistence.Constants.TAG;
import static de.cyface.persistence.serialization.MeasurementSerializer.BYTES_IN_ONE_POINT_3D_ENTRY;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;

import de.cyface.persistence.DefaultFileAccess;
import de.cyface.persistence.GeoLocationsTable;
import de.cyface.persistence.MeasurementContentProviderClient;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.Measurement;
import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;

/**
 * {@code FileSerializerStrategy} implementation for {@link MeasurementSerializer#TRANSFER_FILE_FORMAT_VERSION} files.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 5.0.0-beta1
 */
public class MeasurementFileSerializerStrategy implements FileSerializerStrategy {

    @Override
    public void loadSerialized(@NonNull final BufferedOutputStream bufferedOutputStream,
            @NonNull final MeasurementContentProviderClient loader, final long measurementIdentifier,
            @NonNull final PersistenceLayer persistence)
            throws CursorIsNullException {

        // Logging to collect data on serialization and compression sizes
        long bytesSerialized = 0;

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
                outputStream.write(MeasurementSerializer.serializeGeoLocations(geoLocationsCursor));
            }
            serializedGeoLocations = outputStream.toByteArray();
            Log.v(TAG, String.format("Serialized %s geoLocations for synchronization.",
                    DefaultFileAccess.humanReadableByteCount(serializedGeoLocations.length, true)));
            bytesSerialized += serializedGeoLocations.length;

        } catch (final RemoteException | IOException e) {
            throw new IllegalStateException(e);
        } finally {
            if (geoLocationsCursor != null) {
                geoLocationsCursor.close();
            }
        }

        // Get already serialized Point3dFiles
        final File accelerationFile = persistence.getFileAccessLayer().getFilePath(persistence.getContext(),
                measurementIdentifier,
                Point3dFile.ACCELERATIONS_FOLDER_NAME, Point3dFile.ACCELERATIONS_FILE_EXTENSION);
        final File rotationFile = persistence.getFileAccessLayer().getFilePath(persistence.getContext(),
                measurementIdentifier,
                Point3dFile.ROTATIONS_FOLDER_NAME, Point3dFile.ROTATION_FILE_EXTENSION);
        final File directionFile = persistence.getFileAccessLayer().getFilePath(persistence.getContext(),
                measurementIdentifier,
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
        final byte[] transferFileHeader = MeasurementSerializer.serializeTransferFileHeader(geoLocationCount,
                measurement, accelerationsCount,
                rotationsCount, directionsCount);
        Log.v(TAG, String.format("Serialized %s binaryHeader for synchronization.",
                DefaultFileAccess.humanReadableByteCount(transferFileHeader.length, true)));
        bytesSerialized += transferFileHeader.length;

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
            bytesSerialized += accelerationFile.length();
            persistence.getFileAccessLayer().writeToOutputStream(accelerationFile, bufferedOutputStream);
        }
        if (rotationsCount > 0) {
            Log.v(TAG, String.format("Serializing %s rotations for synchronization.",
                    DefaultFileAccess.humanReadableByteCount(rotationFile.length(), true)));
            bytesSerialized += rotationFile.length();
            persistence.getFileAccessLayer().writeToOutputStream(rotationFile, bufferedOutputStream);
        }
        if (directionsCount > 0) {
            Log.v(TAG, String.format("Serializing %s directions for synchronization.",
                    DefaultFileAccess.humanReadableByteCount(directionFile.length(), true)));
            bytesSerialized += directionFile.length();
            persistence.getFileAccessLayer().writeToOutputStream(directionFile, bufferedOutputStream);
        }

        try {
            bufferedOutputStream.flush();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        Log.d(TAG, String.format("Serialized %s",
                DefaultFileAccess.humanReadableByteCount(bytesSerialized, true)));
    }
}
