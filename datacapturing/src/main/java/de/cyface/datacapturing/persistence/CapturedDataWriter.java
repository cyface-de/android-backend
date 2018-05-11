package de.cyface.datacapturing.persistence;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.os.Build;
import android.os.DeadObjectException;
import android.os.RemoteException;
import android.util.Log;

import java.util.Arrays;

import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.model.CapturedData;
import de.cyface.datacapturing.model.Point3D;
import de.cyface.persistence.MagneticValuePointTable;
import de.cyface.persistence.MeasuringPointsContentProvider;
import de.cyface.persistence.RotationPointTable;
import de.cyface.persistence.SamplePointTable;

/**
 * A class responsible for writing captured sensor data to the underlying database.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 1.0.0
 */
public class CapturedDataWriter implements Runnable {

    private static final String TAG = CapturedDataWriter.class.getName();
    private final CapturedData data;
    private final ContentResolver resolver;
    private final long measurementIdentifier;
    /**
     * Number of save operations to carry out in one batch. Increasing this value might increase performance but also
     * can lead to a {@link android.os.TransactionTooLargeException} on some mobile phones. The current value is the one
     * where all tests are finally passing on the Pixel 2.
     */
    public static final int MAX_SIMULTANEOUS_OPERATIONS = 550;

    CapturedDataWriter(final CapturedData data, final ContentResolver resolver, final long measurementIdentifier) {
        if (data == null || resolver == null) {
            throw new IllegalStateException("CapturedDataWrite called without data or resolver.");
        }

        this.data = data;
        this.resolver = resolver;
        this.measurementIdentifier = measurementIdentifier;
    }

    /**
     * Even though the ContentResolver is easier to use, we use the ContentProviderClient as it is
     * faster when you execute multiple operations. (https://stackoverflow.com/a/5233631/5815054)
     * - It's essential to create a new client for each thread and to close the client after usage,
     * as the client is not thread safe, see:
     * https://developer.android.com/reference/android/content/ContentProviderClient
     */
    private void writeCapturedData() {

        Log.d(TAG, "bulkInserting " + data.getAccelerations().size() + "/" + data.getRotations().size() + "/"
                + data.getDirections().size() + " A/R/MPs on: " + Thread.currentThread().getName());

        try {
            ContentProviderClient client = null;
            try {
                client = resolver.acquireContentProviderClient(MeasuringPointsContentProvider.AUTHORITY);
                if (client == null) {
                    throw new DataCapturingException(String.format("Unable to create client for content provider %s",
                            MeasuringPointsContentProvider.AUTHORITY));
                }

                ContentValues[] values = new ContentValues[data.getAccelerations().size()];
                for (int i = 0; i < data.getAccelerations().size(); i++) {
                    Point3D dataPoint = data.getAccelerations().get(i);
                    ContentValues ret = new ContentValues();
                    ret.put(SamplePointTable.COLUMN_AX, dataPoint.getX());
                    ret.put(SamplePointTable.COLUMN_AY, dataPoint.getY());
                    ret.put(SamplePointTable.COLUMN_AZ, dataPoint.getZ());
                    ret.put(SamplePointTable.COLUMN_IS_SYNCED, 0);
                    ret.put(SamplePointTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
                    ret.put(SamplePointTable.COLUMN_TIME, dataPoint.getTimestamp());
                    values[i] = ret;
                }
                for (int startIndex = 0; startIndex < values.length; startIndex += MAX_SIMULTANEOUS_OPERATIONS) {
                    int endIndex = Math.min(values.length, startIndex + MAX_SIMULTANEOUS_OPERATIONS);
                    // BulkInsert is about 80 times faster than insertBatch
                    client.bulkInsert(MeasuringPointsContentProvider.SAMPLE_POINTS_URI,
                            Arrays.copyOfRange(values, startIndex, endIndex));
                }

                values = new ContentValues[data.getRotations().size()];
                for (int i = 0; i < data.getRotations().size(); i++) {
                    Point3D dataPoint = data.getRotations().get(i);
                    ContentValues ret = new ContentValues();
                    ret.put(RotationPointTable.COLUMN_RX, dataPoint.getX());
                    ret.put(RotationPointTable.COLUMN_RY, dataPoint.getY());
                    ret.put(RotationPointTable.COLUMN_RZ, dataPoint.getZ());
                    ret.put(RotationPointTable.COLUMN_IS_SYNCED, 0);
                    ret.put(RotationPointTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
                    ret.put(RotationPointTable.COLUMN_TIME, dataPoint.getTimestamp());
                    values[i] = ret;
                }
                for (int startIndex = 0; startIndex < values.length; startIndex += MAX_SIMULTANEOUS_OPERATIONS) {
                    int endIndex = Math.min(values.length, startIndex + MAX_SIMULTANEOUS_OPERATIONS);
                    // BulkInsert is about 80 times faster than insertBatch
                    client.bulkInsert(MeasuringPointsContentProvider.ROTATION_POINTS_URI,
                            Arrays.copyOfRange(values, startIndex, endIndex));
                }

                values = new ContentValues[data.getDirections().size()];
                for (int i = 0; i < data.getDirections().size(); i++) {
                    Point3D dataPoint = data.getDirections().get(i);
                    ContentValues ret = new ContentValues();
                    ret.put(MagneticValuePointTable.COLUMN_MX, dataPoint.getX());
                    ret.put(MagneticValuePointTable.COLUMN_MY, dataPoint.getY());
                    ret.put(MagneticValuePointTable.COLUMN_MZ, dataPoint.getZ());
                    ret.put(MagneticValuePointTable.COLUMN_IS_SYNCED, 0);
                    ret.put(MagneticValuePointTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
                    ret.put(MagneticValuePointTable.COLUMN_TIME, dataPoint.getTimestamp());
                    values[i] = ret;
                }
                for (int startIndex = 0; startIndex < values.length; startIndex += MAX_SIMULTANEOUS_OPERATIONS) {
                    int endIndex = Math.min(values.length, startIndex + MAX_SIMULTANEOUS_OPERATIONS);
                    // BulkInsert is about 80 times faster than insertBatch
                    client.bulkInsert(MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI,
                            Arrays.copyOfRange(values, startIndex, endIndex));
                }

            } catch (DataCapturingException e) {
                // TODO: not sure if this is ok according to our SDK API?
                throw new IllegalStateException(e);
            } finally {
                if (client != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        client.close();
                    } else {
                        client.release();
                    }
                }
            }
        } catch (DeadObjectException e) {
            Log.e(TAG, "Binder buffer full. Cannot save data.", e);
        } catch (RemoteException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void run() {
        writeCapturedData();
    }
}
