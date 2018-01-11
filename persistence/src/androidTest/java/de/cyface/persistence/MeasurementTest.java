package de.cyface.persistence;

        import android.content.ContentValues;
        import android.net.Uri;
        import android.support.test.InstrumentationRegistry;

        import org.junit.Before;
        import org.junit.Test;

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
public class MeasurementTest extends CyfaceDatabaseTest {

    private ContentValues fixtureMeasurement;
    private ContentValues fixtureGpsPoint;
    private ContentValues fixtureAccelerationPoint;
    private ContentValues fixtureRotationPoint;
    private ContentValues fixtureMagneticValuePoint;

    @Override protected Uri getTableUri() {
        return MeasuringPointsContentProvider.MEASUREMENT_URI;
    }

    @Before
    public void setUp() throws Exception {
        setContext(InstrumentationRegistry.getTargetContext());
        super.setUp();
        fixtureMeasurement = new ContentValues();
        fixtureMeasurement.put(MeasurementTable.COLUMN_WORKAROUND,0);

        fixtureGpsPoint = new ContentValues();
        fixtureGpsPoint.put(GpsPointsTable.COLUMN_GPS_TIME,10000L);
        fixtureGpsPoint.put(GpsPointsTable.COLUMN_IS_SYNCED,false);
        fixtureGpsPoint.put(GpsPointsTable.COLUMN_LAT,13.0);
        fixtureGpsPoint.put(GpsPointsTable.COLUMN_LON,51.0);
        fixtureGpsPoint.put(GpsPointsTable.COLUMN_MEASUREMENT_FK,1);
        fixtureGpsPoint.put(GpsPointsTable.COLUMN_SPEED,1.0);
        fixtureGpsPoint.put(GpsPointsTable.COLUMN_ACCURACY,300);

        fixtureAccelerationPoint = new ContentValues();
        fixtureAccelerationPoint.put(SamplePointTable.COLUMN_TIME,10000L);
        fixtureAccelerationPoint.put(SamplePointTable.COLUMN_IS_SYNCED,false);
        fixtureAccelerationPoint.put(SamplePointTable.COLUMN_AX,1.0);
        fixtureAccelerationPoint.put(SamplePointTable.COLUMN_AY,1.0);
        fixtureAccelerationPoint.put(SamplePointTable.COLUMN_AZ,1.0);
        fixtureAccelerationPoint.put(SamplePointTable.COLUMN_MEASUREMENT_FK,1);

        fixtureRotationPoint = new ContentValues();
        fixtureRotationPoint.put(RotationPointTable.COLUMN_TIME,10000L);
        fixtureRotationPoint.put(RotationPointTable.COLUMN_IS_SYNCED,false);
        fixtureRotationPoint.put(RotationPointTable.COLUMN_RX,1.0);
        fixtureRotationPoint.put(RotationPointTable.COLUMN_RY,1.0);
        fixtureRotationPoint.put(RotationPointTable.COLUMN_RZ,1.0);
        fixtureRotationPoint.put(RotationPointTable.COLUMN_MEASUREMENT_FK,1);

        fixtureMagneticValuePoint = new ContentValues();
        fixtureMagneticValuePoint.put(MagneticValuePointTable.COLUMN_TIME,10000L);
        fixtureMagneticValuePoint.put(MagneticValuePointTable.COLUMN_IS_SYNCED,false);
        fixtureMagneticValuePoint.put(MagneticValuePointTable.COLUMN_MX,1.0);
        fixtureMagneticValuePoint.put(MagneticValuePointTable.COLUMN_MY,1.0);
        fixtureMagneticValuePoint.put(MagneticValuePointTable.COLUMN_MZ,1.0);
        fixtureMagneticValuePoint.put(MagneticValuePointTable.COLUMN_MEASUREMENT_FK,1);
    }

    @Test
    public void testCascadingDeleteOneMeasurement() {
        create(fixtureMeasurement,"1");
        create(fixtureGpsPoint,"1",MeasuringPointsContentProvider.GPS_POINTS_URI);
        create(fixtureAccelerationPoint,"1",MeasuringPointsContentProvider.SAMPLE_POINTS_URI);
        create(fixtureRotationPoint,"1",MeasuringPointsContentProvider.ROTATION_POINTS_URI);
        create(fixtureMagneticValuePoint,"1",MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI);

        delete(1); // already tests if one row (measurement) was deleted
        assertThat(getMockContentResolver().query(MeasuringPointsContentProvider.GPS_POINTS_URI,null,null,null,null).getCount(),is(0));
        assertThat(getMockContentResolver().query(MeasuringPointsContentProvider.SAMPLE_POINTS_URI,null,null,null,null).getCount(),is(0));
        assertThat(getMockContentResolver().query(MeasuringPointsContentProvider.ROTATION_POINTS_URI,null,null,null,null).getCount(),is(0));
        assertThat(getMockContentResolver().query(MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI,null,null,null,null).getCount(),is(0));
    }
}
