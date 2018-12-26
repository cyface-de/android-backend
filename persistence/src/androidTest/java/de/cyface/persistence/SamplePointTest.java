package de.cyface.persistence;

import android.content.ContentResolver;
import android.content.ContentValues;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.provider.ProviderTestRule;

import static de.cyface.persistence.TestUtils.AUTHORITY;

/**
 * Tests correct behaviour for database operations on sample points.
 *
 * @author Klemens Muthmann
 * @version 1.0.3
 * @since 1.0.0
 */
@RunWith(AndroidJUnit4.class)
public class SamplePointTest {
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
        fixturePoint.put(AccelerationPointTable.COLUMN_AX, 1.0);
        fixturePoint.put(AccelerationPointTable.COLUMN_AY, 1.0);
        fixturePoint.put(AccelerationPointTable.COLUMN_AZ, 1.0);
        fixturePoint.put(AccelerationPointTable.COLUMN_TIME, 1L);
        fixturePoint.put(AccelerationPointTable.COLUMN_MEASUREMENT_FK, 1);
        fixturePoint.put(AccelerationPointTable.COLUMN_IS_SYNCED, 1);

        mockResolver = providerRule.getResolver();
    }

    @Test
    public void testCreateSuccessfully() {
        TestUtils.create(mockResolver, TestUtils.getAccelerationsUri(), fixturePoint);
    }

    @Test
    public void testReadSuccessfully() {
        final long identifier = TestUtils.create(mockResolver, TestUtils.getAccelerationsUri(), fixturePoint);
        TestUtils.read(mockResolver, TestUtils.getAccelerationsUri().buildUpon().appendPath(Long.toString(identifier)).build(), fixturePoint);
    }

    @Test
    public void testUpdateSuccessfully() {
        final long identifier = TestUtils.create(mockResolver, TestUtils.getAccelerationsUri(), fixturePoint);
        TestUtils.update(mockResolver, TestUtils.getAccelerationsUri(), identifier, AccelerationPointTable.COLUMN_AX, 1.0);
    }

    @Test
    public void testDeleteSuccessfully() {
        final long identifier = TestUtils.create(mockResolver, TestUtils.getAccelerationsUri(), fixturePoint);
        TestUtils.delete(mockResolver, TestUtils.getAccelerationsUri(), identifier);
    }

    @After
    public void tearDown() {
        mockResolver.delete(TestUtils.getAccelerationsUri(), null, null);
    }
}
