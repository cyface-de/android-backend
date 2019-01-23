package de.cyface.synchronization;

import static de.cyface.persistence.serialization.MeasurementSerializer.BYTES_IN_HEADER;
import static de.cyface.persistence.serialization.MeasurementSerializer.BYTES_IN_ONE_GEO_LOCATION_ENTRY;
import static de.cyface.persistence.serialization.MeasurementSerializer.BYTES_IN_ONE_POINT_3D_ENTRY;
import static de.cyface.synchronization.TestUtils.AUTHORITY;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.when;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;

import android.content.ContentProvider;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import de.cyface.persistence.GeoLocationsTable;
import de.cyface.persistence.MeasurementContentProviderClient;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.PointMetaData;
import de.cyface.persistence.serialization.MeasurementSerializer;
import de.cyface.utils.CursorIsNullException;

/**
 * Tests whether serialization and deserialization of the Cyface binary format is successful.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.3.0
 * @since 2.0.0
 */
@RunWith(RobolectricTestRunner.class)
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
    /**
     * A mocked cursor for 3D points like accelerations, rotations and directions.
     */
    @Mock
    private Cursor pointsCursor;

    private final int SERIALIZED_SIZE = BYTES_IN_HEADER + 3 * BYTES_IN_ONE_GEO_LOCATION_ENTRY
            + 3 * 3 * BYTES_IN_ONE_POINT_3D_ENTRY;

    @Before
    public void setUp() throws RemoteException, CursorIsNullException {
        Uri geoLocationUri = new Uri.Builder().scheme("content").authority(AUTHORITY)
                .appendPath(GeoLocationsTable.URI_PATH).build();
        when(loader.createGeoLocationTableUri()).thenReturn(geoLocationUri);
        when(loader.countData(geoLocationUri, GeoLocationsTable.COLUMN_MEASUREMENT_FK)).thenReturn(3);
        when(loader.loadGeoLocations(anyInt(), anyInt())).thenReturn(geoLocationsCursor);
        when(persistence.loadPointMetaData(anyLong()))
                .thenReturn(new PointMetaData(3, 3, 3, MeasurementSerializer.PERSISTENCE_FILE_FORMAT_VERSION));
        when(persistence.getContext()).thenReturn(mockedContext);
        when(mockedContext.getFilesDir()).thenReturn(new File("/mocked-files-dir/"));
        when(geoLocationsCursor.getCount()).thenReturn(3);
        // Insert 3 geo locations
        when(geoLocationsCursor.moveToNext()).thenReturn(true).thenReturn(true).thenReturn(true).thenReturn(false);
        // Insert 3 points of each Point3d type
        when(pointsCursor.moveToNext()).thenReturn(true).thenReturn(true).thenReturn(true).thenReturn(false)
                .thenReturn(true).thenReturn(true).thenReturn(true).thenReturn(false).thenReturn(true).thenReturn(true)
                .thenReturn(true).thenReturn(false);
        // Sample point attributes
        when(geoLocationsCursor.getColumnIndex(any(String.class))).thenReturn(0);
        when(pointsCursor.getColumnIndex(any(String.class))).thenReturn(0);
        when(geoLocationsCursor.getDouble(0)).thenReturn(1.0);
        when(pointsCursor.getDouble(0)).thenReturn(1.0);
        when(geoLocationsCursor.getLong(0)).thenReturn(1L);
        when(pointsCursor.getLong(0)).thenReturn(1L);
        when(geoLocationsCursor.getInt(0)).thenReturn(1);
    }

    /**
     * Tests if serialization of a measurement is successful.
     *
     */
    @Test
    public void testSerializeMeasurement() throws CursorIsNullException {
        MeasurementSerializer serializer = new MeasurementSerializer();
        byte[] data = serializer.loadSerialized(loader, 0, persistence);
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
        MeasurementSerializer serializer = new MeasurementSerializer();

        byte[] measurementBytes = serializer.loadSerialized(loader, 0, persistence);
        // byte[] individualBytes = new byte[SERIALIZED_SIZE];
        assertThat(/* stream.read(individualBytes) */measurementBytes.length, is(equalTo(SERIALIZED_SIZE)));

        ByteBuffer buffer = ByteBuffer.wrap(measurementBytes);
        short formatVersion = buffer.order(ByteOrder.BIG_ENDIAN).getShort(0);
        assertThat(formatVersion, is(equalTo((short)1)));
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

        List<Map<String, ?>> geoLocations = deserializeGeoLocations(
                Arrays.copyOfRange(measurementBytes, beginOfGeoLocationsIndex, beginOfAccelerationsIndex));
        assertThat(geoLocations, hasSize(3));

        List<Map<String, ?>> accelerations = deserializePoint3D(
                Arrays.copyOfRange(measurementBytes, beginOfAccelerationsIndex, beginOfRotationsIndex));
        assertThat(accelerations, hasSize(3));

        List<Map<String, ?>> rotations = deserializePoint3D(
                Arrays.copyOfRange(measurementBytes, beginOfRotationsIndex, beginOfDirectionsIndex));
        assertThat(rotations, hasSize(3));

        List<Map<String, ?>> directions = deserializePoint3D(
                Arrays.copyOfRange(measurementBytes, beginOfDirectionsIndex, measurementBytes.length));
        assertThat(directions, hasSize(3));
    }

    /**
     * Tests successful serialization of a measurement to a compressed state.
     *
     * @throws IOException Thrown on streaming errors. Since this only uses ByteArrayStreams the exception should not
     *             occur.
     */
    @Test
    public void testSerializeCompressedMeasurement() throws IOException, CursorIsNullException {
        MeasurementSerializer serializer = new MeasurementSerializer();

        InputStream input = serializer.loadSerializedCompressed(loader, 0, persistence);

        // Before the epic #CY-4067 the compression resulted into 30 bytes - did the compression change?
        final int SERIALIZED_COMPRESSED_SIZE = 31;
        assertThat(input.available(), is(equalTo(SERIALIZED_COMPRESSED_SIZE)));
    }

    /**
     * Deserializes a list of 3D sample points (i.e. acceleration, rotation or direction) from an array of bytes in
     * Cyface binary format.
     *
     * @param bytes The bytes array to deserialize the sample points from.
     * @return A poor mans list of objects (i.e. <code>Map</code>). Each map contains 4 entrys for x, y, z and timestamp
     *         with the corresponding values. A timestamp is a <code>long</code>, all other values are
     *         <code>double</code>.
     */
    private List<Map<String, ?>> deserializePoint3D(byte[] bytes) {
        List<Map<String, ?>> ret = new ArrayList<>();

        for (int i = 0; i < bytes.length; i += BYTES_IN_ONE_POINT_3D_ENTRY) {
            ByteBuffer buffer = ByteBuffer.wrap(Arrays.copyOfRange(bytes, i, i + BYTES_IN_ONE_POINT_3D_ENTRY));
            Map<String, Object> entry = new HashMap<>(4);
            entry.put("timestamp", buffer.getLong());
            entry.put("x", buffer.getDouble());
            entry.put("y", buffer.getDouble());
            entry.put("z", buffer.getDouble());

            ret.add(entry);
        }

        return ret;
    }

    /**
     * Deserializes a list of geo locations from an array of bytes in Cyface binary format.
     *
     * @param bytes The bytes array to deserialize the geo locations from.
     * @return A poor mans list of objects (i.e. <code>Map</code>). Each map contains 5 entrys keyed with "timestamp",
     *         "lat", "lon", "speed" and "accuracy" with the appropriate values. The timestamp is a <code>long</code>,
     *         accuracy is an <code>int</code> and all other values are <code>double</code> values.
     */
    private List<Map<String, ?>> deserializeGeoLocations(byte[] bytes) {
        List<Map<String, ?>> ret = new ArrayList<>();
        for (int i = 0; i < bytes.length; i += BYTES_IN_ONE_GEO_LOCATION_ENTRY) {
            ByteBuffer buffer = ByteBuffer.wrap(Arrays.copyOfRange(bytes, i, i + BYTES_IN_ONE_GEO_LOCATION_ENTRY));
            Map<String, Object> entry = new HashMap<>(5);
            entry.put("timestamp", buffer.getLong());
            entry.put("lat", buffer.getDouble());
            entry.put("lon", buffer.getDouble());
            entry.put("speed", buffer.getDouble());
            entry.put("accuracy", buffer.getInt());

            ret.add(entry);
        }
        return ret;
    }

    /**
     * Tests that the serialized and compressed data can be decompressed and deserialized again.
     */
    @Test
    public void testDecompressDeserialize() throws IOException, DataFormatException, CursorIsNullException {
        MeasurementSerializer serializer = new MeasurementSerializer();
        byte[] serializedData = serializer.loadSerialized(loader, 0, persistence);
        InputStream compressedStream = serializer.loadSerializedCompressed(loader, 0, persistence);

        // Decompress the compressed bytes and check length and bytes
        byte[] compressedBytes = new byte[compressedStream.available()];
        DataInputStream dis = new DataInputStream(compressedStream);
        dis.readFully(compressedBytes);
        Inflater inflater = new Inflater();
        inflater.setInput(compressedBytes, 0, compressedBytes.length);
        byte[] decompressedBytes = new byte[1000];
        int decompressedLength = inflater.inflate(decompressedBytes);
        inflater.end();
        assertThat(decompressedLength, is(equalTo(SERIALIZED_SIZE)));
        assertThat(Arrays.copyOfRange(decompressedBytes, 0, SERIALIZED_SIZE), is(equalTo(serializedData)));
    }
}