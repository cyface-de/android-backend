/*
 * Created on at 17:44.
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
 * Tests whether creating, inserting, updating and deleting of rotation points is successful.
 * </p>
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
@RunWith(AndroidJUnit4.class)
public class RotationPointTest extends ProviderTestCase2<MeasuringPointsContentProvider> {
    private static final String PROVIDER = BuildConfig.testProvider;

    static {
        MeasuringPointsContentProvider.AUTHORITY = PROVIDER;
    }

    private ContentValues fixturePoint;

    public RotationPointTest() {
        super(MeasuringPointsContentProvider.class, PROVIDER);
    }


    @Override
    @Before
    public void setUp() throws Exception {
        setContext(InstrumentationRegistry.getTargetContext());
        super.setUp();
        fixturePoint = new ContentValues();
        fixturePoint.put(RotationPointTable.COLUMN_MEASUREMENT_FK, 1);
        fixturePoint.put(RotationPointTable.COLUMN_RX, 1.1);
        fixturePoint.put(RotationPointTable.COLUMN_RY, 1.1);
        fixturePoint.put(RotationPointTable.COLUMN_RZ, 1.1);
        fixturePoint.put(RotationPointTable.COLUMN_TIME, 1l);
    }

    @Test
    public void testCreateSuccessfully() {
        TestUtils.create(getMockContentResolver(),MeasuringPointsContentProvider.ROTATION_POINTS_URI,fixturePoint);
    }

    @Test
    public void testReadSuccessfully() {
        TestUtils.create(getMockContentResolver(),MeasuringPointsContentProvider.ROTATION_POINTS_URI,fixturePoint);
        TestUtils.read(getMockContentResolver(),MeasuringPointsContentProvider.ROTATION_POINTS_URI,fixturePoint);
    }

    @Test
    public void testUpdateSuccessfully() {
        long identifier = TestUtils.create(getMockContentResolver(),MeasuringPointsContentProvider.ROTATION_POINTS_URI,fixturePoint);
        TestUtils.update(getMockContentResolver(),MeasuringPointsContentProvider.ROTATION_POINTS_URI,identifier,RotationPointTable.COLUMN_RY,2.0);
    }

    @Test
    public void testDeleteSuccessfully() {
        long identifier = TestUtils.create(getMockContentResolver(),MeasuringPointsContentProvider.ROTATION_POINTS_URI,fixturePoint);
        TestUtils.delete(getMockContentResolver(),MeasuringPointsContentProvider.ROTATION_POINTS_URI,identifier);
    }

    @After
    public void tearDown() throws Exception {
        getMockContentResolver().delete(MeasuringPointsContentProvider.ROTATION_POINTS_URI,null,null);
        super.tearDown();
        getProvider().shutdown();
    }
}
