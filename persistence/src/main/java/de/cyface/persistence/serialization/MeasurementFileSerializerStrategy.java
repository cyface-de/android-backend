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
import static de.cyface.persistence.DefaultFileAccess.humanReadableSize;
import static de.cyface.persistence.serialization.Point3dFile.ACCELERATIONS_FILE_EXTENSION;
import static de.cyface.persistence.serialization.Point3dFile.ACCELERATIONS_FOLDER_NAME;
import static de.cyface.persistence.serialization.Point3dFile.DIRECTIONS_FOLDER_NAME;
import static de.cyface.persistence.serialization.Point3dFile.DIRECTION_FILE_EXTENSION;
import static de.cyface.persistence.serialization.Point3dFile.ROTATIONS_FOLDER_NAME;
import static de.cyface.persistence.serialization.Point3dFile.ROTATION_FILE_EXTENSION;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;

import de.cyface.persistence.GeoLocationsTable;
import de.cyface.persistence.MeasurementContentProviderClient;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.serialization.proto.LocationSerializer;
import de.cyface.utils.CursorIsNullException;

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
                outputStream.write(LocationSerializer.serialize(geoLocationsCursor));
            }
            serializedGeoLocations = outputStream.toByteArray();
            Log.v(TAG, String.format("Serialized %s geoLocations for synchronization.",
                    humanReadableSize(serializedGeoLocations.length, true)));
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
                measurementIdentifier, ACCELERATIONS_FOLDER_NAME, ACCELERATIONS_FILE_EXTENSION);
        final File rotationFile = persistence.getFileAccessLayer().getFilePath(persistence.getContext(),
                measurementIdentifier, ROTATIONS_FOLDER_NAME, ROTATION_FILE_EXTENSION);
        final File directionFile = persistence.getFileAccessLayer().getFilePath(persistence.getContext(),
                measurementIdentifier, DIRECTIONS_FOLDER_NAME, DIRECTION_FILE_EXTENSION);

        // Generate transfer file header
        final Measurement measurement = persistence.loadMeasurement(measurementIdentifier);
        final byte[] transferFileHeader = MeasurementSerializer.transferFileHeader(measurement);
        Log.v(TAG, String.format("Serialized %s binaryHeader for synchronization.",
                humanReadableSize(transferFileHeader.length, true)));
        bytesSerialized += transferFileHeader.length;

        // Assemble bytes to transfer via buffered stream to avoid OOM
        try {
            // The stream must be closed by the called in a finally catch
            bufferedOutputStream.write(transferFileHeader);
            bufferedOutputStream.write(serializedGeoLocations);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }

        // FIXME: test what happens when one such file does not exist
        if (accelerationFile.exists()) {
            Log.v(TAG, String.format("Serializing %s accelerations for synchronization.",
                    humanReadableSize(accelerationFile.length(), true)));
            bytesSerialized += accelerationFile.length();
            persistence.getFileAccessLayer().writeToOutputStream(accelerationFile, bufferedOutputStream);
        }
        if (rotationFile.exists()) {
            Log.v(TAG, String.format("Serializing %s rotations for synchronization.",
                    humanReadableSize(rotationFile.length(), true)));
            bytesSerialized += rotationFile.length();
            persistence.getFileAccessLayer().writeToOutputStream(rotationFile, bufferedOutputStream);
        }
        if (directionFile.exists()) {
            Log.v(TAG, String.format("Serializing %s directions for synchronization.",
                    humanReadableSize(directionFile.length(), true)));
            bytesSerialized += directionFile.length();
            persistence.getFileAccessLayer().writeToOutputStream(directionFile, bufferedOutputStream);
        }

        try {
            bufferedOutputStream.flush();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        Log.d(TAG, String.format("Serialized %s", humanReadableSize(bytesSerialized, true)));
    }
}
