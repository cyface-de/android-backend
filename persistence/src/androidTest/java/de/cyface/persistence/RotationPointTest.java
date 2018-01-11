/*
 * Created on at 17:44.
 */
package de.cyface.persistence;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.ContentValues;
import android.net.Uri;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

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
public class RotationPointTest extends CyfaceDatabaseTest {

    private ContentValues fixturePoint;


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

    @Override
    protected Uri getTableUri() {
        return MeasuringPointsContentProvider.ROTATION_POINTS_URI;
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
        update("1",RotationPointTable.COLUMN_RY,2.0);
    }

    @Test
    public void testDeleteSuccessfully() {
        create(fixturePoint,"1");
        delete(1);
    }
}
