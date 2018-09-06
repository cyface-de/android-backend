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
import de.cyface.persistence.AccelerationPointTable;
import de.cyface.persistence.DirectionPointTable;
import de.cyface.persistence.RotationPointTable;

/**
 * A class responsible for writing captured sensor data to the underlying database.
 * <p>
 * This is a throw away runnable. You should never run this twice. Doing so will cause duplicates inside the
 * <code>ContentProvider</code>. Instead create a new instance per <code>CapturedData</code> to save.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 3.0.1
 * @since 1.0.0
 */
public class CapturedDataWriter implements Runnable {

    /**
     * The tag used to identify Logcat messages from this class.
     */
    private static final String TAG = CapturedDataWriter.class.getName();
    /**
     * The data to write.
     */
    private final CapturedData data;
    /**
     * The <code>ContentResolver</code> to use to access the <code>ContentProvider</code> to write the data to.
     */
    private final ContentResolver resolver;
    /**
     * The authority used to identify the Android content provider to write the data to.
     */
    private final String authority;
    /**
     * The device wide unique identifier of the measurement to write the data to.
     */
    private final long measurementIdentifier;
    /**
     * Callback which is called after writing data has finished.
     */
    private final WritingDataCompletedCallback callback;
    /**
     * Number of save operations to carry out in one batch. Increasing this value might increase performance but also
     * can lead to a {@link android.os.TransactionTooLargeException} on some mobile phones. The current value is the one
     * where all tests are finally passing on the Pixel 2.
     */
    private static final int MAX_SIMULTANEOUS_OPERATIONS = 550;

    /**
     * Creates a new completely initialized writer for captured data.
     *
     * @param data The data to write.
     * @param resolver The <code>ContentResolver</code> to use to access the <code>ContentProvider</code> to write the
     *            data to.
     * @param authority The authority used to identify the Android content provider to write the data to.
     * @param measurementIdentifier The device wide unique identifier of the measurement to write the data to.
     * @param callback Callback which is called after writing data has finished.
     */
    CapturedDataWriter(final @NonNull CapturedData data, final @NonNull ContentResolver resolver,
            final @NonNull String authority, final long measurementIdentifier,
            final @NonNull WritingDataCompletedCallback callback) {
        this.data = data;
        this.resolver = resolver;
        this.authority = authority;
        this.measurementIdentifier = measurementIdentifier;
        this.callback = callback;
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
                client = resolver.acquireContentProviderClient(authority);
                if (client == null) {
                    throw new DataCapturingException(
                            String.format("Unable to create client for content provider %s", authority));
                }

                insert(client, data.getAccelerations(),
                        new Uri.Builder().scheme("content").authority(authority).appendPath(AccelerationPointTable.URI_PATH)
                                .build(),
                        AccelerationPointTable.COLUMN_AX, AccelerationPointTable.COLUMN_AY, AccelerationPointTable.COLUMN_AZ,
                        AccelerationPointTable.COLUMN_IS_SYNCED, AccelerationPointTable.COLUMN_MEASUREMENT_FK,
                        AccelerationPointTable.COLUMN_TIME);

                insert(client, data.getRotations(),
                        new Uri.Builder().scheme("content").authority(authority).appendPath(RotationPointTable.URI_PATH)
                                .build(),
                        RotationPointTable.COLUMN_RX, RotationPointTable.COLUMN_RY, RotationPointTable.COLUMN_RZ,
                        RotationPointTable.COLUMN_IS_SYNCED, RotationPointTable.COLUMN_MEASUREMENT_FK,
                        RotationPointTable.COLUMN_TIME);

                insert(client, data.getDirections(),
                        new Uri.Builder().scheme("content").authority(authority)
                                .appendPath(DirectionPointTable.URI_PATH).build(),
                        DirectionPointTable.COLUMN_MX, DirectionPointTable.COLUMN_MY,
                        DirectionPointTable.COLUMN_MZ, DirectionPointTable.COLUMN_IS_SYNCED,
                        DirectionPointTable.COLUMN_MEASUREMENT_FK, DirectionPointTable.COLUMN_TIME);

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
        try {
            writeCapturedData();
        } finally {
            callback.writingDataCompleted();
        }
    }
}
