package de.cyface.synchronization;

import static de.cyface.synchronization.TestUtils.AUTHORITY;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;

import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.BaseColumns;
import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import de.cyface.persistence.MeasurementContentProviderClient;
import de.cyface.persistence.MeasurementTable;
import de.cyface.persistence.MeasuringPointsContentProvider;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.utils.Validate;

/**
 * Tests the correct internal workings of the <code>SyncAdapter</code>.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 2.0.0
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 16, manifest = Config.NONE)
public class SyncAdapterTest {

    /**
     * The Android mockedContext to use for this test case.
     */
    private Context context;

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
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
        ContentProviderClient client = null;
        Uri measurementUri = new Uri.Builder().scheme("content").authority(AUTHORITY)
                .appendPath(MeasurementTable.URI_PATH).build();
        try {
            client = context.getContentResolver().acquireContentProviderClient(measurementUri);
            if (client == null) {
                throw new IllegalStateException("ContentProviderClient was null.");
            }

            ContentValues notFinishedMeasurementValues = createMeasurement(MeasurementStatus.OPEN);
            ContentValues notSyncedMeasurementValues = createMeasurement(MeasurementStatus.FINISHED);
            ContentValues syncedMeasurementValues = createMeasurement(MeasurementStatus.SYNCED);
            client.insert(measurementUri, notFinishedMeasurementValues);
            Uri result = client.insert(measurementUri, notSyncedMeasurementValues);
            Validate.notNull("Unable to insert measurement into ContentProvider.", result);

            Validate.notNull(result.getLastPathSegment());
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
                // noinspection deprecation - because Roboelectric returns "NoSuchMeasurementError" in version 4.1
                client.release();
            }
        }

    }

    /**
     * Creates the <code>ContentValues</code> object required to insert a new measurement ot a content provider.
     *
     * @param status The {@link MeasurementStatus} in which the measurement should be created.
     * @return The prepared <code>ContentValues</code> for inserting into a content provider.
     */
    private ContentValues createMeasurement(@NonNull final MeasurementStatus status) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTable.COLUMN_STATUS, status.getDatabaseIdentifier());
        return values;
    }

}
