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
import java.io.OutputStream;
import java.util.List;

import com.google.protobuf.ByteString;

import android.content.ContentProvider;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;

import de.cyface.persistence.EventTable;
import de.cyface.persistence.GeoLocationsTable;
import de.cyface.persistence.MeasurementContentProviderClient;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.serialization.proto.EventSerializer;
import de.cyface.persistence.serialization.proto.LocationSerializer;
import de.cyface.protos.model.Event;
import de.cyface.protos.model.LocationRecords;
import de.cyface.protos.model.MeasurementBytes;
import de.cyface.utils.CursorIsNullException;

/**
 * Serializes {@link MeasurementSerializer#TRANSFER_FILE_FORMAT_VERSION} files.
 *
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 5.0.0
 */
public class TransferFileSerializer {

    /**
     * Implements the core algorithm of loading data of a {@link Measurement} from the {@link PersistenceLayer}
     * and serializing it into an array of bytes, ready to be compressed.
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
     * @param persistence The {@code PersistenceLayer} to access file based data
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    public static void loadSerialized(@NonNull final BufferedOutputStream bufferedOutputStream,
            @NonNull final MeasurementContentProviderClient loader, final long measurementIdentifier,
            @NonNull final PersistenceLayer persistence)
            throws CursorIsNullException {

        // Load data from ContentProvider
        final List<Event> events = loadEvents(loader);
        final LocationRecords locationRecords = loadLocations(loader);

        // Using the modified `MeasurementBytes` class to inject the sensor bytes without parsing
        final MeasurementBytes.Builder builder = MeasurementBytes.newBuilder()
                .setFormatVersion(2) // FIXME: use `TRANSFER_FILE_FORMAT_VERSION` when set to 2
                .addAllEvents(events)
                .setLocationRecords(locationRecords);

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

    private static List<Event> loadEvents(MeasurementContentProviderClient loader) throws CursorIsNullException {

        final EventSerializer eventSerializer = new EventSerializer();
        Cursor eventsCursor = null;
        try {
            final Uri eventTableUri = loader.createEventTableUri();
            final int eventCount = loader.countData(eventTableUri, EventTable.COLUMN_MEASUREMENT_FK);
            for (int startIndex = 0; startIndex < eventCount; startIndex += DATABASE_QUERY_LIMIT) {
                eventsCursor = loader.loadEvents(startIndex, DATABASE_QUERY_LIMIT);
                eventSerializer.readFrom(eventsCursor);
            }
        } catch (final RemoteException e) {
            throw new IllegalStateException(e);
        } finally {
            if (eventsCursor != null) {
                eventsCursor.close();
            }
        }
        return eventSerializer.result();
    }

    private static LocationRecords loadLocations(MeasurementContentProviderClient loader) throws CursorIsNullException {

        final LocationSerializer locationSerializer = new LocationSerializer();
        Cursor geoLocationsCursor = null;
        try {
            final Uri geoLocationTableUri = loader.createGeoLocationTableUri();
            final int geoLocationCount = loader.countData(geoLocationTableUri, GeoLocationsTable.COLUMN_MEASUREMENT_FK);
            for (int startIndex = 0; startIndex < geoLocationCount; startIndex += DATABASE_QUERY_LIMIT) {
                geoLocationsCursor = loader.loadGeoLocations(startIndex, DATABASE_QUERY_LIMIT);
                locationSerializer.readFrom(geoLocationsCursor);
            }
        } catch (final RemoteException e) {
            throw new IllegalStateException(e);
        } finally {
            if (geoLocationsCursor != null) {
                geoLocationsCursor.close();
            }
        }
        return locationSerializer.result();
    }
}
