package de.cyface.persistence;

        import android.content.ContentValues;
        import android.net.Uri;
        import android.support.test.InstrumentationRegistry;
        import android.support.test.runner.AndroidJUnit4;
        import android.test.ProviderTestCase2;
        import android.test.mock.MockContentResolver;

        import org.junit.Before;
        import org.junit.Test;
        import org.junit.runner.RunWith;

        import static org.hamcrest.CoreMatchers.is;
        import static org.junit.Assert.assertThat;

/**
 * <p>
 * //TODO: missing documentation
 * </p>
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
@RunWith(AndroidJUnit4.class)
public class MeasurementTest extends ProviderTestCase2<MeasuringPointsContentProvider> {

    private ContentValues fixtureMeasurement;

    Uri contentUri = MeasuringPointsContentProvider.MEASUREMENT_URI;

    public MeasurementTest() {
        super(MeasuringPointsContentProvider.class, BuildConfig.provider);
    }

    @Before
    public void setUp() throws Exception {
        setContext(InstrumentationRegistry.getTargetContext());
        super.setUp();
        fixtureMeasurement = new ContentValues();
        fixtureMeasurement.put(MeasurementTable.COLUMN_WORKAROUND,0);
    }

    @Test
    public void testCascadingDeleteOneMeasurement() {
        long identifier = TestUtils.create(getMockContentResolver(),contentUri,fixtureMeasurement);

        ContentValues fixtureGpsPoint = new ContentValues();
        fixtureGpsPoint.put(GpsPointsTable.COLUMN_GPS_TIME,10000L);
        fixtureGpsPoint.put(GpsPointsTable.COLUMN_IS_SYNCED,false);
        fixtureGpsPoint.put(GpsPointsTable.COLUMN_LAT,13.0);
        fixtureGpsPoint.put(GpsPointsTable.COLUMN_LON,51.0);
        fixtureGpsPoint.put(GpsPointsTable.COLUMN_MEASUREMENT_FK,identifier);
        fixtureGpsPoint.put(GpsPointsTable.COLUMN_SPEED,1.0);
        fixtureGpsPoint.put(GpsPointsTable.COLUMN_ACCURACY,300);

        ContentValues fixtureAccelerationPoint = new ContentValues();
        fixtureAccelerationPoint.put(SamplePointTable.COLUMN_TIME,10000L);
        fixtureAccelerationPoint.put(SamplePointTable.COLUMN_IS_SYNCED,false);
        fixtureAccelerationPoint.put(SamplePointTable.COLUMN_AX,1.0);
        fixtureAccelerationPoint.put(SamplePointTable.COLUMN_AY,1.0);
        fixtureAccelerationPoint.put(SamplePointTable.COLUMN_AZ,1.0);
        fixtureAccelerationPoint.put(SamplePointTable.COLUMN_MEASUREMENT_FK,identifier);

        ContentValues fixtureRotationPoint = new ContentValues();
        fixtureRotationPoint.put(RotationPointTable.COLUMN_TIME,10000L);
        fixtureRotationPoint.put(RotationPointTable.COLUMN_IS_SYNCED,false);
        fixtureRotationPoint.put(RotationPointTable.COLUMN_RX,1.0);
        fixtureRotationPoint.put(RotationPointTable.COLUMN_RY,1.0);
        fixtureRotationPoint.put(RotationPointTable.COLUMN_RZ,1.0);
        fixtureRotationPoint.put(RotationPointTable.COLUMN_MEASUREMENT_FK,identifier);

        ContentValues fixtureMagneticValuePoint = new ContentValues();
        fixtureMagneticValuePoint.put(MagneticValuePointTable.COLUMN_TIME,10000L);
        fixtureMagneticValuePoint.put(MagneticValuePointTable.COLUMN_IS_SYNCED,false);
        fixtureMagneticValuePoint.put(MagneticValuePointTable.COLUMN_MX,1.0);
        fixtureMagneticValuePoint.put(MagneticValuePointTable.COLUMN_MY,1.0);
        fixtureMagneticValuePoint.put(MagneticValuePointTable.COLUMN_MZ,1.0);
        fixtureMagneticValuePoint.put(MagneticValuePointTable.COLUMN_MEASUREMENT_FK,identifier);

        TestUtils.create(getMockContentResolver(),MeasuringPointsContentProvider.GPS_POINTS_URI,fixtureGpsPoint);
        TestUtils.create(getMockContentResolver(),MeasuringPointsContentProvider.SAMPLE_POINTS_URI,fixtureAccelerationPoint);
        TestUtils.create(getMockContentResolver(),MeasuringPointsContentProvider.ROTATION_POINTS_URI,fixtureRotationPoint);
        TestUtils.create(getMockContentResolver(),MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI,fixtureMagneticValuePoint);

        TestUtils.delete(getMockContentResolver(),contentUri,identifier); // already tests if one row (measurement) was deleted
        assertThat(TestUtils.count(getMockContentResolver(),MeasuringPointsContentProvider.GPS_POINTS_URI),is(0));
        assertThat(TestUtils.count(getMockContentResolver(),MeasuringPointsContentProvider.SAMPLE_POINTS_URI),is(0));
        assertThat(TestUtils.count(getMockContentResolver(),MeasuringPointsContentProvider.ROTATION_POINTS_URI),is(0));
        assertThat(TestUtils.count(getMockContentResolver(),MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI),is(0));
    }

    @Test
    public void tearDown() throws Exception {
        getMockContentResolver().delete(MeasuringPointsContentProvider.GPS_POINTS_URI,null,null);
        getMockContentResolver().delete(MeasuringPointsContentProvider.SAMPLE_POINTS_URI,null,null);
        getMockContentResolver().delete(MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI,null,null);
        getMockContentResolver().delete(MeasuringPointsContentProvider.MEASUREMENT_URI,null,null);
        getMockContentResolver().delete(MeasuringPointsContentProvider.ROTATION_POINTS_URI,null,null);
        super.tearDown();
        getProvider().shutdown();
    }
}
