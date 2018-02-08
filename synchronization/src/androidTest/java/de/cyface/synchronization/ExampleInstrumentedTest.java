package de.cyface.synchronization;

import static junit.framework.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.ByteBuffer;

import de.cyface.persistence.GpsPointsTable;
import de.cyface.persistence.MagneticValuePointTable;
import de.cyface.persistence.MeasurementTable;
import de.cyface.persistence.MeasuringPointsContentProvider;
import de.cyface.persistence.RotationPointTable;
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
        long measurementIdentifier = insertTestMeasurement(resolver,"UNKOWN");
        insertTestGeoLocation(resolver,measurementIdentifier,1503055141000L,49.9304133333333,8.82831833333333,0.0,940);
        insertTestGeoLocation(resolver,measurementIdentifier,1503055142000L,49.9305066666667,8.82814,8.78270530700684,840);
        insertTestAcceleration(resolver,measurementIdentifier,1501662635973L,10.1189575,-0.15088624,0.2921924);
        insertTestAcceleration(resolver,measurementIdentifier,1501662635981L,10.116563,-0.16765137,0.3544629);
        insertTestAcceleration(resolver,measurementIdentifier,1501662635983L,10.171648,-0.2921924,0.3784131);
        insertTestRotation(resolver,measurementIdentifier,1501662635981L,0.001524045,0.0025423833,-0.0010279021);
        insertTestRotation(resolver,measurementIdentifier,1501662635990L,0.001524045,0.0025423833,-0.016474236);
        insertTestRotation(resolver,measurementIdentifier,1501662635993L,-0.0064654383,-0.0219587,-0.014343708);
        insertTestDirection(resolver,measurementIdentifier,1501662636010L,7.65,-32.4,-71.4);
        insertTestDirection(resolver,measurementIdentifier,1501662636030L,7.65,-32.550003,-71.700005);
        insertTestDirection(resolver,measurementIdentifier,1501662636050L,7.65,-33.15,-71.700005);

        InputStream measurementData = convertMeasurementToInput(measurementIdentifier);
        sendMovebisData(measurementIdentifier, "garbage", measurementData, new UploadProgressListener() {
            @Override
            public void updatedProgress(float percent) {
                Log.d(TAG,String.format("Upload Progress %f",percent));
            }
        });
    }

    private void insertTestDirection(ContentResolver resolver, long measurementIdentifier, long timestamp, double x, double y, double z) {
        ContentValues values = new ContentValues();
        values.put(MagneticValuePointTable.COLUMN_MEASUREMENT_FK,measurementIdentifier);
        values.put(MagneticValuePointTable.COLUMN_IS_SYNCED,false);
        values.put(MagneticValuePointTable.COLUMN_MX,x);
        values.put(MagneticValuePointTable.COLUMN_MY,y);
        values.put(MagneticValuePointTable.COLUMN_MZ,z);
        values.put(MagneticValuePointTable.COLUMN_TIME,timestamp);
        resolver.insert(MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI,values);
    }

    private void insertTestRotation(ContentResolver resolver, long measurementIdentifier, long timestamp, double x, double y, double z) {
        ContentValues values = new ContentValues();
        values.put(RotationPointTable.COLUMN_MEASUREMENT_FK,measurementIdentifier);
        values.put(RotationPointTable.COLUMN_IS_SYNCED,false);
        values.put(RotationPointTable.COLUMN_RX,x);
        values.put(RotationPointTable.COLUMN_RY,y);
        values.put(RotationPointTable.COLUMN_RZ,z);
        values.put(RotationPointTable.COLUMN_TIME,timestamp);
        resolver.insert(MeasuringPointsContentProvider.ROTATION_POINTS_URI,values);
    }

    private void insertTestAcceleration(ContentResolver resolver, long measurementIdentifier, long timestamp, double x, double y, double z) {
        ContentValues values = new ContentValues();
        values.put(SamplePointTable.COLUMN_MEASUREMENT_FK,measurementIdentifier);
        values.put(SamplePointTable.COLUMN_IS_SYNCED,false);
        values.put(SamplePointTable.COLUMN_AX,x);
        values.put(SamplePointTable.COLUMN_AY,y);
        values.put(SamplePointTable.COLUMN_AZ,z);
        values.put(SamplePointTable.COLUMN_TIME,timestamp);
        resolver.insert(MeasuringPointsContentProvider.SAMPLE_POINTS_URI,values);
    }

    private void insertTestGeoLocation(ContentResolver resolver, long measurementIdentifier, long timestamp, double lat, double lon, double speed, int accuracy) {
        ContentValues values = new ContentValues();
        values.put(GpsPointsTable.COLUMN_ACCURACY,accuracy);
        values.put(GpsPointsTable.COLUMN_GPS_TIME,timestamp);
        values.put(GpsPointsTable.COLUMN_IS_SYNCED,false);
        values.put(GpsPointsTable.COLUMN_LAT,lat);
        values.put(GpsPointsTable.COLUMN_LON,lon);
        values.put(GpsPointsTable.COLUMN_MEASUREMENT_FK,measurementIdentifier);
        values.put(GpsPointsTable.COLUMN_SPEED,speed);
        resolver.insert(MeasuringPointsContentProvider.GPS_POINTS_URI,values);
    }

    private long insertTestMeasurement(ContentResolver resolver, String vehicle) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTable.COLUMN_FINISHED,true);
        values.put(MeasurementTable.COLUMN_VEHICLE,vehicle);
        Uri resultUri = resolver.insert(MeasuringPointsContentProvider.MEASUREMENT_URI,values);
        return Long.parseLong(resultUri.getLastPathSegment());
    }

    public void sendMovebisData(final long measurementIdentifier, final String deviceIdentifier, final @NonNull InputStream data,
            final @NonNull UploadProgressListener progressListener) {
        HttpURLConnection.setFollowRedirects(false);
        HttpURLConnection connection = null;
        String fileName = String.format("%s_%d.cyf",deviceIdentifier,measurementIdentifier);

        try {
            try {
                connection = (HttpURLConnection)new URL("http://141.76.40.152:8080/measurements").openConnection();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            try {
                connection.setRequestMethod("POST");
            } catch (ProtocolException e) {
                throw new IllegalStateException(e);
            }
            String boundary = "---------------------------boundary";
            String tail = "\r\n--" + boundary + "--\r\n";
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            connection.setDoOutput(true);

//            String metadataPart = "--" + boundary + "\r\n" + "Content-Disposition: form-data; name=\"metadata\"\r\n\r\n"
//                    + "" + "\r\n";
            String userIdPart = addPart("userId",deviceIdentifier,boundary);
            String measurementIdPart = addPart("measurementId",Long.valueOf(measurementIdentifier).toString(),boundary);

            String fileHeader1 = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"" + fileName + "\"\r\n"
                    + "Content-Type: application/octet-stream\r\n" + "Content-Transfer-Encoding: binary\r\n";

            // byte[] source = "test".getBytes();
            // long fileLength = source.length + tail.length();
            // FIXME This will only work correctly as long as we are using a ByteArrayInputStream. For other streams it
            // returns only the data currently in memory or something similar.
            int dataSize = 0;
            try {
                dataSize = data.available()+tail.length();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            String fileHeader2 = "Content-length: " + dataSize + "\r\n";
            String fileHeader = fileHeader1 + fileHeader2 + "\r\n";
            String stringData = userIdPart + measurementIdPart + fileHeader;

            long requestLength = stringData.length() + dataSize;
            connection.setRequestProperty("Content-length", "" + requestLength);
            connection.setFixedLengthStreamingMode((int)requestLength);
            try {
                connection.connect();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }

            DataOutputStream out = null;
            try {
                out = new DataOutputStream(connection.getOutputStream());
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            try {
                out.writeBytes(stringData);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            try {
                out.flush();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }

            int progress = 0;
            int bytesRead = 0;
            byte buf[] = new byte[1024];
            BufferedInputStream bufInput = new BufferedInputStream(data);
            try {
                while ((bytesRead = bufInput.read(buf)) != -1) {
                    // write output
                    out.write(buf, 0, bytesRead);
                    out.flush();
                    progress += bytesRead; // Here progress is total uploaded bytes
                    progressListener.updatedProgress((progress * 100) / dataSize);

                    // publishProgress(""+(int)((progress*100)/totalSize)); // sending progress percent to publishProgress
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }

            // Write closing boundary and close stream
            try {
                out.writeBytes(tail);
                out.flush();
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Get server response
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line = "";
                StringBuilder builder = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }

                Log.d(TAG, builder.toString());
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }

        } finally {
            if (connection != null)
                connection.disconnect();
        }
    }

    private String addPart(final @NonNull String key, final @NonNull String value, final @NonNull String boundary) {
        return String.format("--%s\r\nContent-Disposition: form-data; name=\"%s\"\r\n\r\n\r\n%s",boundary,key,value);
    }

    private InputStream convertMeasurementToInput(final long measurementIdentifier) {
        byte[] serializedGeoLocations = serializeGeoLocations(measurementIdentifier);

        Point3DSerializer accelerationsSerializer = new Point3DSerializer(resolver) {
            @Override
            protected Uri getTableUri() {
                return MeasuringPointsContentProvider.SAMPLE_POINTS_URI;
            }

            @Override
            protected String getXColumnName() {
                return SamplePointTable.COLUMN_AX;
            }

            @Override
            protected String getYColumnName() {
                return SamplePointTable.COLUMN_AY;
            }

            @Override
            protected String getZColumnName() {
                return SamplePointTable.COLUMN_AZ;
            }

            @Override
            protected String getMeasurementKeyColumnName() {
                return SamplePointTable.COLUMN_MEASUREMENT_FK;
            }

            @Override
            protected String getTimestampColumnName() {
                return SamplePointTable.COLUMN_TIME;
            }
        };
        Point3DSerializer rotationsSerializer = new Point3DSerializer(resolver) {
            @Override
            protected Uri getTableUri() {
                return MeasuringPointsContentProvider.ROTATION_POINTS_URI;
            }

            @Override
            protected String getXColumnName() {
                return RotationPointTable.COLUMN_RX;
            }

            @Override
            protected String getYColumnName() {
                return RotationPointTable.COLUMN_RY;
            }

            @Override
            protected String getZColumnName() {
                return RotationPointTable.COLUMN_RZ;
            }

            @Override
            protected String getMeasurementKeyColumnName() {
                return RotationPointTable.COLUMN_MEASUREMENT_FK;
            }

            @Override
            protected String getTimestampColumnName() {
                return RotationPointTable.COLUMN_TIME;
            }
        };
        Point3DSerializer directionsSerializer = new Point3DSerializer(resolver) {
            @Override
            protected Uri getTableUri() {
                return MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI;
            }

            @Override
            protected String getXColumnName() {
                return MagneticValuePointTable.COLUMN_MX;
            }

            @Override
            protected String getYColumnName() {
                return MagneticValuePointTable.COLUMN_MY;
            }

            @Override
            protected String getZColumnName() {
                return MagneticValuePointTable.COLUMN_MZ;
            }

            @Override
            protected String getMeasurementKeyColumnName() {
                return MagneticValuePointTable.COLUMN_MEASUREMENT_FK;
            }

            @Override
            protected String getTimestampColumnName() {
                return MagneticValuePointTable.COLUMN_TIME;
            }
        };

        byte[] serializedAccelerations = accelerationsSerializer.serialize(measurementIdentifier);
        byte[] serializedRotations = rotationsSerializer.serialize(measurementIdentifier);
        byte[] serializedDirections = directionsSerializer.serialize(measurementIdentifier);

        ByteBuffer buffer = ByteBuffer.allocate(serializedAccelerations.length + serializedAccelerations.length + serializedRotations.length + serializedDirections.length);
        buffer.put(serializedGeoLocations);
        buffer.put(serializedAccelerations);
        buffer.put(serializedRotations);
        buffer.put(serializedDirections);

        return new ByteArrayInputStream(buffer.array());
    }

    private byte[] serializeGeoLocations(final long measurementIdentifier) {
        Cursor geoLocationsQueryCursor = null;
        try {
            geoLocationsQueryCursor = resolver.query(MeasuringPointsContentProvider.GPS_POINTS_URI,
                    new String[] {GpsPointsTable.COLUMN_GPS_TIME, GpsPointsTable.COLUMN_LAT, GpsPointsTable.COLUMN_LON,
                            GpsPointsTable.COLUMN_SPEED, GpsPointsTable.COLUMN_ACCURACY},
                    GpsPointsTable.COLUMN_MEASUREMENT_FK + "=?",
                    new String[] {Long.valueOf(measurementIdentifier).toString()}, null);
            if (geoLocationsQueryCursor == null) {
                throw new IllegalStateException("Unable to query local data store.");
            }

            // Allocate enough space for all geo locations
            ByteBuffer buffer = ByteBuffer
                    .allocate(geoLocationsQueryCursor.getCount() * (Long.BYTES + 3 * Double.BYTES + Integer.BYTES));

            while (geoLocationsQueryCursor.moveToNext()) {
                buffer.putLong(geoLocationsQueryCursor
                        .getLong(geoLocationsQueryCursor.getColumnIndex(GpsPointsTable.COLUMN_GPS_TIME)));
                buffer.putDouble(geoLocationsQueryCursor
                        .getDouble(geoLocationsQueryCursor.getColumnIndex(GpsPointsTable.COLUMN_LAT)));
                buffer.putDouble(geoLocationsQueryCursor
                        .getDouble(geoLocationsQueryCursor.getColumnIndex(GpsPointsTable.COLUMN_LON)));
                buffer.putDouble(geoLocationsQueryCursor
                        .getDouble(geoLocationsQueryCursor.getColumnIndex(GpsPointsTable.COLUMN_SPEED)));
                buffer.putInt(geoLocationsQueryCursor
                        .getInt(geoLocationsQueryCursor.getColumnIndex(GpsPointsTable.COLUMN_ACCURACY)));
            }
            byte[] payload = new byte[buffer.capacity()];
            ((ByteBuffer)buffer.duplicate().clear()).get(payload);
            // if we want to switch from write to read mode on the byte buffer we need to .flip() !!
            return payload;
        } finally {
            if (geoLocationsQueryCursor != null) {
                geoLocationsQueryCursor.close();
            }
        }
    }

    private abstract static class Point3DSerializer {

        private ContentResolver resolver;

        public Point3DSerializer(final @NonNull ContentResolver resolver) {
            this.resolver = resolver;
        }

        protected abstract Uri getTableUri();

        protected abstract String getXColumnName();

        protected abstract String getYColumnName();

        protected abstract String getZColumnName();

        protected abstract String getMeasurementKeyColumnName();

        protected abstract String getTimestampColumnName();

        byte[] serialize(final long measurementIdentifier) {
            Cursor queryCursor = null;
            try {
                queryCursor = resolver.query(getTableUri(),
                        new String[] {getTimestampColumnName(), getXColumnName(), getYColumnName(), getZColumnName()},
                        getMeasurementKeyColumnName() + "=?",
                        new String[] {Long.valueOf(measurementIdentifier).toString()}, null);
                if (queryCursor == null) {
                    throw new IllegalStateException("Unable to load accelerations from local data store!");
                }

                ByteBuffer buffer = ByteBuffer.allocate(queryCursor.getCount() * (Long.BYTES + 3 * Double.BYTES));
                while (queryCursor.moveToNext()) {
                    buffer.putLong(queryCursor.getLong(queryCursor.getColumnIndex(getTimestampColumnName())));
                    buffer.putDouble(queryCursor.getDouble(queryCursor.getColumnIndex(getXColumnName())));
                    buffer.putDouble(queryCursor.getDouble(queryCursor.getColumnIndex(getYColumnName())));
                    buffer.putDouble(queryCursor.getDouble(queryCursor.getColumnIndex(getZColumnName())));
                }
                byte[] payload = new byte[buffer.capacity()];
                ((ByteBuffer)buffer.duplicate().clear()).get(payload);
                // if we want to switch from write to read mode on the byte buffer we need to .flip() !!
                return payload;
            } finally {
                if (queryCursor != null) {
                    queryCursor.close();
                }
            }
        }
    }
}

interface UploadProgressListener {
    void updatedProgress(float percent);
}
