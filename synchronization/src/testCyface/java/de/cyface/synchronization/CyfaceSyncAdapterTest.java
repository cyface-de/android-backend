package de.cyface.synchronization;

import static de.cyface.synchronization.TestUtils.AUTHORITY;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;

import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.BaseColumns;

import de.cyface.persistence.MeasurementTable;
import de.cyface.persistence.MeasuringPointsContentProvider;

/**
 * Tests the correct internal workings of the <code>CyfaceSyncAdapter</code>.
 *
 * @author Klemens Muthmann
 * @version 1.0.1
 * @since 2.0.0
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 16, manifest = Config.NONE)
public class CyfaceSyncAdapterTest {

    /**
     * The Android context to use for this test case.
     */
    private Context context;

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    @Mock
    Http httpConnection;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.application;
        ShadowContentResolver.registerProviderInternal(AUTHORITY,
                Robolectric.setupContentProvider(MeasuringPointsContentProvider.class));
    }

    /**
     * Tests whether the sync adapter loads the correct measurements for synchronization.
     *
     * @throws RemoteException If accessing the Android content provider fails.
     */
    @Test
    public void testGetSyncableMeasurement() throws RemoteException {
        CyfaceSyncAdapter oocut = new CyfaceSyncAdapter(context, false, httpConnection, 10_000, 10_000, 10_000, 10_000);
        ContentProviderClient client = null;
        Uri measurementUri = new Uri.Builder().scheme("content").authority(AUTHORITY)
                .appendPath(MeasurementTable.URI_PATH).build();
        try {
            client = context.getContentResolver().acquireContentProviderClient(measurementUri);
            if (client == null) {
                throw new IllegalStateException("ContentProviderClient was null.");
            }

            ContentValues notFinishedMeasurementValues = createMeasurement(false, false);
            ContentValues notSyncedMeasurementValues = createMeasurement(false, true);
            ContentValues syncedMeasurementValues = createMeasurement(true, true);
            client.insert(measurementUri, notFinishedMeasurementValues);
            Uri result = client.insert(measurementUri, notSyncedMeasurementValues);
            if (result == null) {
                throw new IllegalStateException("Unable to insert measurement into ContentProvider.");
            }

            long expectedIdentifier = Long.parseLong(result.getLastPathSegment());
            client.insert(measurementUri, syncedMeasurementValues);

            Cursor syncableMeasurementsCursor = MeasurementContentProviderClient.loadSyncableMeasurements(client,
                    AUTHORITY);

            assertThat(syncableMeasurementsCursor.getCount(), is(1));
            syncableMeasurementsCursor.moveToFirst();
            assertThat(syncableMeasurementsCursor.getLong(syncableMeasurementsCursor.getColumnIndex(BaseColumns._ID)),
                    is(expectedIdentifier));
        } finally {
            if (client != null) {
                client.release();
            }
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
