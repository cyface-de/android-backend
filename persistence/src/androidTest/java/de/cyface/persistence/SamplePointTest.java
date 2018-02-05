/*
 * Created on 03.12.15 at 20:58
 */
package de.cyface.persistence;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.ContentValues;
import android.net.Uri;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.test.ProviderTestCase2;

/**
 * <p>
 * Tests correct behaviour for database operations on sample points.
 * </p>
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
@RunWith(AndroidJUnit4.class)
public class SamplePointTest extends ProviderTestCase2<MeasuringPointsContentProvider> {

    private ContentValues fixturePoint;

    public SamplePointTest() {
        super(MeasuringPointsContentProvider.class, BuildConfig.provider);
    }

    @Before
    public void setUp() throws Exception {
        setContext(InstrumentationRegistry.getTargetContext());
        super.setUp();
        fixturePoint = new ContentValues();
        fixturePoint.put(SamplePointTable.COLUMN_AX,1.0);
        fixturePoint.put(SamplePointTable.COLUMN_AY,1.0);
        fixturePoint.put(SamplePointTable.COLUMN_AZ,1.0);
        fixturePoint.put(SamplePointTable.COLUMN_TIME,1L);
        fixturePoint.put(SamplePointTable.COLUMN_MEASUREMENT_FK,1);
        fixturePoint.put(SamplePointTable.COLUMN_IS_SYNCED,1);
    }

    @Test
    public void testCreateSuccessfully() {
        TestUtils.create(getMockContentResolver(),MeasuringPointsContentProvider.SAMPLE_POINTS_URI,fixturePoint);
    }

    @Test
    public void testReadSuccessfully() {
        TestUtils.create(getMockContentResolver(),MeasuringPointsContentProvider.SAMPLE_POINTS_URI,fixturePoint);
        TestUtils.read(getMockContentResolver(),MeasuringPointsContentProvider.SAMPLE_POINTS_URI,fixturePoint);
    }

    @Test
    public void testUpdateSuccessfully() {
        long identifier = TestUtils.create(getMockContentResolver(),MeasuringPointsContentProvider.SAMPLE_POINTS_URI,fixturePoint);
        TestUtils.update(getMockContentResolver(),MeasuringPointsContentProvider.SAMPLE_POINTS_URI,identifier,SamplePointTable.COLUMN_AX,1.0);
    }

    @Test
    public void testDeleteSuccessfully() {
        long identifier = TestUtils.create(getMockContentResolver(),MeasuringPointsContentProvider.SAMPLE_POINTS_URI,fixturePoint);
        TestUtils.delete(getMockContentResolver(),MeasuringPointsContentProvider.SAMPLE_POINTS_URI,identifier);
    }
    
    @After
    public void tearDown() throws Exception {
        getMockContentResolver().delete(MeasuringPointsContentProvider.SAMPLE_POINTS_URI,null,null);
        super.tearDown();
        getProvider().shutdown();
    }
}
