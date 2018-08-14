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
 * Tests correct behaviour for database operations on sample points.
 *
 * @author Klemens Muthmann
 * @version 1.0.1
 * @since 1.0.0
 */
@RunWith(AndroidJUnit4.class)
public class SamplePointTest extends ProviderTestCase2<MeasuringPointsContentProvider> {
    private ContentValues fixturePoint;

    public SamplePointTest() {
        super(MeasuringPointsContentProvider.class, TestUtils.AUTHORITY);
    }

    @Before
    public void setUp() throws Exception {
        setContext(InstrumentationRegistry.getTargetContext());
        super.setUp();

        Uri.Builder uriBuilder = new Uri.Builder();

        fixturePoint = new ContentValues();
        fixturePoint.put(SamplePointTable.COLUMN_AX, 1.0);
        fixturePoint.put(SamplePointTable.COLUMN_AY, 1.0);
        fixturePoint.put(SamplePointTable.COLUMN_AZ, 1.0);
        fixturePoint.put(SamplePointTable.COLUMN_TIME, 1L);
        fixturePoint.put(SamplePointTable.COLUMN_MEASUREMENT_FK, 1);
        fixturePoint.put(SamplePointTable.COLUMN_IS_SYNCED, 1);
    }

    @Test
    public void testCreateSuccessfully() {
        TestUtils.create(getMockContentResolver(), TestUtils.getAccelerationsUri(), fixturePoint);
    }

    @Test
    public void testReadSuccessfully() {
        final long identifier = TestUtils.create(getMockContentResolver(), TestUtils.getAccelerationsUri(), fixturePoint);
        TestUtils.read(getMockContentResolver(), TestUtils.getAccelerationsUri().buildUpon().appendPath(Long.toString(identifier)).build(), fixturePoint);
    }

    @Test
    public void testUpdateSuccessfully() {
        final long identifier = TestUtils.create(getMockContentResolver(), TestUtils.getAccelerationsUri(), fixturePoint);
        TestUtils.update(getMockContentResolver(), TestUtils.getAccelerationsUri(), identifier, SamplePointTable.COLUMN_AX, 1.0);
    }

    @Test
    public void testDeleteSuccessfully() {
        final long identifier = TestUtils.create(getMockContentResolver(), TestUtils.getAccelerationsUri(), fixturePoint);
        TestUtils.delete(getMockContentResolver(), TestUtils.getAccelerationsUri(), identifier);
    }

    @After
    public void tearDown() throws Exception {
        getMockContentResolver().delete(TestUtils.getAccelerationsUri(), null, null);
        super.tearDown();
        getProvider().shutdown();
    }
}
