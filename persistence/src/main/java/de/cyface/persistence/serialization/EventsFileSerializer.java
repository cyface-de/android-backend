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
import static de.cyface.persistence.Constants.DEFAULT_CHARSET;
import static de.cyface.persistence.Constants.TAG;
import static de.cyface.persistence.model.Event.EventType.LIFECYCLE_PAUSE;
import static de.cyface.persistence.model.Event.EventType.LIFECYCLE_RESUME;
import static de.cyface.persistence.model.Event.EventType.LIFECYCLE_START;
import static de.cyface.persistence.model.Event.EventType.LIFECYCLE_STOP;
import static de.cyface.persistence.model.Event.EventType.MODALITY_TYPE_CHANGE;
import static de.cyface.persistence.serialization.ByteSizes.INT_BYTES;
import static de.cyface.persistence.serialization.ByteSizes.LONG_BYTES;
import static de.cyface.persistence.serialization.ByteSizes.SHORT_BYTES;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.cyface.persistence.Constants;
import de.cyface.persistence.DefaultFileAccess;
import de.cyface.persistence.EventTable;
import de.cyface.persistence.MeasurementContentProviderClient;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.Event;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;

/**
 * This class implements the serialization from {@link Event}s data of a {@link Measurement} stored in a
 * {@code MeasuringPointContentProvider} into the Cyface {@link #EVENT_TRANSFER_FILE_FORMAT_VERSION} binary format. This
 * file starts with a header with the following information:
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
 * @author Armin Schnabel
 * @version 1.0.1
 * @since 5.0.0-beta1
 */
public class EventsFileSerializer {

    /**
     * The current version of the transferred file which contains {@link Event}s. This is always specified by the first
     * two bytes of the file transferred and helps compatible APIs to process data from different client versions.
     */
    public final static short EVENT_TRANSFER_FILE_FORMAT_VERSION = 1;
    /**
     * A constant with the number of bytes for the header of the {@link #EVENT_TRANSFER_FILE_FORMAT_VERSION} file.
     */
    public final static int BYTES_IN_EVENT_FILE_HEADER = SHORT_BYTES + INT_BYTES;

    /**
     * Serializes all the {@link Event}s from the {@link Measurement} identified by the provided
     * {@code measurementIdentifier}.
     *
     * @param eventsCursor A {@link Cursor} returned by a {@link ContentResolver} to load {@code Event}s
     *            from.
     * @return A <code>byte</code> array containing all the data.
     * @throws UnsupportedEncodingException if {@link Constants#DEFAULT_CHARSET} is not supported
     * @throws IOException if a serialized {@code Event} could not be serialized to a {@link Byte} array.
     */
    static byte[] serializeEvents(@NonNull final Cursor eventsCursor) throws IOException {
        // Allocate enough space for all Events
        Log.v(TAG, String.format("Serializing %d Events for synchronization.", eventsCursor.getCount()));

        ByteArrayOutputStream baos = null;
        final byte[] payload;
        try {
            baos = new ByteArrayOutputStream();
            byte[] serializedEvent;
            while (eventsCursor.moveToNext()) {
                // The value string length is dynamic so we need to allocate the buffer for each table row
                @Nullable // Because not all EventTypes use this field
                final String value = eventsCursor.getString(eventsCursor.getColumnIndexOrThrow(EventTable.COLUMN_VALUE));
                final boolean valueIsNull = value == null;
                final byte[] valueBytes = valueIsNull ? new byte[] {} : value.getBytes(DEFAULT_CHARSET);
                final int valueBytesLength = valueBytes.length;
                Validate.isTrue(valueBytesLength <= Short.MAX_VALUE);
                final short shortValueBytesLength = (short)valueBytesLength;
                final String eventTypeString = eventsCursor
                        .getString(eventsCursor.getColumnIndexOrThrow(EventTable.COLUMN_TYPE));
                final Event.EventType eventType = Event.EventType.valueOf(eventTypeString);
                final short serializedEventType = serializeEventType(eventType);

                // Bytes: long timestamp, short event type enum, short value byte length, variable value UTF-8 bytes
                final ByteBuffer buffer = ByteBuffer
                        .allocate(LONG_BYTES + SHORT_BYTES + SHORT_BYTES + valueBytes.length);
                buffer.putLong(eventsCursor.getLong(eventsCursor.getColumnIndexOrThrow(EventTable.COLUMN_TIMESTAMP)));
                buffer.putShort(serializedEventType);
                buffer.putShort(shortValueBytesLength);
                buffer.put(valueBytes);
                serializedEvent = new byte[buffer.capacity()];
                // if we want to switch from write to read mode on the byte buffer we need to .flip() !!
                ((ByteBuffer)buffer.duplicate().clear()).get(serializedEvent);
                baos.write(serializedEvent);
            }
            payload = baos.toByteArray();
        } finally {
            if (baos != null) {
                baos.close();
            }
        }

        return payload;
    }

    /**
     * Converts the {@param eventType} to a {@code Short} number for serialization as defined in
     * {@link #EVENT_TRANSFER_FILE_FORMAT_VERSION}.
     * <p>
     * <b>Attention:</b> Do not break the compatibility in here without increasing the
     * {@code #EVENT_TRANSFER_FILE_FORMAT_VERSION}.
     *
     * @param eventType the value to be converted
     * @return the {@code Short} representation of the {@link Event.EventType}
     */
    private static short serializeEventType(@NonNull final Event.EventType eventType) {
        switch (eventType) {
            case LIFECYCLE_START:
                return 1;
            case LIFECYCLE_STOP:
                return 2;
            case LIFECYCLE_RESUME:
                return 3;
            case LIFECYCLE_PAUSE:
                return 4;
            case MODALITY_TYPE_CHANGE:
                return 5;
            default:
                throw new IllegalArgumentException("Unknown EventType: " + eventType);
        }
    }

    /**
     * Converts the {@param serializedEventType} back to it's actual {@link Event.EventType} as defined in
     * {@link #EVENT_TRANSFER_FILE_FORMAT_VERSION}.
     * <p>
     * <b>Attention:</b> Do not break the compatibility in here without increasing the
     * {@link #EVENT_TRANSFER_FILE_FORMAT_VERSION}.
     *
     * @param serializedEventType the serialized value of the actual {@code Event.EventType}
     * @return the deserialized {@code EventType}
     */
    public static Event.EventType deserializeEventType(final short serializedEventType) {
        switch (serializedEventType) {
            case 1:
                return LIFECYCLE_START;
            case 2:
                return LIFECYCLE_STOP;
            case 3:
                return LIFECYCLE_RESUME;
            case 4:
                return LIFECYCLE_PAUSE;
            case 5:
                return MODALITY_TYPE_CHANGE;
            default:
                throw new IllegalArgumentException("Unknown EventType short representation: " + serializedEventType);
        }
    }

    /**
     * Creates the header field for serialized {@link Event}s of a {@link Measurement} in big endian format for
     * synchronization.
     *
     * (!) Attention: Changes to this format must be discussed with compatible API providers.
     *
     * @param eventsCount Number of {@link Event}s in the serialized {@code Measurement}.
     * @return The header byte array.
     */
    static byte[] serializeEventTransferFileHeader(final int eventsCount) {

        byte[] ret = new byte[6];
        ret[0] = (byte)(EVENT_TRANSFER_FILE_FORMAT_VERSION >> 8);
        ret[1] = (byte)EVENT_TRANSFER_FILE_FORMAT_VERSION;
        ret[2] = (byte)(eventsCount >> 24);
        ret[3] = (byte)(eventsCount >> 16);
        ret[4] = (byte)(eventsCount >> 8);
        ret[5] = (byte)eventsCount;
        return ret;
    }

    /**
     * Implements the core algorithm of loading {@link Event}s of a {@link Measurement} from the
     * {@link PersistenceLayer}
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
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    public static void loadSerializedEvents(@NonNull final BufferedOutputStream bufferedOutputStream,
            @NonNull final MeasurementContentProviderClient loader) throws CursorIsNullException {

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
