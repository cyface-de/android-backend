package de.cyface.synchronization;

import static junit.framework.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;

import de.cyface.persistence.GpsPointsTable;
import de.cyface.persistence.MeasuringPointsContentProvider;
import de.cyface.persistence.SamplePointTable;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {

    private static final String TAG = "de.cyface.test";

    private ContentResolver resolver;

    @Test
    public void useAppContext() throws Exception {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getTargetContext();

        assertEquals("de.cyface.synchronization.test", appContext.getPackageName());
    }

    @Test
    public void testUploadSomeBytesViaMultiPart() {
        resolver = InstrumentationRegistry.getTargetContext().getContentResolver();

        HttpURLConnection.setFollowRedirects(false);
        HttpURLConnection connection = null;
        String fileName = "test.txt";

        try {
            connection = (HttpURLConnection) new URL("http://192.168.178.41:8080/measurements").openConnection();
            connection.setRequestMethod("POST");
            String boundary = "---------------------------boundary";
            String tail = "\r\n--" + boundary + "--\r\n";
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            connection.setDoOutput(true);

            String metadataPart = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"metadata\"\r\n\r\n"
                    + "" + "\r\n";

            String fileHeader1 = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"fileToUpload\"; filename=\""
                    + fileName + "\"\r\n"
                    + "Content-Type: application/octet-stream\r\n"
                    + "Content-Transfer-Encoding: binary\r\n";

            byte[] source = "test".getBytes();
            long fileLength = source.length + tail.length();
            String fileHeader2 = "Content-length: " + fileLength + "\r\n";
            String fileHeader = fileHeader1 + fileHeader2 + "\r\n";
            String stringData = metadataPart + fileHeader;

            long requestLength = stringData.length() + fileLength;
            connection.setRequestProperty("Content-length", "" + requestLength);
            connection.setFixedLengthStreamingMode((int) requestLength);
            connection.connect();

            DataOutputStream out = new DataOutputStream(connection.getOutputStream());
            out.writeBytes(stringData);
            out.flush();

            int progress = 0;
            int bytesRead = 0;
            byte buf[] = new byte[1024];
            BufferedInputStream bufInput = new BufferedInputStream(new ByteArrayInputStream(source));
            while ((bytesRead = bufInput.read(buf)) != -1) {
                // write output
                out.write(buf, 0, bytesRead);
                out.flush();
                progress += bytesRead; // Here progress is total uploaded bytes

//                publishProgress(""+(int)((progress*100)/totalSize)); // sending progress percent to publishProgress
            }

            // Write closing boundary and close stream
            out.writeBytes(tail);
            out.flush();
            out.close();

            // Get server response
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line = "";
            StringBuilder builder = new StringBuilder();
            while((line = reader.readLine()) != null) {
                builder.append(line);
            }

            Log.d(TAG,builder.toString());

        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private InputStream convertMeasurementToInput(final @NonNull long measurementIdentifier) {
        resolver.
    }

    private byte[] serializeGeoLocations(final @NonNull long measurementIdentifier) {
        Cursor geoLocationsQueryCursor = null;
        try {
            geoLocationsQueryCursor = resolver.query(MeasuringPointsContentProvider.ROTATION_POINTS_URI, new String[]{GpsPointsTable.COLUMN_GPS_TIME, GpsPointsTable.COLUMN_LAT, GpsPointsTable.COLUMN_LON, GpsPointsTable.COLUMN_SPEED, GpsPointsTable.COLUMN_ACCURACY}, GpsPointsTable.COLUMN_MEASUREMENT_FK + "=?", new String[]{Long.valueOf(measurementIdentifier).toString()}, null);
            if(geoLocationsQueryCursor==null) {
                throw new IllegalStateException("Unable to query local data store.");
            }

            // Allocate enough space for all geo locations
            ByteBuffer buffer = ByteBuffer.allocate(geoLocationsQueryCursor.getCount()*(Long.BYTES + 3 * Double.BYTES + Integer.BYTES));

            while(geoLocationsQueryCursor.moveToNext()) {
                buffer.putLong(geoLocationsQueryCursor.getLong(geoLocationsQueryCursor.getColumnIndex(GpsPointsTable.COLUMN_GPS_TIME)));
                buffer.putDouble(geoLocationsQueryCursor.getDouble(geoLocationsQueryCursor.getColumnIndex(GpsPointsTable.COLUMN_LAT)));
                buffer.putDouble(geoLocationsQueryCursor.getDouble(geoLocationsQueryCursor.getColumnIndex(GpsPointsTable.COLUMN_LON)));
                buffer.putDouble(geoLocationsQueryCursor.getDouble(geoLocationsQueryCursor.getColumnIndex(GpsPointsTable.COLUMN_SPEED)));
                buffer.putInt(geoLocationsQueryCursor.getInt(geoLocationsQueryCursor.getColumnIndex(GpsPointsTable.COLUMN_ACCURACY)));
            }
            byte[] payload = new byte[buffer.capacity()];
            ((ByteBuffer) buffer.duplicate().clear()).get(payload);
            // if we want to switch from write to read mode on the byte buffer we need to .flip() !!
            return payload;
        } finally {
            if(geoLocationsQueryCursor!=null) {
                geoLocationsQueryCursor.close();
            }
        }
    }

    private byte[] serializeAccelerations(final @NonNull long measurementIdentifier) {
        Cursor accelerationsQueryCursor = null;
        try {
            accelerationsQueryCursor = resolver.query(MeasuringPointsContentProvider.SAMPLE_POINTS_URI,new String[]{SamplePointTable.COLUMN_TIME,SamplePointTable.COLUMN_AX,SamplePointTable.COLUMN_AY,SamplePointTable.COLUMN_AZ}, SamplePointTable.COLUMN_MEASUREMENT_FK + "=?",new String[]{Long.valueOf(measurementIdentifier).toString()},null);
            if(accelerationsQueryCursor==null) {
                throw new IllegalStateException("Unable to load accelerations from local data store!");
            }


            ByteBuffer buffer = ByteBuffer.allocate(accelerationsQueryCursor.getCount() * (Long.BYTES + 3 * Double.BYTES));
            while(accelerationsQueryCursor.moveToNext()) {
                buffer.putLong(accelerationsQueryCursor.getLong(accelerationsQueryCursor.getColumnIndex(SamplePointTable.COLUMN_TIME)));
                buffer.putDouble(accelerationsQueryCursor.getDouble(accelerationsQueryCursor.getColumnIndex(SamplePointTable.COLUMN_AX)));
                buffer.putDouble(accelerationsQueryCursor.getDouble(accelerationsQueryCursor.getColumnIndex(SamplePointTable.COLUMN_AY)));
                buffer.putDouble(accelerationsQueryCursor.getDouble(accelerationsQueryCursor.getColumnIndex(SamplePointTable.COLUMN_AZ)));
            }
            byte[] payload = new byte[buffer.capacity()];
            ((ByteBuffer) buffer.duplicate().clear()).get(payload);
            // if we want to switch from write to read mode on the byte buffer we need to .flip() !!
            return payload;
        } finally {
            if(accelerationsQueryCursor!=null) {
                accelerationsQueryCursor.close();
            }
        }
    }
}
