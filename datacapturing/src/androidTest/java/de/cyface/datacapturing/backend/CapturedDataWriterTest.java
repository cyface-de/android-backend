/*
 * Created on 12.08.15 at 16:31
 */
package de.cyface.datacapturing.backend;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.database.Cursor;
import android.provider.BaseColumns;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.test.ProviderTestCase2;
import android.util.Log;

import java.util.List;

import de.cyface.datacapturing.Measurement;
import de.cyface.datacapturing.model.Vehicle;
import de.cyface.datacapturing.persistence.MeasurementPersistence;
import de.cyface.persistence.MeasurementTable;
import de.cyface.persistence.MeasuringPointsContentProvider;
import de.cyface.persistence.BuildConfig;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Tests whether captured data is correctly saved to the underlying content provider.
 * The Integration tests need an Activity to be executed, this they are in an own module.
 *
 * (!) Create a custom Instrumented Test config for integration-test module to run this in the IDE !
 *
 * @author Klemens Muthmann
 * @version 2.0.0
 * @since 1.0.0
 */
@RunWith(AndroidJUnit4.class)
public class CapturedDataWriterTest extends ProviderTestCase2<MeasuringPointsContentProvider> {

    private final static String TAG = "de.cyface.test";

    /**
     * Constructor.
     */
    public CapturedDataWriterTest() {
        super(MeasuringPointsContentProvider.class, de.cyface.persistence.BuildConfig.provider);
    }

    @Before
    public void setUp() throws Exception {
        // WARNING: Never change the order of the following two lines, even though the Google documentation tells you
        // something different!
        setContext(InstrumentationRegistry.getTargetContext());
        super.setUp();
    }

    @Test
    public void testCreateNewMeasurement() {
        MeasurementPersistence persistence = new MeasurementPersistence(InstrumentationRegistry.getContext());
        long identifier = persistence.newMeasurement(Vehicle.UNKOWN);
        assertThat(identifier >= 0L, is(equalTo(true)));
        String identifierString = Long.valueOf(identifier).toString();
        Log.d(TAG, identifierString);

        List<Measurement> loadedMeasurements = persistence.loadMeasurements();
        for (Measurement measurement : loadedMeasurements) {
            Log.d(TAG, measurement.toString());
        }

        // TODO this test does not work yet, since the mock content resolver does not use the same content provider as
        // the content resolver inside MeasurementPersistence. Solution is to either inject content resolver into
        // MeasurementPersistence or test solely using the interface of MeasurementPersistence without accessing the
        // content resolver directly from this test.
        Cursor result = null;
        try {
            result = getMockContentResolver().query(MeasuringPointsContentProvider.MEASUREMENT_URI, null,
                    BaseColumns._ID + "=?", new String[] {identifierString}, null);
            assertThat(result.getCount(), is(equalTo(1)));
            assertThat(result.moveToFirst(), is(equalTo(true)));

            assertThat(result.getString(result.getColumnIndex(MeasurementTable.COLUMN_VEHICLE)),
                    is(equalTo(Vehicle.UNKOWN.getDatabaseIdentifier())));
            assertThat(result.getInt(result.getColumnIndex(MeasurementTable.COLUMN_FINISHED)), is(equalTo(0)));

        } finally {
            if (result != null) {
                result.close();
            }
        }

        persistence.closeRecentMeasurement();
        Cursor closingResult = null;
        try {
            closingResult = getMockContentResolver().query(MeasuringPointsContentProvider.MEASUREMENT_URI, null,
                    BaseColumns._ID + "=?", new String[] {identifierString}, null);
            assertThat(result.getCount(), is(equalTo(1)));
            assertThat(result.moveToFirst(), is(equalTo(true)));

            assertThat(result.getString(result.getColumnIndex(MeasurementTable.COLUMN_VEHICLE)),
                    is(equalTo(Vehicle.UNKOWN.getDatabaseIdentifier())));
            assertThat(result.getInt(result.getColumnIndex(MeasurementTable.COLUMN_FINISHED)), is(equalTo(1)));
        } finally {
            if (closingResult != null) {
                closingResult.close();
            }
        }
    }

    /*
     * @Test
     * public void testWriteData() {
     * ContentResolver resolver = getMockContentResolver();
     * Point3D[] values = new Point3D[] {new Point3D(0.25f, 0.25f, 0.25f, 1000L)};
     * CapturedDataWriter writer = new CapturedDataWriter(new CapturedData(51L, 13L, 1000L, 1.0, 300,
     * Arrays.asList(values), Arrays.asList(values), Arrays.asList(values)), resolver, 1L);
     * writer.writeCapturedData();
     * Cursor result = resolver.query(MeasuringPointsContentProvider.SAMPLE_POINTS_URI, null, null, null, null);
     * Assert.assertTrue(result.getCount() == 1);
     * }
     */
}