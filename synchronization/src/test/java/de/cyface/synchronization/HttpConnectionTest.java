/*
 * Copyright 2018-2021 Cyface GmbH
 *
 * This file is part of the Cyface SDK for Android.
 *
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.synchronization;

import static de.cyface.testutils.SharedTestUtils.generateGeoLocation;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Modality;

/**
 * Tests whether our default implementation of the {@link Http} protocol works as expected.
 *
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 4.0.0
 */
public class HttpConnectionTest {

    /**
     * Tests that the body of the pre-request stays consistent.
     */
    @Test
    public void testPreRequestBody() {

        final GeoLocation startLocation = generateGeoLocation(0);
        final GeoLocation endLocation = generateGeoLocation(10);
        final SyncAdapter.MetaData metaData = new SyncAdapter.MetaData(startLocation, endLocation, "test-did", 78,
                "test_deviceType", "test_osVersion", "test_appVersion", 10.0, 5, Modality.BICYCLE);

        // Act
        final Map<String, String> result = HttpConnection.preRequestBody(metaData);

        // Assert
        final Map<String, String> expected = new HashMap<>();
        expected.put("startLocLat", "51.1");
        expected.put("startLocLon", "13.1");
        expected.put("startLocTS", "1000000000");
        expected.put("endLocLat", "51.10008993199995");
        expected.put("endLocLon", "13.100000270697");
        expected.put("endLocTS", "1000010000");
        expected.put("deviceId", "test-did");
        expected.put("measurementId", "78");
        expected.put("deviceType", "test_deviceType");
        expected.put("osVersion", "test_osVersion");
        expected.put("appVersion", "test_appVersion");
        expected.put("length", "10.0");
        expected.put("locationCount", "5");
        expected.put("vehicle", "BICYCLE");
        expected.put("formatVersion", "2");
        assertThat(result, is(equalTo(expected)));
    }
}