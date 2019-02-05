package de.cyface.synchronization;

import static android.os.Build.VERSION_CODES.KITKAT;
import static de.cyface.persistence.serialization.MeasurementSerializer.BYTES_IN_HEADER;
import static de.cyface.persistence.serialization.MeasurementSerializer.BYTES_IN_ONE_GEO_LOCATION_ENTRY;
import static de.cyface.persistence.serialization.MeasurementSerializer.BYTES_IN_ONE_POINT_3D_ENTRY;
import static de.cyface.persistence.serialization.MeasurementSerializer.serialize;
import static de.cyface.synchronization.TestUtils.AUTHORITY;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import android.content.ContentProvider;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import de.cyface.persistence.FileAccessLayer;
import de.cyface.persistence.GeoLocationsTable;
import de.cyface.persistence.MeasurementContentProviderClient;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.Utils;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.Point3d;
import de.cyface.persistence.model.PointMetaData;
import de.cyface.persistence.serialization.MeasurementSerializer;
import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;

/**
 * Tests whether serialization and deserialization of the Cyface binary format is successful.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 2.0.0
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = KITKAT) // Because this test is not yet implemented for the code used on newer devices
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

    private MeasurementSerializer oocut;

    private final int SERIALIZED_SIZE = BYTES_IN_HEADER + 3 * BYTES_IN_ONE_GEO_LOCATION_ENTRY
            + 3 * 3 * BYTES_IN_ONE_POINT_3D_ENTRY;

    private final int samplePoint3ds = 3;
    private final int sampleGeoLocations = 3;
    private final double sampleDoubleValue = 1.0;
    private final long sampleLongValue = 1L;
    private final long sampleMeasurementId = 0L;

    @Before
    public void setUp() throws RemoteException, CursorIsNullException {

        // Mock GeoLocation database access
        Uri geoLocationUri = Utils.getGeoLocationsUri(AUTHORITY);
        when(loader.createGeoLocationTableUri()).thenReturn(geoLocationUri);
        when(loader.countData(geoLocationUri, GeoLocationsTable.COLUMN_MEASUREMENT_FK)).thenReturn(sampleGeoLocations);
        when(loader.loadGeoLocations(anyInt(), anyInt())).thenReturn(geoLocationsCursor);

        // Mock point counters
        when(persistence.loadPointMetaData(anyLong())).thenReturn(new PointMetaData(samplePoint3ds, samplePoint3ds,
                samplePoint3ds, MeasurementSerializer.PERSISTENCE_FILE_FORMAT_VERSION));
        when(persistence.getContext()).thenReturn(mockedContext);
        when(geoLocationsCursor.getCount()).thenReturn(sampleGeoLocations);

        // Mock insert of 3 GeoLocations
        when(geoLocationsCursor.moveToNext()).thenReturn(true).thenReturn(true).thenReturn(true).thenReturn(false);

        // Mock load sample GeoLocation data
        int sampleColumnIndex = 0;
        when(geoLocationsCursor.getColumnIndex(any(String.class))).thenReturn(sampleColumnIndex);
        when(geoLocationsCursor.getDouble(sampleColumnIndex)).thenReturn(sampleDoubleValue);
        when(geoLocationsCursor.getLong(sampleColumnIndex)).thenReturn(sampleLongValue);
        int sampleIntValue = 1;
        when(geoLocationsCursor.getInt(sampleColumnIndex)).thenReturn(sampleIntValue);

        // Mock load sample Point3dFile data
        List<Point3d> point3ds = new ArrayList<>();
        point3ds.add(new Point3d((float)sampleDoubleValue, (float)sampleDoubleValue, (float)sampleDoubleValue,
                sampleLongValue));
        point3ds.add(new Point3d((float)sampleDoubleValue, (float)sampleDoubleValue, (float)sampleDoubleValue,
                sampleLongValue));
        point3ds.add(new Point3d((float)sampleDoubleValue, (float)sampleDoubleValue, (float)sampleDoubleValue,
                sampleLongValue));
        final byte[] serializedPoint3ds = serialize(point3ds);
        Validate.notNull(serializedPoint3ds);
        // because this is null as we did not mock the mockedFileAccessLayer.getFilePath()
        when(mockedFileAccessLayer.loadBytes(Mockito.<File> any())).thenReturn(serializedPoint3ds);

        oocut = new MeasurementSerializer(mockedFileAccessLayer);
    }

    /**
     * Tests if serialization of a measurement is successful.
     *
     */
    @Test
    public void testSerializeMeasurement() throws CursorIsNullException {

        byte[] data = oocut.loadSerialized(loader, sampleMeasurementId, persistence);
        assertThat(data.length, is(equalTo(SERIALIZED_SIZE)));
    }

    /**
     * Tests whether deserialization of measurements from a serialized measurement is successful and provides the
     * expected result.
     *
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test
    public void testDeserializeMeasurement() throws CursorIsNullException {

        byte[] uncompressedTransferFileBytes = oocut.loadSerialized(loader, sampleMeasurementId, persistence);
        assertThat(uncompressedTransferFileBytes.length, is(equalTo(SERIALIZED_SIZE)));

        deserializeAndCheck(uncompressedTransferFileBytes);
    }

    /**
     * Tests successful serialization of a measurement to a compressed state.
     *
     * @throws IOException Thrown on streaming errors. Since this only uses ByteArrayStreams the exception should not
     *             occur.
     */
    @Test
    public void testSerializeCompressedMeasurement() throws IOException, CursorIsNullException {

        InputStream input = oocut.loadSerializedCompressed(loader, 0, persistence);

        final int SERIALIZED_COMPRESSED_SIZE = 30;
        assertThat(input.available(), is(equalTo(SERIALIZED_COMPRESSED_SIZE)));
    }

    /**
     * Tests that the serialized and compressed data can be decompressed and deserialized again.
     */
    @Test
    public void testDecompressDeserialize() throws IOException, DataFormatException, CursorIsNullException {

        // Assemble serialized compressed bytes
        InputStream compressedStream = oocut.loadSerializedCompressed(loader, sampleMeasurementId, persistence);
        byte[] compressedBytes = new byte[compressedStream.available()];

        // Decompress the compressed bytes and check length and bytes
        DataInputStream dis = new DataInputStream(compressedStream);
        dis.readFully(compressedBytes);
        Inflater inflater = new Inflater();
        inflater.setInput(compressedBytes, 0, compressedBytes.length);
        byte[] decompressedBytes = new byte[1000];
        int decompressedLength = inflater.inflate(decompressedBytes);
        inflater.end();
        assertThat(decompressedLength, is(equalTo(SERIALIZED_SIZE)));

        // Deserialize
        byte[] decompressedTransferFileBytes = Arrays.copyOfRange(decompressedBytes, 0, SERIALIZED_SIZE);

        // Deserialize bytes back to Objects and check their values
        deserializeAndCheck(decompressedTransferFileBytes);

        // Prepare comparison bytes which were not compressed at all
        // Mock insert of 3 GeoLocations (again)
        when(geoLocationsCursor.moveToNext()).thenReturn(true).thenReturn(true).thenReturn(true).thenReturn(false);
        byte[] uncompressedTransferFileBytes = oocut.loadSerialized(loader, sampleMeasurementId, persistence);
        deserializeAndCheck(uncompressedTransferFileBytes); // just to be sure

        // We check the bytes after we checked the object values because in the bytes it's hard to find the error
        assertThat(decompressedTransferFileBytes, is(equalTo(uncompressedTransferFileBytes)));
    }

    private void deserializeAndCheck(byte[] uncompressedTransferFileBytes) {
        MeasurementData measurementData = deserializeTransferFile(uncompressedTransferFileBytes);

        // Check header
        assertThat(measurementData.transferFileFormat, is(MeasurementSerializer.TRANSFER_FILE_FORMAT_VERSION));
        assertThat(measurementData.geoLocations.size(), is(sampleGeoLocations));
        assertThat(measurementData.accelerations.size(), is(samplePoint3ds));
        assertThat(measurementData.rotations.size(), is(samplePoint3ds));
        assertThat(measurementData.directions.size(), is(samplePoint3ds));

        // check values
        for (int i = 0; i < sampleGeoLocations; i++) {
            assertThat(measurementData.geoLocations.get(i).getAccuracy(), is((float)sampleDoubleValue));
            assertThat(measurementData.geoLocations.get(i).getLat(), is(sampleDoubleValue));
            assertThat(measurementData.geoLocations.get(i).getLon(), is(sampleDoubleValue));
            assertThat(measurementData.geoLocations.get(i).getSpeed(), is(sampleDoubleValue));
            assertThat(measurementData.geoLocations.get(i).getTimestamp(), is(sampleLongValue));
        }
        for (int i = 0; i < samplePoint3ds; i++) {
            assertThat(measurementData.accelerations.get(i).getX(), is((float)sampleDoubleValue));
            assertThat(measurementData.accelerations.get(i).getY(), is((float)sampleDoubleValue));
            assertThat(measurementData.accelerations.get(i).getZ(), is((float)sampleDoubleValue));
            assertThat(measurementData.accelerations.get(i).getTimestamp(), is(sampleLongValue));
            assertThat(measurementData.rotations.get(i).getX(), is((float)sampleDoubleValue));
            assertThat(measurementData.rotations.get(i).getY(), is((float)sampleDoubleValue));
            assertThat(measurementData.rotations.get(i).getZ(), is((float)sampleDoubleValue));
            assertThat(measurementData.rotations.get(i).getTimestamp(), is(sampleLongValue));
            assertThat(measurementData.directions.get(i).getX(), is((float)sampleDoubleValue));
            assertThat(measurementData.directions.get(i).getY(), is((float)sampleDoubleValue));
            assertThat(measurementData.directions.get(i).getZ(), is((float)sampleDoubleValue));
            assertThat(measurementData.directions.get(i).getTimestamp(), is(sampleLongValue));
        }
    }

    private MeasurementData deserializeTransferFile(byte[] uncompressedTransferFileBytes) {

        ByteBuffer buffer = ByteBuffer.wrap(uncompressedTransferFileBytes);
        short formatVersion = buffer.order(ByteOrder.BIG_ENDIAN).getShort(0);
        int numberOfGeoLocations = buffer.order(ByteOrder.BIG_ENDIAN).getInt(2);
        assertThat(numberOfGeoLocations, is(equalTo(3)));
        int numberOfAccelerations = buffer.order(ByteOrder.BIG_ENDIAN).getInt(6);
        assertThat(numberOfAccelerations, is(equalTo(3)));
        int numberOfRotations = buffer.order(ByteOrder.BIG_ENDIAN).getInt(10);
        assertThat(numberOfRotations, is(equalTo(3)));
        int numberOfDirections = buffer.order(ByteOrder.BIG_ENDIAN).getInt(14);
        assertThat(numberOfDirections, is(equalTo(3)));
        int beginOfGeoLocationsIndex = BYTES_IN_HEADER;
        int beginOfAccelerationsIndex = beginOfGeoLocationsIndex
                + numberOfGeoLocations * BYTES_IN_ONE_GEO_LOCATION_ENTRY;
        int beginOfRotationsIndex = beginOfAccelerationsIndex + numberOfAccelerations * BYTES_IN_ONE_POINT_3D_ENTRY;
        int beginOfDirectionsIndex = beginOfRotationsIndex + numberOfRotations * BYTES_IN_ONE_POINT_3D_ENTRY;

        List<GeoLocation> geoLocations = deserializeGeoLocations(
                Arrays.copyOfRange(uncompressedTransferFileBytes, beginOfGeoLocationsIndex, beginOfAccelerationsIndex));

        List<Point3d> accelerations = deserializePoint3D(
                Arrays.copyOfRange(uncompressedTransferFileBytes, beginOfAccelerationsIndex, beginOfRotationsIndex));

        List<Point3d> rotations = deserializePoint3D(
                Arrays.copyOfRange(uncompressedTransferFileBytes, beginOfRotationsIndex, beginOfDirectionsIndex));

        List<Point3d> directions = deserializePoint3D(Arrays.copyOfRange(uncompressedTransferFileBytes,
                beginOfDirectionsIndex, uncompressedTransferFileBytes.length));

        return new MeasurementData(formatVersion, geoLocations, accelerations, rotations, directions);
    }

    /**
     * Deserializes a list of {@link Point3d}s (i.e. acceleration, rotation or direction) from an array of bytes in
     * Cyface {@link MeasurementSerializer#TRANSFER_FILE_FORMAT_VERSION}.
     *
     * @param bytes The bytes array to deserialize the {@code Point3d}s from.
     * @return A list of the deserialized {@code Point3d}s.
     */
    private List<Point3d> deserializePoint3D(byte[] bytes) {
        List<Point3d> point3ds = new ArrayList<>();

        for (int i = 0; i < bytes.length; i += BYTES_IN_ONE_POINT_3D_ENTRY) {
            ByteBuffer buffer = ByteBuffer.wrap(Arrays.copyOfRange(bytes, i, i + BYTES_IN_ONE_POINT_3D_ENTRY));

            // Don't change the order how the bytes are read from the buffer
            final long timestamp = buffer.getLong();
            final double x = buffer.getDouble();
            final double y = buffer.getDouble();
            final double z = buffer.getDouble();
            Point3d point3d = new Point3d((float)x, (float)y, (float)z, timestamp);
            point3ds.add(point3d);
        }

        return point3ds;
    }

    /**
     * Deserializes a list of {@link GeoLocation}s from an array of bytes in Cyface
     * {@link MeasurementSerializer#TRANSFER_FILE_FORMAT_VERSION}.
     *
     * @param bytes The bytes array to deserialize the {@code GeoLocation}s from.
     * @return A list of the deserialized {@code GeoLocation}s.
     */
    private List<GeoLocation> deserializeGeoLocations(byte[] bytes) {
        List<GeoLocation> geoLocations = new ArrayList<>();
        for (int i = 0; i < bytes.length; i += BYTES_IN_ONE_GEO_LOCATION_ENTRY) {
            ByteBuffer buffer = ByteBuffer.wrap(Arrays.copyOfRange(bytes, i, i + BYTES_IN_ONE_GEO_LOCATION_ENTRY));

            // Don't change the order how the bytes are read from the buffer
            final long timestamp = buffer.getLong();
            final double lat = buffer.getDouble();
            final double lon = buffer.getDouble();
            final double speed = buffer.getDouble();
            final int accuracy = buffer.getInt();
            GeoLocation geoLocation = new GeoLocation(lat, lon, timestamp, speed, accuracy);
            geoLocations.add(geoLocation);
        }
        return geoLocations;
    }

    /**
     * Helper class for testing which wraps all data of a {@link Measurement} which was serialized into the
     * {@link MeasurementSerializer#TRANSFER_FILE_FORMAT_VERSION}.
     */
    private class MeasurementData {
        short transferFileFormat;
        List<GeoLocation> geoLocations;
        List<Point3d> accelerations;
        List<Point3d> rotations;
        List<Point3d> directions;

        MeasurementData(short transferFileFormat, List<GeoLocation> geoLocations, List<Point3d> accelerations,
                List<Point3d> rotations, List<Point3d> directions) {
            this.transferFileFormat = transferFileFormat;
            this.geoLocations = geoLocations;
            this.accelerations = accelerations;
            this.rotations = rotations;
            this.directions = directions;
        }
    }
}