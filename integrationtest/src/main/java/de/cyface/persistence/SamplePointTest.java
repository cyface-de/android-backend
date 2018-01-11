/*
 * Created on 03.12.15 at 20:58
 */
package de.cyface.persistence;

import android.content.ContentValues;
import android.net.Uri;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

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
public class SamplePointTest extends CyfaceDatabaseTest {

    private ContentValues fixturePoint;

    @Override
    protected Uri getTableUri() {
        return MeasuringPointsContentProvider.SAMPLE_POINTS_URI;
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
        create(fixturePoint,"1");
    }

    @Test
    public void testReadSuccessfully() {
        create(fixturePoint,"1");
        read(fixturePoint);
    }

    @Test
    public void testUpdateSuccessfully() {
        create(fixturePoint,"1");
        update("1",SamplePointTable.COLUMN_AX,1.0);
    }

    @Test
    public void testDeleteSuccessfully() {
        create(fixturePoint,"1");
        delete(1);
    }
}
