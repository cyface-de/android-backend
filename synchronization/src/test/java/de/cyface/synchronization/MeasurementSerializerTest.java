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
package de.cyface.synchronization;

import static android.os.Build.VERSION_CODES.P;
import static de.cyface.persistence.model.MeasurementStatus.OPEN;
import static de.cyface.persistence.serialization.MeasurementSerializer.BYTES_IN_HEADER;
import static de.cyface.persistence.serialization.MeasurementSerializer.COMPRESSION_NOWRAP;
import static de.cyface.persistence.serialization.Point3dType.ACCELERATION;
import static de.cyface.persistence.serialization.Point3dType.DIRECTION;
import static de.cyface.persistence.serialization.Point3dType.ROTATION;
import static de.cyface.protos.model.Measurement.parseFrom;
import static de.cyface.synchronization.TestUtils.AUTHORITY;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import com.google.protobuf.InvalidProtocolBufferException;

import android.content.ContentProvider;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;

import de.cyface.persistence.DefaultFileAccess;
import de.cyface.persistence.FileAccessLayer;
import de.cyface.persistence.GeoLocationsTable;
import de.cyface.persistence.MeasurementContentProviderClient;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.Utils;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Modality;
import de.cyface.persistence.model.Point3d;
import de.cyface.persistence.serialization.MeasurementSerializer;
import de.cyface.persistence.serialization.Point3dFile;
import de.cyface.persistence.serialization.TransferFileSerializer;
import de.cyface.persistence.serialization.proto.Point3dSerializer;
import de.cyface.protos.model.Measurement;
import de.cyface.synchronization.serialization.proto.LocationDeserializer;
import de.cyface.synchronization.serialization.proto.Point3dDeserializer;
import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;

/**
 * Tests whether serialization and deserialization of the Cyface binary format is successful.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 3.0.0
 * @since 2.0.0
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = P) // >= Q needs java 9
public class MeasurementSerializerTest {

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
     * A mock persistence layer, not accessing any files.
     */
    @Mock
    PersistenceLayer persistence;
    /**
     * A mock mockedContext, to be able to mock-generate file paths
     */
    @Mock
    Context mockedContext;
    /**
     * A mocked cursor for geo locations.
     */
    @Mock
    private Cursor geoLocationsCursor;
    @Mock
    private FileAccessLayer mockedFileAccessLayer;
    @Mock
    private File mockedAccelerationFile;
    @Mock
    private File mockedRotationFile;
    @Mock
    private File mockedDirectionFile;
    private MeasurementSerializer oocut;
    private final static int SAMPLE_ACCELERATION_POINTS = 21;
    private final static int SAMPLE_ROTATION_POINTS = 20;
    private final static int SAMPLE_DIRECTION_POINTS = 10;
    private final static int SAMPLE_GEO_LOCATIONS = 3;
    private final static double SAMPLE_DOUBLE_VALUE = 1.0;
    private final static int SAMPLE_ACCURACY_VALUE = 1;
    private final static long SAMPLE_LONG_VALUE = 1L;
    private final static long SAMPLE_MEASUREMENT_ID = 1L;
    private final static long SERIALIZED_MEASUREMENT_FILE_SIZE = 284L; // FIXME: just updated

    @Before
    public void setUp() throws RemoteException, CursorIsNullException {

        // Mock GeoLocation database access
        Uri geoLocationUri = Utils.getGeoLocationsUri(AUTHORITY);
        when(loader.createGeoLocationTableUri()).thenReturn(geoLocationUri);
        when(loader.countData(geoLocationUri, GeoLocationsTable.COLUMN_MEASUREMENT_FK))
                .thenReturn(SAMPLE_GEO_LOCATIONS);
        when(loader.loadGeoLocations(anyInt(), anyInt())).thenReturn(geoLocationsCursor);

        // Mock point counters
        final de.cyface.persistence.model.Measurement measurement = new de.cyface.persistence.model.Measurement(1L,
                OPEN, Modality.UNKNOWN,
                MeasurementSerializer.PERSISTENCE_FILE_FORMAT_VERSION, 0.0, 123L);
        when(persistence.loadMeasurement(anyLong())).thenReturn(measurement);
        when(persistence.getContext()).thenReturn(mockedContext);
        when(geoLocationsCursor.getCount()).thenReturn(SAMPLE_GEO_LOCATIONS);

        // Mock insert of 3 GeoLocations
        when(geoLocationsCursor.moveToNext()).thenReturn(true).thenReturn(true).thenReturn(true).thenReturn(false);

        // Mock load sample GeoLocation data
        int sampleColumnIndex = 0;
        when(geoLocationsCursor.getColumnIndexOrThrow(any(String.class))).thenReturn(sampleColumnIndex);
        when(geoLocationsCursor.getDouble(sampleColumnIndex)).thenReturn(SAMPLE_DOUBLE_VALUE);
        when(geoLocationsCursor.getLong(sampleColumnIndex)).thenReturn(SAMPLE_LONG_VALUE);
        when(geoLocationsCursor.getInt(sampleColumnIndex)).thenReturn(SAMPLE_ACCURACY_VALUE);

        // Mock load sample Point3dFile data
        final List<Point3d> accelerations = new ArrayList<>();
        final List<Point3d> rotations = new ArrayList<>();
        final List<Point3d> directions = new ArrayList<>();
        for (int i = 0; i < SAMPLE_ACCELERATION_POINTS; i++) {
            accelerations.add(new Point3d((float)SAMPLE_DOUBLE_VALUE, (float)SAMPLE_DOUBLE_VALUE,
                    (float)SAMPLE_DOUBLE_VALUE, SAMPLE_LONG_VALUE));
        }
        for (int i = 0; i < SAMPLE_ROTATION_POINTS; i++) {
            rotations.add(new Point3d((float)SAMPLE_DOUBLE_VALUE, (float)SAMPLE_DOUBLE_VALUE,
                    (float)SAMPLE_DOUBLE_VALUE, SAMPLE_LONG_VALUE));
        }
        for (int i = 0; i < SAMPLE_DIRECTION_POINTS; i++) {
            directions.add(new Point3d((float)SAMPLE_DOUBLE_VALUE, (float)SAMPLE_DOUBLE_VALUE,
                    (float)SAMPLE_DOUBLE_VALUE, SAMPLE_LONG_VALUE));
        }
        final byte[] serializedAccelerations = Point3dSerializer.serialize(accelerations, ACCELERATION);
        Validate.notNull(serializedAccelerations);
        final byte[] serializedRotations = Point3dSerializer.serialize(rotations, ROTATION);
        Validate.notNull(serializedRotations);
        final byte[] serializedDirections = Point3dSerializer.serialize(directions, DIRECTION);
        Validate.notNull(serializedDirections);

        // Mock persistence
        when(persistence.getFileAccessLayer()).thenReturn(mockedFileAccessLayer);

        // Mock FileAccessLayer
        when(mockedFileAccessLayer.getFilePath(any(Context.class), eq(SAMPLE_MEASUREMENT_ID),
                eq(Point3dFile.ACCELERATIONS_FOLDER_NAME), eq(Point3dFile.ACCELERATIONS_FILE_EXTENSION)))
                        .thenReturn(mockedAccelerationFile);
        when(mockedFileAccessLayer.getFilePath(any(Context.class), eq(SAMPLE_MEASUREMENT_ID),
                eq(Point3dFile.ROTATIONS_FOLDER_NAME), eq(Point3dFile.ROTATION_FILE_EXTENSION)))
                        .thenReturn(mockedRotationFile);
        when(mockedFileAccessLayer.getFilePath(any(Context.class), eq(SAMPLE_MEASUREMENT_ID),
                eq(Point3dFile.DIRECTIONS_FOLDER_NAME), eq(Point3dFile.DIRECTION_FILE_EXTENSION)))
                        .thenReturn(mockedDirectionFile);
        when(mockedFileAccessLayer.loadBytes(mockedAccelerationFile)).thenReturn(serializedAccelerations);
        when(mockedFileAccessLayer.loadBytes(mockedRotationFile)).thenReturn(serializedRotations);
        when(mockedFileAccessLayer.loadBytes(mockedDirectionFile)).thenReturn(serializedDirections);
        when(mockedAccelerationFile.exists()).thenReturn(true);
        when(mockedRotationFile.exists()).thenReturn(true);
        when(mockedDirectionFile.exists()).thenReturn(true);
        when(mockedAccelerationFile.length()).thenReturn((long)serializedAccelerations.length);
        when(mockedRotationFile.length()).thenReturn((long)serializedRotations.length);
        when(mockedDirectionFile.length()).thenReturn((long)serializedDirections.length);

        doAnswer((Answer<Void>)invocation -> {
            Object[] args = invocation.getArguments();
            BufferedOutputStream outputStream = (BufferedOutputStream)args[1];
            outputStream.write(serializedAccelerations);
            outputStream.flush();
            return null;
        }).when(mockedFileAccessLayer).writeToOutputStream(eq(mockedAccelerationFile), any(BufferedOutputStream.class));
        doAnswer((Answer<Void>)invocation -> {
            Object[] args = invocation.getArguments();
            BufferedOutputStream outputStream = (BufferedOutputStream)args[1];
            outputStream.write(serializedRotations);
            outputStream.flush();
            return null;
        }).when(mockedFileAccessLayer).writeToOutputStream(eq(mockedRotationFile), any(BufferedOutputStream.class));
        doAnswer((Answer<Void>)invocation -> {
            Object[] args = invocation.getArguments();
            BufferedOutputStream outputStream = (BufferedOutputStream)args[1];
            outputStream.write(serializedDirections);
            outputStream.flush();
            return null;
        }).when(mockedFileAccessLayer).writeToOutputStream(eq(mockedDirectionFile), any(BufferedOutputStream.class));

        oocut = new MeasurementSerializer();
    }

    /**
     * Tests if serialization of a measurement is successful.
     * <p>
     * This test checks that the binary header contains the correct {@link Point3d} counters.
     */
    @Test
    public void testSerializeMeasurement() throws IOException, CursorIsNullException {

        // Arrange
        final File serializedFile = File.createTempFile("serializedTestFile", ".tmp");
        try {
            final FileOutputStream fileOutputStream = new FileOutputStream(serializedFile);
            final BufferedOutputStream bufferedFileOutputStream = new BufferedOutputStream(fileOutputStream);

            // Act
            TransferFileSerializer.loadSerialized(bufferedFileOutputStream, loader, SAMPLE_MEASUREMENT_ID, persistence);

            // Assert
            assertThat(serializedFile.exists(), is(true));
            assertThat(serializedFile.length(), is(equalTo(SERIALIZED_MEASUREMENT_FILE_SIZE)));
        } finally {
            if (serializedFile.exists()) {
                Validate.isTrue(serializedFile.delete());
            }
        }
    }

    /**
     * Tests whether deserialization of measurements from a serialized measurement is successful and provides the
     * expected result.
     *
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test
    public void testDeserializeMeasurement() throws CursorIsNullException, IOException {

        // Arrange
        final File serializedFile = File.createTempFile("serializedTestFile", ".tmp");
        try {
            final FileOutputStream fileOutputStream = new FileOutputStream(serializedFile);
            final BufferedOutputStream bufferedFileOutputStream = new BufferedOutputStream(fileOutputStream);

            // Already tested by `testSerializeMeasurement` - but not the deserialized bytes
            TransferFileSerializer.loadSerialized(bufferedFileOutputStream, loader, SAMPLE_MEASUREMENT_ID, persistence);
            assertThat(serializedFile.length(), is(equalTo(SERIALIZED_MEASUREMENT_FILE_SIZE)));

            // Act & Assert - check the deserialized bytes
            deserializeAndCheck(new DefaultFileAccess().loadBytes(serializedFile));
        } finally {
            if (serializedFile.exists()) {
                Validate.isTrue(serializedFile.delete());
            }
        }
    }

    /**
     * Tests successful serialization of a measurement to a compressed state.
     */
    @Test
    public void testSerializeCompressedMeasurement() throws CursorIsNullException {

        // If you need to change the sample point counts (and this) make sure the test work with the previous counts
        // FIXME: increased from 43L to 91L. Maybe just because the sample data is like 0,0,0,0,0
        // which can be compressed unrealistically?
        // TODO: compare the file size with the analysis we did in MOVEBIS
        // TODO: check if the compression still makes sense with the new custom encoding!
        final long SERIALIZED_COMPRESSED_SIZE = 91L; // When compression Deflater(level 9, true)
        final File compressedTransferBytes = oocut.writeSerializedCompressed(loader, SAMPLE_MEASUREMENT_ID,
                persistence);
        assertThat(compressedTransferBytes.length(), is(equalTo(SERIALIZED_COMPRESSED_SIZE)));
    }

    /**
     * Tests that the serialized and compressed data can be decompressed and deserialized again.
     */
    @Test
    public void testDecompressDeserialize() throws IOException, DataFormatException, CursorIsNullException {

        // Assemble serialized compressed bytes
        final File compressedTransferTempFile = oocut.writeSerializedCompressed(loader, SAMPLE_MEASUREMENT_ID,
                persistence);
        // Load bytes from compressedTransferFile
        final byte[] compressedBytes = new byte[(int)compressedTransferTempFile.length()];
        DataInputStream dis = new DataInputStream(new FileInputStream(compressedTransferTempFile));
        dis.readFully(compressedBytes);

        // Decompress the compressed bytes and check length and bytes
        Inflater inflater = new Inflater(COMPRESSION_NOWRAP);
        inflater.setInput(compressedBytes, 0, compressedBytes.length);
        byte[] decompressedBytes = new byte[2000];
        long decompressedLength = inflater.inflate(decompressedBytes);
        inflater.end();
        assertThat(decompressedLength, is(equalTo(SERIALIZED_MEASUREMENT_FILE_SIZE)));

        // Deserialize
        byte[] decompressedTransferFileBytes = Arrays.copyOfRange(decompressedBytes, 0,
                (int)SERIALIZED_MEASUREMENT_FILE_SIZE);

        // Deserialize bytes back to Objects and check their values
        deserializeAndCheck(decompressedTransferFileBytes);

        // Prepare comparison bytes which were not compressed at all
        // Mock insert of 3 GeoLocations (again)
        when(geoLocationsCursor.moveToNext()).thenReturn(true).thenReturn(true).thenReturn(true).thenReturn(false);
        final File serializedFile = File.createTempFile("serializedTestFile", ".tmp");
        final byte[] uncompressedTransferFileBytes;
        try {
            final FileOutputStream fileOutputStream = new FileOutputStream(serializedFile);
            final BufferedOutputStream bufferedFileOutputStream = new BufferedOutputStream(fileOutputStream);
            TransferFileSerializer.loadSerialized(bufferedFileOutputStream, loader, SAMPLE_MEASUREMENT_ID, persistence);
            assertThat(serializedFile.length(), is(equalTo(SERIALIZED_MEASUREMENT_FILE_SIZE)));

            uncompressedTransferFileBytes = new DefaultFileAccess().loadBytes(serializedFile);
            deserializeAndCheck(uncompressedTransferFileBytes); // just to be sure
        } finally {
            if (serializedFile.exists()) {
                Validate.isTrue(serializedFile.delete());
            }
        }

        // We check the bytes after we checked the object values because in the bytes it's hard to find the error
        assertThat(decompressedTransferFileBytes, is(equalTo(uncompressedTransferFileBytes)));
    }

    /**
     * @param bytes The {@code byte}s containing as stored in the uncompressed transfer file
     */
    private void deserializeAndCheck(byte[] bytes) throws InvalidProtocolBufferException {

        Validate.isTrue(bytes.length > 0);
        Measurement measurement = deserializeTransferFile(bytes);
        assertThat(measurement.getFormatVersion(), is((int)MeasurementSerializer.TRANSFER_FILE_FORMAT_VERSION));
        assertThat(measurement.getLocationRecords().getSpeedCount(), is(SAMPLE_GEO_LOCATIONS));
        assertThat(measurement.getAccelerations().getXCount(), is(SAMPLE_ACCELERATION_POINTS));
        assertThat(measurement.getRotations().getYCount(), is(SAMPLE_ROTATION_POINTS));
        assertThat(measurement.getDirections().getZCount(), is(SAMPLE_DIRECTION_POINTS));

        // Convert back to the model format (from the offset/diff proto format)
        final List<GeoLocation> locations = LocationDeserializer.deserialize(measurement.getLocationRecords());

        final List<Point3d> accelerations = Point3dDeserializer.deserialize(measurement.getAccelerations());
        final List<Point3d> rotations = Point3dDeserializer.deserialize(measurement.getRotations());
        final List<Point3d> directions = Point3dDeserializer.deserialize(measurement.getDirections());

        for (int i = 0; i < SAMPLE_GEO_LOCATIONS; i++) {
            final GeoLocation entry = locations.get(i);
            assertThat(entry.getAccuracy(), is((float)SAMPLE_ACCURACY_VALUE));
            assertThat(entry.getLat(), is(SAMPLE_DOUBLE_VALUE));
            assertThat(entry.getLon(), is(SAMPLE_DOUBLE_VALUE));
            assertThat(entry.getSpeed(), is(SAMPLE_DOUBLE_VALUE));
            assertThat(entry.getTimestamp(), is(SAMPLE_LONG_VALUE));
        }
        for (int i = 0; i < SAMPLE_ACCELERATION_POINTS; i++) {
            final Point3d entry = accelerations.get(i);
            assertThat(entry.getX(), is((float)SAMPLE_DOUBLE_VALUE));
            assertThat(entry.getY(), is((float)SAMPLE_DOUBLE_VALUE));
            assertThat(entry.getZ(), is((float)SAMPLE_DOUBLE_VALUE));
            assertThat(entry.getTimestamp(), is(SAMPLE_LONG_VALUE));
        }
        for (int i = 0; i < SAMPLE_ROTATION_POINTS; i++) {
            final Point3d entry = rotations.get(i);
            assertThat(entry.getX(), is((float)SAMPLE_DOUBLE_VALUE));
            assertThat(entry.getY(), is((float)SAMPLE_DOUBLE_VALUE));
            assertThat(entry.getZ(), is((float)SAMPLE_DOUBLE_VALUE));
            assertThat(entry.getTimestamp(), is(SAMPLE_LONG_VALUE));
        }
        for (int i = 0; i < SAMPLE_DIRECTION_POINTS; i++) {
            final Point3d entry = directions.get(i);
            assertThat(entry.getX(), is((float)SAMPLE_DOUBLE_VALUE));
            assertThat(entry.getY(), is((float)SAMPLE_DOUBLE_VALUE));
            assertThat(entry.getZ(), is((float)SAMPLE_DOUBLE_VALUE));
            assertThat(entry.getTimestamp(), is(SAMPLE_LONG_VALUE));
        }
    }

    /**
     * @param bytes The {@code byte}s containing as stored in the uncompressed transfer file
     */
    private Measurement deserializeTransferFile(byte[] bytes) throws InvalidProtocolBufferException {
        // FIXME: Did we decide to keep storing the version like this?
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        final short formatVersion = buffer.order(ByteOrder.BIG_ENDIAN).getShort(0);
        assertThat(formatVersion, is(equalTo((short)2)));

        // slice from index 5 to index 9
        byte[] protoBytes = Arrays.copyOfRange(bytes, BYTES_IN_HEADER, bytes.length);

        final Measurement deserialized = parseFrom(protoBytes);
        assertThat(deserialized.getFormatVersion(), is(equalTo(2)));
        assertThat(deserialized.getLocationRecords().getTimestampCount(), is(equalTo(SAMPLE_GEO_LOCATIONS)));
        assertThat(deserialized.getAccelerations().getTimestampCount(), is(equalTo(SAMPLE_ACCELERATION_POINTS)));
        assertThat(deserialized.getRotations().getTimestampCount(), is(equalTo(SAMPLE_ROTATION_POINTS)));
        assertThat(deserialized.getDirections().getTimestampCount(), is(equalTo(SAMPLE_DIRECTION_POINTS)));
        return deserialized;
    }
}
