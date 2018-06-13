package de.cyface.synchronization;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;

import de.cyface.persistence.BuildConfig;
import de.cyface.persistence.GpsPointsTable;
import de.cyface.persistence.MagneticValuePointTable;
import de.cyface.persistence.MeasurementTable;
import de.cyface.persistence.MeasuringPointsContentProvider;
import de.cyface.persistence.RotationPointTable;
import de.cyface.persistence.SamplePointTable;

/**
 * Created by muthmann on 05.03.18.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public final class ContentProviderClientTest {

    @Test
    public void test() throws RemoteException {
        Context context = InstrumentationRegistry.getTargetContext();
        ContentProviderClient client = null;
        Cursor loadedAccelerations = null;
        Cursor loadedRotations = null;
        Cursor loadedDirections = null;
        Cursor loadedEmptyAccelerations = null;
        Cursor loadedEmptyRotations = null;
        Cursor loadedEmptyDirections = null;

        try {
            client = context.getContentResolver().acquireContentProviderClient(BuildConfig.provider);

            ContentValues measurementValues = new ContentValues();
            measurementValues.put(MeasurementTable.COLUMN_VEHICLE, "BICYCLE");
            measurementValues.put(MeasurementTable.COLUMN_FINISHED, 1);
            Uri result = client.insert(MeasuringPointsContentProvider.MEASUREMENT_URI, measurementValues);
            long measurementIdentifier = Long.parseLong(result.getLastPathSegment());

            ContentValues geoLocationValues = new ContentValues();
            geoLocationValues.put(GpsPointsTable.COLUMN_SPEED, 1.0);
            geoLocationValues.put(GpsPointsTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
            geoLocationValues.put(GpsPointsTable.COLUMN_LON, 1.0);
            geoLocationValues.put(GpsPointsTable.COLUMN_LAT, 1.0);
            geoLocationValues.put(GpsPointsTable.COLUMN_IS_SYNCED, 0);
            geoLocationValues.put(GpsPointsTable.COLUMN_GPS_TIME, 1L);
            geoLocationValues.put(GpsPointsTable.COLUMN_ACCURACY, 1);
            client.insert(MeasuringPointsContentProvider.GPS_POINTS_URI, geoLocationValues);
            client.insert(MeasuringPointsContentProvider.GPS_POINTS_URI, geoLocationValues);

            ContentValues samplePointValues = new ContentValues();
            samplePointValues.put(SamplePointTable.COLUMN_AX, 1.0);
            samplePointValues.put(SamplePointTable.COLUMN_AY, 1.0);
            samplePointValues.put(SamplePointTable.COLUMN_AZ, 1.0);
            samplePointValues.put(SamplePointTable.COLUMN_TIME, 1L);
            samplePointValues.put(SamplePointTable.COLUMN_IS_SYNCED, 0);
            samplePointValues.put(SamplePointTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
            client.insert(MeasuringPointsContentProvider.SAMPLE_POINTS_URI, samplePointValues);
            client.insert(MeasuringPointsContentProvider.SAMPLE_POINTS_URI, samplePointValues);
            client.insert(MeasuringPointsContentProvider.SAMPLE_POINTS_URI, samplePointValues);

            ContentValues rotationPointValues = new ContentValues();
            rotationPointValues.put(RotationPointTable.COLUMN_RX, 1.0);
            rotationPointValues.put(RotationPointTable.COLUMN_RY, 1.0);
            rotationPointValues.put(RotationPointTable.COLUMN_RZ, 1.0);
            rotationPointValues.put(RotationPointTable.COLUMN_TIME, 1L);
            rotationPointValues.put(RotationPointTable.COLUMN_IS_SYNCED, 0);
            rotationPointValues.put(RotationPointTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
            client.insert(MeasuringPointsContentProvider.ROTATION_POINTS_URI, rotationPointValues);
            client.insert(MeasuringPointsContentProvider.ROTATION_POINTS_URI, rotationPointValues);
            client.insert(MeasuringPointsContentProvider.ROTATION_POINTS_URI, rotationPointValues);

            ContentValues directionPointValues = new ContentValues();
            directionPointValues.put(MagneticValuePointTable.COLUMN_MX, 1.0);
            directionPointValues.put(MagneticValuePointTable.COLUMN_MY, 1.0);
            directionPointValues.put(MagneticValuePointTable.COLUMN_MZ, 1.0);
            directionPointValues.put(MagneticValuePointTable.COLUMN_TIME, 1L);
            directionPointValues.put(MagneticValuePointTable.COLUMN_IS_SYNCED, 0);
            directionPointValues.put(MagneticValuePointTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
            client.insert(MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI, directionPointValues);
            client.insert(MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI, directionPointValues);
            client.insert(MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI, directionPointValues);

            MeasurementContentProviderClient oocut = new MeasurementContentProviderClient(measurementIdentifier,
                    client);

            loadedAccelerations = oocut.load3DPoint(MeasurementSerializer.accelerationsSerializer);
            loadedRotations = oocut.load3DPoint(MeasurementSerializer.rotationsSerializer);
            loadedDirections = oocut.load3DPoint(MeasurementSerializer.directionsSerializer);

            assertThat(loadedAccelerations.getCount(), is(equalTo(3)));
            assertThat(loadedRotations.getCount(), is(equalTo(3)));
            assertThat(loadedDirections.getCount(), is(equalTo(3)));

            assertThat(oocut.cleanMeasurement(), is(equalTo(9)));

            loadedEmptyAccelerations = oocut.load3DPoint(MeasurementSerializer.accelerationsSerializer);
            loadedEmptyRotations = oocut.load3DPoint(MeasurementSerializer.rotationsSerializer);
            loadedEmptyDirections = oocut.load3DPoint(MeasurementSerializer.directionsSerializer);

            assertThat(loadedEmptyAccelerations.getCount(), is(equalTo(0)));
            assertThat(loadedEmptyRotations.getCount(), is(equalTo(0)));
            assertThat(loadedEmptyDirections.getCount(), is(equalTo(0)));
        } finally {
            if (client != null) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    client.release();
                } else {
                    client.close();
                }
            }
            if (loadedAccelerations != null) {
                loadedAccelerations.close();
            }
            if (loadedRotations != null) {
                loadedRotations.close();
            }
            if (loadedDirections != null) {
                loadedDirections.close();
            }
            if (loadedEmptyAccelerations != null) {
                loadedEmptyAccelerations.close();
            }
            if (loadedEmptyRotations != null) {
                loadedEmptyRotations.close();
            }
            if (loadedEmptyDirections != null) {
                loadedEmptyDirections.close();
            }
        }
    }
}
