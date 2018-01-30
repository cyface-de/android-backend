/*
 * Created on 12.08.15 at 16:31
 */
package de.cyface.datacapturing;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.ContentResolver;
import android.database.Cursor;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.test.ProviderTestCase2;

import de.cynav.capturing.model.Point3D;
import de.cynav.persistence.*;
import de.cynav.persistence.BuildConfig;

/**
 * Tests whether captured data is correctly saved to the underlying content provider.
 * The Integration tests need an Activity to be executed, this they are in an own module.
 *
 * (!) Create a custom Instrumented Test config for integration-test module to run this in the IDE !
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
@RunWith(AndroidJUnit4.class)
public class CapturedDataWriterTest extends ProviderTestCase2<MeasuringPointsContentProvider> {
    /**
     * Constructor.
     */
    public CapturedDataWriterTest() {
        super(MeasuringPointsContentProvider.class, BuildConfig.provider);
    }

    @Before
    public void setUp() throws Exception {
        setContext(InstrumentationRegistry.getTargetContext());
        super.setUp();
    }

    @Test
    public void testWriteData() {

        ContentResolver resolver = getMockContentResolver();
        Point3D[] values = new Point3D[] {new Point3D(0.25f, 0.25f, 0.25f, 1000L)};
        CapturedDataWriter writer = new CapturedDataWriter(new CapturedData(51L, 13L, 1000L, 1.0, 300,
                Arrays.asList(values), Arrays.asList(values), Arrays.asList(values)), resolver, 1L);
        writer.writeCapturedData();

        Cursor result = resolver.query(MeasuringPointsContentProvider.SAMPLE_POINTS_URI, null, null, null, null);
        Assert.assertTrue(result.getCount() == 1);
    }
}