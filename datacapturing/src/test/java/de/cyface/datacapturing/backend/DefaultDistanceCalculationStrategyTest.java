package de.cyface.datacapturing.backend;

import static de.cyface.datacapturing.backend.TestUtils.generateGeoLocation;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import android.location.Location;

import de.cyface.datacapturing.DefaultDistanceCalculationStrategy;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.utils.Validate;

/**
 * Tests that the {@link Measurement#distance} is calculated as expected.
 * <p>
 * This has to be an integration test as we use Android's {@link Location} class for distance calculation.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.2.0
 */
@RunWith(RobolectricTestRunner.class)
public class DefaultDistanceCalculationStrategyTest {

    /**
     * The object of the class under test
     */
    private DefaultDistanceCalculationStrategy distanceCalculationStrategy;

    @Before
    public void setUp() {
        distanceCalculationStrategy = new DefaultDistanceCalculationStrategy();
    }

    /**
     * Tests if the distance between two {@link GeoLocation}s is calculated as expected.
     */
    @Test
    public void testCalculateDistance() {
        // Arrange
        final int base = 0;
        final int expectedDistance = 2;
        final GeoLocation previousLocation = generateGeoLocation(base);
        final GeoLocation nextLocation = generateGeoLocation(base + expectedDistance);
        // Mock - nothing to do

        // Act
        final double distance = distanceCalculationStrategy.calculateDistance(previousLocation, nextLocation);

        // Assert
        // FIXME: stehe gerade auf dem Schlauch wie ich das is(closeTo()) hier mache ohne closeTo()
        Validate.isTrue(Math.abs(distance - expectedDistance) < 0.010);
    }
}
