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
import static de.cyface.persistence.serialization.EventsFileSerializer.serializeEventTransferFileHeader;
import static de.cyface.persistence.serialization.EventsFileSerializer.serializeEvents;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;

import de.cyface.persistence.DefaultFileAccess;
import de.cyface.persistence.EventTable;
import de.cyface.persistence.MeasurementContentProviderClient;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.utils.CursorIsNullException;

/**
 * {@code FileSerializerStrategy} implementation for {@link EventsFileSerializer#EVENT_TRANSFER_FILE_FORMAT_VERSION}
 * files.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 5.0.0-beta1
 */
public class EventsFileSerializerStrategy implements FileSerializerStrategy {

    @Override
    public void loadSerialized(@NonNull final BufferedOutputStream bufferedOutputStream,
            @NonNull final MeasurementContentProviderClient loader, final long measurementIdentifier,
            @NonNull final PersistenceLayer persistence) throws CursorIsNullException {

        // Logging to collect data on serialization and compression sizes
        long bytesSerialized = 0;

        Cursor eventsCursor = null;
        final byte[] serializedEvents;
        final int eventCount;
        try {
            final Uri eventTableUri = loader.createEventTableUri();
            eventCount = loader.countData(eventTableUri, EventTable.COLUMN_MEASUREMENT_FK);

            // Serialize Events
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            for (int startIndex = 0; startIndex < eventCount; startIndex += DATABASE_QUERY_LIMIT) {
                eventsCursor = loader.loadEvents(startIndex, DATABASE_QUERY_LIMIT);
                outputStream.write(serializeEvents(eventsCursor));
            }
            serializedEvents = outputStream.toByteArray();
            Log.v(TAG, String.format("Serialized %s Events for synchronization.",
                    DefaultFileAccess.humanReadableByteCount(serializedEvents.length, true)));
            bytesSerialized += serializedEvents.length;

        } catch (final RemoteException | IOException e) {
            throw new IllegalStateException(e);
        } finally {
            if (eventsCursor != null) {
                eventsCursor.close();
            }
        }

        // Generate transfer file header
        final byte[] eventTransferFileHeader = serializeEventTransferFileHeader(eventCount);
        Log.v(TAG, String.format("Serialized %s Events binaryHeader for synchronization.",
                DefaultFileAccess.humanReadableByteCount(eventTransferFileHeader.length, true)));
        bytesSerialized += eventTransferFileHeader.length;

        // Assemble bytes to transfer via buffered stream to avoid OOM
        try {
            // The stream must be closed by the called in a finally catch
            bufferedOutputStream.write(eventTransferFileHeader);
            bufferedOutputStream.write(serializedEvents);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }

        try {
            bufferedOutputStream.flush();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        Log.d(TAG,
                String.format("Serialized %s Events", DefaultFileAccess.humanReadableByteCount(bytesSerialized, true)));
    }
}
