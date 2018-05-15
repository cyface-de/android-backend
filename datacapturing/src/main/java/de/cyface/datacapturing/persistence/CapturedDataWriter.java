package de.cyface.datacapturing.persistence;

import java.util.Arrays;
import java.util.List;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.DeadObjectException;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.util.Log;

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
 * @version 1.0.1
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

                insert(client, data.getAccelerations(), MeasuringPointsContentProvider.SAMPLE_POINTS_URI,
                        SamplePointTable.COLUMN_AX, SamplePointTable.COLUMN_AY, SamplePointTable.COLUMN_AZ,
                        SamplePointTable.COLUMN_IS_SYNCED, SamplePointTable.COLUMN_MEASUREMENT_FK,
                        SamplePointTable.COLUMN_TIME);

                insert(client, data.getRotations(), MeasuringPointsContentProvider.ROTATION_POINTS_URI,
                        RotationPointTable.COLUMN_RX, RotationPointTable.COLUMN_RY, RotationPointTable.COLUMN_RZ,
                        RotationPointTable.COLUMN_IS_SYNCED, RotationPointTable.COLUMN_MEASUREMENT_FK,
                        RotationPointTable.COLUMN_TIME);

                insert(client, data.getDirections(), MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI,
                        MagneticValuePointTable.COLUMN_MX, MagneticValuePointTable.COLUMN_MY,
                        MagneticValuePointTable.COLUMN_MZ, MagneticValuePointTable.COLUMN_IS_SYNCED,
                        MagneticValuePointTable.COLUMN_MEASUREMENT_FK, MagneticValuePointTable.COLUMN_TIME);

            } catch (DataCapturingException e) {
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

    private void insert(final ContentProviderClient client, final List<Point3D> pointData,
            final @NonNull Uri pointUriColumnName, final @NonNull String xColumnName, final @NonNull String yColumnName,
            final @NonNull String zColumnName, final @NonNull String isSyncedColumnName,
            final @NonNull String mFkColumnName, final @NonNull String timeColumnName) throws RemoteException {
        ContentValues[] values = new ContentValues[pointData.size()];
        for (int i = 0; i < pointData.size(); i++) {
            Point3D dataPoint = pointData.get(i);
            ContentValues ret = new ContentValues();
            ret.put(xColumnName, dataPoint.getX());
            ret.put(yColumnName, dataPoint.getY());
            ret.put(zColumnName, dataPoint.getZ());
            ret.put(isSyncedColumnName, 0);
            ret.put(mFkColumnName, measurementIdentifier);
            ret.put(timeColumnName, dataPoint.getTimestamp());
            values[i] = ret;
        }
        for (int startIndex = 0; startIndex < values.length; startIndex += MAX_SIMULTANEOUS_OPERATIONS) {
            int endIndex = Math.min(values.length, startIndex + MAX_SIMULTANEOUS_OPERATIONS);
            // BulkInsert is about 80 times faster than insertBatch
            client.bulkInsert(pointUriColumnName, Arrays.copyOfRange(values, startIndex, endIndex));
        }
    }

    @Override
    public void run() {
        writeCapturedData();
    }
}
