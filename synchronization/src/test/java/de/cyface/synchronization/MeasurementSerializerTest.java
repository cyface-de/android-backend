package de.cyface.synchronization;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;

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
    private MeasurementLoader loader;

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

    /**
     * Tests if serialization of a measurement is successful.
     * 
     * @throws IOException Should not happen as long as serialization depends on ByteArrayInputStream.
     */
    @Test
    public void testSerializeMeasurement() throws IOException, RemoteException {
        when(loader.loadGeoLocations()).thenReturn(geoLocationsCursor);
        when(loader.load3DPoint(any(Point3DSerializer.class))).thenReturn(pointsCursor);
        when(geoLocationsCursor.getCount()).thenReturn(3);
        when(pointsCursor.getCount()).thenReturn(3);
        when(geoLocationsCursor.moveToNext()).thenReturn(true).thenReturn(true).thenReturn(true).thenReturn(false);
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

        MeasurementSerializer serializer = new MeasurementSerializer();

        InputStream stream = serializer.serialize(loader);

        assertThat(stream.available(), is(equalTo(396)));
    }
}