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
import java.io.File;
import java.io.IOException;

import com.google.protobuf.ByteString;

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
import de.cyface.protos.model.LocationRecords;
import de.cyface.protos.model.MeasurementBytes;
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

        // Using the modified `MeasurementBytes` class to inject the sensor bytes without parsing
        final MeasurementBytes.Builder builder = MeasurementBytes.newBuilder()
                .setFormatVersion(2); // FIXME: use `TRANSFER_FILE_FORMAT_VERSION` when set to 2

        // Load GeoLocations and write to ProtoBuf `builder`
        Cursor geoLocationsCursor = null;
        final LocationSerializer locationSerializer = new LocationSerializer();
        try {
            final Uri geoLocationTableUri = loader.createGeoLocationTableUri();
            final int geoLocationCount = loader.countData(geoLocationTableUri, GeoLocationsTable.COLUMN_MEASUREMENT_FK);
            for (int startIndex = 0; startIndex < geoLocationCount; startIndex += DATABASE_QUERY_LIMIT) {
                geoLocationsCursor = loader.loadGeoLocations(startIndex, DATABASE_QUERY_LIMIT);
                locationSerializer.readFrom(geoLocationsCursor);
            }
            final LocationRecords locationRecords = locationSerializer.result();
            builder.setLocationRecords(locationRecords);
        } catch (final RemoteException e) {
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

        // FIXME: test what happens when one such file does not exist
        // FIXME: check FORMAT VERSION to ensure we inject compatibly bytes and remove the version-bytes
        if (accelerationFile.exists()) {
            Log.v(TAG, String.format("Serializing %s accelerations for synchronization.",
                    humanReadableSize(accelerationFile.length(), true)));
            final byte[] bytes = persistence.getFileAccessLayer().loadBytes(accelerationFile);
            builder.setAccelerations(ByteString.copyFrom(bytes));
        }
        if (rotationFile.exists()) {
            Log.v(TAG, String.format("Serializing %s rotations for synchronization.",
                    humanReadableSize(rotationFile.length(), true)));
            final byte[] bytes = persistence.getFileAccessLayer().loadBytes(rotationFile);
            builder.setRotations(ByteString.copyFrom(bytes));
        }
        if (directionFile.exists()) {
            Log.v(TAG, String.format("Serializing %s directions for synchronization.",
                    humanReadableSize(directionFile.length(), true)));
            final byte[] bytes = persistence.getFileAccessLayer().loadBytes(directionFile);
            builder.setDirections(ByteString.copyFrom(bytes));
        }

        // Assemble bytes to transfer via buffered stream to avoid OOM
        final Measurement measurement = persistence.loadMeasurement(measurementIdentifier);
        final byte[] transferFileHeader = MeasurementSerializer.transferFileHeader(measurement);
        final byte[] measurementBytes = builder.build().toByteArray();
        try {
            // The stream must be closed by the called in a finally catch
            bufferedOutputStream.write(transferFileHeader);
            bufferedOutputStream.write(measurementBytes);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
        try {
            bufferedOutputStream.flush();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        Log.d(TAG, String.format("Serialized %s",
                humanReadableSize(transferFileHeader.length + measurementBytes.length, true)));
    }
}
