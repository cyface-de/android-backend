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
package de.cyface.synchronization;

import static android.os.Build.VERSION_CODES.KITKAT;
import static de.cyface.persistence.Constants.DEFAULT_CHARSET;
import static de.cyface.persistence.serialization.ByteSizes.LONG_BYTES;
import static de.cyface.persistence.serialization.ByteSizes.SHORT_BYTES;
import static de.cyface.persistence.serialization.EventsFileSerializer.BYTES_IN_EVENT_FILE_HEADER;
import static de.cyface.persistence.serialization.EventsFileSerializer.deserializeEventType;
import static de.cyface.synchronization.TestUtils.AUTHORITY;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;

import de.cyface.persistence.DefaultFileAccess;
import de.cyface.persistence.EventTable;
import de.cyface.persistence.MeasurementContentProviderClient;
import de.cyface.persistence.Utils;
import de.cyface.persistence.model.Event;
import de.cyface.persistence.model.Modality;
import de.cyface.persistence.serialization.EventsFileSerializer;
import de.cyface.persistence.serialization.MeasurementSerializer;
import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;

/**
 *
 * @author Armin Schnabel
 * @version 1.0.1
 * @since 5.0.0-beta1
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = KITKAT) // Because this test is not yet implemented for the code used on newer devices
public class EventsFileSerializerTest {

    /**
     * Used to mock Android API objects.
     */
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();
    /**
     * A mock loader, not accessing any database
     */
    @Mock
    private MeasurementContentProviderClient loader;
    /**
     * A mocked cursor for {@code Event}s.
     */
    @Mock
    private Cursor eventsCursor;
    private final static int SAMPLE_EVENTS = 5;
    private final static String SAMPLE_STRING_VALUE = Modality.UNKNOWN.getDatabaseIdentifier();
    private final static long SAMPLE_LONG_VALUE = 1L;
    /**
     * The number of bytes of a serialized Event entry of each {@link #SAMPLE_EVENTS} in the
     * {@link de.cyface.persistence.serialization.EventsFileSerializer#EVENT_TRANSFER_FILE_FORMAT_VERSION} file
     * <p>
     * The dynamic {@link Event#getValue()} size is {@code 7} is SAMPLE_STRING_VALUE.getBytes(DEFAULT_CHARSET).length
     */
    private final static long SERIALIZED_SAMPLE_EVENT_SIZE = LONG_BYTES + SHORT_BYTES + SHORT_BYTES + 7;

    @Before
    public void setUp() throws RemoteException, CursorIsNullException {

        // Mock Event database access
        Uri eventUri = Utils.getEventUri(AUTHORITY);
        when(loader.createEventTableUri()).thenReturn(eventUri);
        when(loader.countData(eventUri, EventTable.COLUMN_MEASUREMENT_FK)).thenReturn(SAMPLE_EVENTS);
        when(loader.loadEvents(anyInt(), anyInt())).thenReturn(eventsCursor);

        // Mock insert of 5 Events
        when(eventsCursor.moveToNext()).thenReturn(true).thenReturn(true).thenReturn(true).thenReturn(true)
                .thenReturn(true).thenReturn(false);

        // Mock load sample Event data
        int sampleColumnIndex = 0;
        int sampleTypeColumnIndex = 1;
        when(eventsCursor.getColumnIndex(EventTable.COLUMN_TYPE)).thenReturn(sampleTypeColumnIndex);
        when(eventsCursor.getColumnIndex(EventTable.COLUMN_VALUE)).thenReturn(sampleColumnIndex);
        when(eventsCursor.getString(sampleTypeColumnIndex))
                .thenReturn(Event.EventType.MODALITY_TYPE_CHANGE.getDatabaseIdentifier());
        when(eventsCursor.getString(sampleColumnIndex))
                .thenReturn(Modality.UNKNOWN.getDatabaseIdentifier());
        when(eventsCursor.getLong(sampleColumnIndex)).thenReturn(SAMPLE_LONG_VALUE);
    }

    /**
     * Tests if serialization of {@link Event}s is successful.
     */
    @Test
    public void testSerializeEvents() throws IOException, CursorIsNullException {

        // Arrange
        final File serializedFile = File.createTempFile("serializedTestEventsFile", ".tmp");
        try {
            final FileOutputStream fileOutputStream = new FileOutputStream(serializedFile);
            final BufferedOutputStream bufferedFileOutputStream = new BufferedOutputStream(fileOutputStream);

            // Act
            EventsFileSerializer.loadSerializedEvents(bufferedFileOutputStream, loader);

            // Assert
            assertThat(serializedFile.exists(), is(true));
            assertThat(serializedFile.length(),
                    is(equalTo(BYTES_IN_EVENT_FILE_HEADER + SAMPLE_EVENTS * SERIALIZED_SAMPLE_EVENT_SIZE)));
        } finally {
            if (serializedFile.exists()) {
                Validate.isTrue(serializedFile.delete());
            }
        }
    }

    /**
     * Tests whether deserialization of {@link Event}s is successful.
     */
    @Test
    public void testDeserializeEvents() throws CursorIsNullException, IOException {

        // Arrange
        final File serializedFile = File.createTempFile("serializedTestEventsFile", ".tmp");
        try {
            final FileOutputStream fileOutputStream = new FileOutputStream(serializedFile);
            final BufferedOutputStream bufferedFileOutputStream = new BufferedOutputStream(fileOutputStream);
            EventsFileSerializer.loadSerializedEvents(bufferedFileOutputStream, loader);
            assertThat(serializedFile.length(),
                    is(equalTo(BYTES_IN_EVENT_FILE_HEADER + SAMPLE_EVENTS * SERIALIZED_SAMPLE_EVENT_SIZE)));

            // Act & Assert
            deserializeEventsAndCheck(new DefaultFileAccess().loadBytes(serializedFile));
        } finally {
            if (serializedFile.exists()) {
                Validate.isTrue(serializedFile.delete());
            }
        }
    }

    private void deserializeEventsAndCheck(byte[] uncompressedEventsFileBytes) throws UnsupportedEncodingException {
        EventsFileData eventsFileData = deserializeEventsTransferFile(uncompressedEventsFileBytes);

        // Check header
        assertThat(eventsFileData.transferFileFormat, is(MeasurementSerializer.TRANSFER_FILE_FORMAT_VERSION));
        assertThat(eventsFileData.events.size(), is(SAMPLE_EVENTS));

        // check values
        for (int i = 0; i < SAMPLE_EVENTS; i++) {
            assertThat(eventsFileData.events.get(i).getTimestamp(), is(SAMPLE_LONG_VALUE));
            assertThat(eventsFileData.events.get(i).getType().getDatabaseIdentifier(),
                    is(Event.EventType.MODALITY_TYPE_CHANGE.getDatabaseIdentifier()));
            assertThat(eventsFileData.events.get(i).getValue(), is(SAMPLE_STRING_VALUE));
        }
    }

    private EventsFileData deserializeEventsTransferFile(byte[] uncompressedEventsFileBytes)
            throws UnsupportedEncodingException {

        ByteBuffer buffer = ByteBuffer.wrap(uncompressedEventsFileBytes);
        short formatVersion = buffer.order(ByteOrder.BIG_ENDIAN).getShort(0);
        int numberOfEvents = buffer.order(ByteOrder.BIG_ENDIAN).getInt(2);
        assertThat(numberOfEvents, is(equalTo(SAMPLE_EVENTS)));
        // noinspection UnnecessaryLocalVariable // for readability
        int beginOfEventsIndex = BYTES_IN_EVENT_FILE_HEADER;

        List<Event> events = deserializeEvents(
                Arrays.copyOfRange(uncompressedEventsFileBytes, beginOfEventsIndex,
                        uncompressedEventsFileBytes.length));

        return new EventsFileData(formatVersion, events);
    }

    /**
     * Deserializes a list of {@link Event}s from an array of bytes in Cyface
     * {@link EventsFileSerializer#EVENT_TRANSFER_FILE_FORMAT_VERSION}.
     *
     * @param bytes The bytes array to deserialize the {@code Event}s from.
     * @return A list of the deserialized {@code Event}s.
     */
    private List<Event> deserializeEvents(byte[] bytes) throws UnsupportedEncodingException {
        List<Event> events = new ArrayList<>();
        int i = 0;
        while (i < bytes.length) {

            // Don't change the order how the bytes are read from the buffer
            // Timestamp, eventType, byte length of value string
            ByteBuffer buffer = ByteBuffer
                    .wrap(Arrays.copyOfRange(bytes, i, i + LONG_BYTES + SHORT_BYTES + SHORT_BYTES));
            final long timestamp = buffer.getLong();
            final short serializedEventType = buffer.getShort();
            final short valueBytesLength = buffer.getShort();
            final Event.EventType eventType = deserializeEventType(serializedEventType);
            i += LONG_BYTES + SHORT_BYTES + SHORT_BYTES;

            // Value String
            buffer = ByteBuffer.wrap(Arrays.copyOfRange(bytes, i, i + valueBytesLength));
            final byte[] valueBytes = new byte[valueBytesLength];
            buffer.get(valueBytes);
            final String value = new String(valueBytes, DEFAULT_CHARSET);
            i += valueBytesLength;

            final Event event = new Event(SAMPLE_LONG_VALUE, eventType, timestamp, value);
            events.add(event);
        }
        return events;
    }

    /**
     * Helper class for testing which wraps all data of {@link Event}s which were serialized into the
     * {@link EventsFileSerializer#EVENT_TRANSFER_FILE_FORMAT_VERSION}.
     */
    private static class EventsFileData {
        short transferFileFormat;
        List<Event> events;

        EventsFileData(short transferFileFormat, List<Event> events) {
            this.transferFileFormat = transferFileFormat;
            this.events = events;
        }
    }
}
