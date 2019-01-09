/*
 * Created on at 17:44.
 */
package de.cyface.persistence;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.ContentResolver;
import android.content.ContentValues;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import androidx.test.rule.provider.ProviderTestRule;

import static de.cyface.persistence.TestUtils.AUTHORITY;

/**
 * Tests whether creating, inserting, updating and deleting of rotation points is successful.
 *
 * @author Klemens Muthmann
 * @version 1.0.2
 * @since 1.0.0
 */
@RunWith(AndroidJUnit4.class)
public class RotationPointTest {

    /**
     * Test rule that provides a mock connection to a <code>ContentProvider</code> to test against.
     */
    @Rule
    public ProviderTestRule providerRule = new ProviderTestRule.Builder(MeasuringPointsContentProvider.class, AUTHORITY)
            .build();

    private ContentValues fixturePoint;
    /**
     * A <code>ContentResolver</code> to access the mock content provider.
     */
    private ContentResolver mockResolver;

    @Before
    public void setUp() {
        fixturePoint = new ContentValues();
        fixturePoint.put(RotationPointTable.COLUMN_MEASUREMENT_FK, 1);
        fixturePoint.put(RotationPointTable.COLUMN_RX, 1.1);
        fixturePoint.put(RotationPointTable.COLUMN_RY, 1.1);
        fixturePoint.put(RotationPointTable.COLUMN_RZ, 1.1);
        fixturePoint.put(RotationPointTable.COLUMN_TIME, 1L);

        mockResolver = providerRule.getResolver();
    }

    @Test
    public void testCreateSuccessfully() {
        TestUtils.create(mockResolver, TestUtils.getRotationsUri(), fixturePoint);
    }

    @Test
    public void testReadSuccessfully() {
        final long identifier = TestUtils.create(mockResolver, TestUtils.getRotationsUri(), fixturePoint);
        TestUtils.read(mockResolver, TestUtils.getRotationsUri().buildUpon().appendPath(Long.toString(identifier)).build(), fixturePoint);
    }

    @Test
    public void testUpdateSuccessfully() {
        long identifier = TestUtils.create(mockResolver, TestUtils.getRotationsUri(), fixturePoint);
        TestUtils.update(mockResolver, TestUtils.getRotationsUri(), identifier,
                RotationPointTable.COLUMN_RY, 2.0);
    }

    @Test
    public void testDeleteSuccessfully() {
        long identifier = TestUtils.create(mockResolver, TestUtils.getRotationsUri(), fixturePoint);
        TestUtils.delete(mockResolver, TestUtils.getRotationsUri(), identifier);
    }

    @After
    public void tearDown() {
        mockResolver.delete(TestUtils.getRotationsUri(), null, null);
    }
}
