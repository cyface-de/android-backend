package de.cyface.synchronization;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import android.database.Cursor;
import android.os.RemoteException;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
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
     * A mocked cursor for geo locations.
     */
    @Mock
    private Cursor geoLocationsCursor;

    /**
     * A mocked cursor for 3D points like accelerations, rotations and directions.
     */
    @Mock
    private Cursor pointsCursor;

    @Before
    public void setUp() throws RemoteException {
        when(loader.loadGeoLocations()).thenReturn(geoLocationsCursor);
        when(loader.load3DPoint(any(Point3DSerializer.class))).thenReturn(pointsCursor);
        when(geoLocationsCursor.getCount()).thenReturn(3);
        when(pointsCursor.getCount()).thenReturn(3);
        // Insert 3 geo locations
        when(geoLocationsCursor.moveToNext()).thenReturn(true).thenReturn(true).thenReturn(true).thenReturn(false);
        // Insert 3 points of each kind
        when(pointsCursor.moveToNext()).thenReturn(true).thenReturn(true).thenReturn(true).thenReturn(false)
                .thenReturn(true).thenReturn(true).thenReturn(true).thenReturn(false).thenReturn(true).thenReturn(true)
                .thenReturn(true).thenReturn(false);
        when(geoLocationsCursor.getColumnIndex(any(String.class))).thenReturn(0);
        when(pointsCursor.getColumnIndex(any(String.class))).thenReturn(0);
        when(geoLocationsCursor.getDouble(0)).thenReturn(1.0);
        when(pointsCursor.getDouble(0)).thenReturn(1.0);
        when(geoLocationsCursor.getLong(0)).thenReturn(1L);
        when(pointsCursor.getLong(0)).thenReturn(1L);
        when(geoLocationsCursor.getInt(0)).thenReturn(1);
        when(pointsCursor.getInt(0)).thenReturn(1);
    }

    /**
     * Tests if serialization of a measurement is successful.
     *
     * @throws IOException Should not happen as long as serialization depends on ByteArrayInputStream.
     */
    @Test
    public void testSerializeMeasurement() throws IOException {
        MeasurementSerializer serializer = new MeasurementSerializer();

        InputStream stream = serializer.serialize(loader);

        assertThat(stream.available(), is(equalTo(414)));
    }

    @Test
    public void testDeserializeMeasurement() throws IOException {
        MeasurementSerializer serializer = new MeasurementSerializer();

        InputStream stream = serializer.serialize(loader);

        byte[] individualBytes = new byte[414];
        assertThat(stream.read(individualBytes), is(equalTo(414)));

        ByteBuffer buffer = ByteBuffer.wrap(individualBytes);
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
        int beginOfGeoLocationsIndex = 18;
        int beginOfAccelerationsIndex = beginOfGeoLocationsIndex
                + numberOfGeoLocations * MeasurementSerializer.BYTES_IN_ONE_GEO_LOCATION_ENTRY;
        int beginOfRotationsIndex = beginOfAccelerationsIndex
                + numberOfAccelerations * Point3DSerializer.BYTES_IN_ONE_POINT_ENTRY;
        int beginOfDirectionsIndex = beginOfRotationsIndex
                + numberOfRotations * Point3DSerializer.BYTES_IN_ONE_POINT_ENTRY;

        List<Map<String, ?>> geoLocations = deserializeGeoLocations(
                Arrays.copyOfRange(individualBytes, beginOfGeoLocationsIndex, beginOfAccelerationsIndex));
        assertThat(geoLocations, hasSize(3));

        List<Map<String, ?>> accelerations = deserializePoint3D(
                Arrays.copyOfRange(individualBytes, beginOfAccelerationsIndex, beginOfRotationsIndex));
        assertThat(accelerations, hasSize(3));

        List<Map<String, ?>> rotations = deserializePoint3D(
                Arrays.copyOfRange(individualBytes, beginOfRotationsIndex, beginOfDirectionsIndex));
        assertThat(rotations, hasSize(3));

        List<Map<String, ?>> directions = deserializePoint3D(
                Arrays.copyOfRange(individualBytes, beginOfDirectionsIndex, individualBytes.length));
        assertThat(directions, hasSize(3));
    }

    private List<Map<String, ?>> deserializePoint3D(byte[] bytes) {
        List<Map<String, ?>> ret = new ArrayList<>();

        for (int i = 0; i < bytes.length; i += Point3DSerializer.BYTES_IN_ONE_POINT_ENTRY) {
            ByteBuffer buffer = ByteBuffer
                    .wrap(Arrays.copyOfRange(bytes, i, i + Point3DSerializer.BYTES_IN_ONE_POINT_ENTRY));
            Map<String, Object> entry = new HashMap<>(4);
            entry.put("timestamp", buffer.getLong());
            entry.put("x", buffer.getDouble());
            entry.put("y", buffer.getDouble());
            entry.put("z", buffer.getDouble());

            ret.add(entry);
        }

        return ret;
    }

    private List<Map<String, ?>> deserializeGeoLocations(byte[] bytes) {
        List<Map<String, ?>> ret = new ArrayList<>();
        for (int i = 0; i < bytes.length; i += MeasurementSerializer.BYTES_IN_ONE_GEO_LOCATION_ENTRY) {
            ByteBuffer buffer = ByteBuffer
                    .wrap(Arrays.copyOfRange(bytes, i, i + MeasurementSerializer.BYTES_IN_ONE_GEO_LOCATION_ENTRY));
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
}