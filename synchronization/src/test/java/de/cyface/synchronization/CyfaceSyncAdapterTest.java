package de.cyface.synchronization;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;

import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.RemoteException;
import android.provider.BaseColumns;

import de.cyface.persistence.BuildConfig;
import de.cyface.persistence.MeasurementTable;
import de.cyface.persistence.MeasuringPointsContentProvider;

/**
 * Tests the correct internal workings of the <code>CyfaceSyncAdapter</code>.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 16, manifest = Config.NONE)
public class CyfaceSyncAdapterTest {

    /**
     * The Android context to use for this test case.
     */
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.application;
        ShadowContentResolver.registerProviderInternal(BuildConfig.provider,
                Robolectric.setupContentProvider(MeasuringPointsContentProvider.class));
    }

    /**
     * Tests whether the sync adapter loads the correct measurements for synchronization.
     *
     * @throws RemoteException If accessing the Android content provider fails.
     */
    @Test
    public void testGetSyncableMeasurement() throws RemoteException {
        CyfaceSyncAdapter oocut = new CyfaceSyncAdapter(context, false);
        ContentProviderClient client = null;
        try {
            client = context.getContentResolver()
                    .acquireContentProviderClient(MeasuringPointsContentProvider.MEASUREMENT_URI);
            ContentValues notFinishedMeasurementValues = createMeasurement(false, false);
            ContentValues notSyncedMeasurementValues = createMeasurement(false, true);
            ContentValues syncedMeasurementValues = createMeasurement(true, true);
            client.insert(MeasuringPointsContentProvider.MEASUREMENT_URI, notFinishedMeasurementValues);
            long expectedIdentifier = Long
                    .parseLong(client.insert(MeasuringPointsContentProvider.MEASUREMENT_URI, notSyncedMeasurementValues)
                            .getLastPathSegment());
            client.insert(MeasuringPointsContentProvider.MEASUREMENT_URI, syncedMeasurementValues);

            Cursor syncableMeasurementsCursor = oocut.loadSyncableMeasurements(client);

            assertThat(syncableMeasurementsCursor.getCount(), is(1));
            syncableMeasurementsCursor.moveToFirst();
            assertThat(syncableMeasurementsCursor.getLong(syncableMeasurementsCursor.getColumnIndex(BaseColumns._ID)),
                    is(expectedIdentifier));
        } finally {
            client.release();
        }

    }

    /**
     * Creates the <code>ContentValues</code> object required to insert a new measurement ot a content provider.
     *
     * @param isSynced If <code>true</code> the measurement has been synced; if <code>false</code> not.
     * @param isFinished If <code>true</code> the measurement has finished data capturing; if <code>false</code> not.
     * @return The prepared <code>ContentValues</code> for inserting into a content provider.
     */
    private ContentValues createMeasurement(final boolean isSynced, final boolean isFinished) {
        ContentValues values = new ContentValues();

        values.put(MeasurementTable.COLUMN_SYNCED, isSynced);
        values.put(MeasurementTable.COLUMN_FINISHED, isFinished);

        return values;
    }

}
