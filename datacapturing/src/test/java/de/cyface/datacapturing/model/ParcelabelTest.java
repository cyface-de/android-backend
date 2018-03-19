package de.cyface.datacapturing.model;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import android.os.Bundle;

/**
 * Tests whether parceling and unparceling of model objects is working as expected.
 *
 * @author Klemens Muthmann
 * @since 2.0.0
 * @version 1.0.0
 */
@RunWith(RobolectricTestRunner.class)
public class ParcelabelTest {
    /**
     * Tests parceling of <code>CapturedData</code> objects.
     */
    @Test
    public void parcelAndUnParcelCapturedData() {
        // Fixture test data.
        List<Point3D> accelerations = new ArrayList<>();
        accelerations.add(new Point3D(1L, 1.0f, 2.0f, 3.0f, 10L));
        accelerations.add(new Point3D(2L, 1.0f, 2.0f, 3.0f, 10L));
        List<Point3D> rotations = new ArrayList<>();
        accelerations.add(new Point3D(3L, 1.0f, 2.0f, 3.0f, 10L));
        accelerations.add(new Point3D(4L, 1.0f, 2.0f, 3.0f, 10L));
        List<Point3D> directions = new ArrayList<>();
        accelerations.add(new Point3D(5L, 1.0f, 2.0f, 3.0f, 10L));
        CapturedData oocut = new CapturedData(accelerations, rotations, directions);

        Bundle bundle = new Bundle();
        bundle.putParcelable("data", oocut);

        CapturedData data = bundle.getParcelable("data");

        assertThat(data, hasProperty("accelerations", equalTo(accelerations)));
        assertThat(data, hasProperty("rotations", equalTo(rotations)));
        assertThat(data, hasProperty("directions", equalTo(directions)));
    }

    @Test
    public void parcelAndUnparcelGeoLocation() {
        // Fixture test data.
        GeoLocation oocut = new GeoLocation(51.0, 13.1, 10L, 10.0, 2.0F);

        Bundle bundle = new Bundle();
        bundle.putParcelable("data", oocut);

        GeoLocation data = bundle.getParcelable("data");

        assertThat(data, hasProperty("lat", equalTo(51.0)));
        assertThat(data, hasProperty("lon", equalTo(13.1)));
        assertThat(data, hasProperty("timestamp", equalTo(10L)));
        assertThat(data, hasProperty("speed", equalTo(10.0)));
        assertThat(data, hasProperty("accuracy", equalTo(2.0F)));
    }
}
