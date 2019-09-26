/*
 * Copyright 2018 Cyface GmbH
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

import static de.cyface.synchronization.HttpConnection.BOUNDARY;
import static de.cyface.synchronization.HttpConnection.LINE_FEED;
import static de.cyface.synchronization.HttpConnection.TAIL;
import static de.cyface.synchronization.HttpConnection.generateFileHeaderPart;
import static de.cyface.testutils.SharedTestUtils.generateGeoLocation;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import org.junit.Before;
import org.junit.Test;

import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Modality;

/**
 * Tests whether our default implementation of the {@link Http} protocol works as expected.
 *
 * @author Armin Schnabel
 * @version 1.1.9
 * @since 4.0.0
 */
public class HttpConnectionTest {

    private HttpConnection oocut;

    /**
     * Initializes all required properties and adds the <code>testListener</code> to the <code>CapturingProcess</code>.
     */
    @Before
    public void setUp() {
        oocut = new HttpConnection();
    }

    /**
     * Tests that the MultiPart header is generated correctly.
     * <p>
     * This test reproduced MOV-655 where the header parts were assembled in the wrong order. As a consequence
     * the file binary which is added directly at the end of the header was added to the locationCount header part
     * instead of the file header part which was the last part of the header before.
     */
    @Test
    public void testGenerateHeader() {

        final GeoLocation startLocation = generateGeoLocation(0);
        final GeoLocation endLocation = generateGeoLocation(10);
        final SyncAdapter.MetaData metaData = new SyncAdapter.MetaData(startLocation, endLocation, "test-did", 78,
                "test_deviceType", "test_osVersion", "test_appVersion", 10.0, 5, Modality.BICYCLE);

        // Act
        final String header = oocut.generateHeader(metaData);

        // Assert
        final String expectedHeader = "--" + BOUNDARY + LINE_FEED

                + "Content-Disposition: form-data; name=\"startLocLat\"" + LINE_FEED + LINE_FEED + "51.1" + LINE_FEED
                + "--" + BOUNDARY + LINE_FEED
                + "Content-Disposition: form-data; name=\"startLocLon\"" + LINE_FEED + LINE_FEED + "13.1" + LINE_FEED
                + "--" + BOUNDARY + LINE_FEED
                + "Content-Disposition: form-data; name=\"startLocTS\"" + LINE_FEED + LINE_FEED + "1000000000"
                + LINE_FEED
                + "--" + BOUNDARY + LINE_FEED

                + "Content-Disposition: form-data; name=\"endLocLat\"" + LINE_FEED + LINE_FEED + "51.10008993199995"
                + LINE_FEED
                + "--" + BOUNDARY + LINE_FEED
                + "Content-Disposition: form-data; name=\"endLocLon\"" + LINE_FEED
                + LINE_FEED
                + "13.100000270697" + LINE_FEED
                + "--" + BOUNDARY + LINE_FEED
                + "Content-Disposition: form-data; name=\"endLocTS\"" + LINE_FEED + LINE_FEED + "1000010000" + LINE_FEED
                + "--" + BOUNDARY + LINE_FEED

                + "Content-Disposition: form-data; name=\"deviceId\"" + LINE_FEED + LINE_FEED + "test-did" + LINE_FEED
                + "--" + BOUNDARY + LINE_FEED
                + "Content-Disposition: form-data; name=\"measurementId\"" + LINE_FEED + LINE_FEED + "78" + LINE_FEED
                + "--" + BOUNDARY + LINE_FEED
                + "Content-Disposition: form-data; name=\"deviceType\"" + LINE_FEED + LINE_FEED + "test_deviceType"
                + LINE_FEED
                + "--" + BOUNDARY + LINE_FEED
                + "Content-Disposition: form-data; name=\"osVersion\"" + LINE_FEED + LINE_FEED + "test_osVersion"
                + LINE_FEED
                + "--" + BOUNDARY + LINE_FEED
                + "Content-Disposition: form-data; name=\"appVersion\"" + LINE_FEED + LINE_FEED + "test_appVersion"
                + LINE_FEED
                + "--" + BOUNDARY + LINE_FEED
                + "Content-Disposition: form-data; name=\"length\"" + LINE_FEED + LINE_FEED + "10.0" + LINE_FEED
                + "--" + BOUNDARY + LINE_FEED
                + "Content-Disposition: form-data; name=\"locationCount\"" + LINE_FEED + LINE_FEED + "5" + LINE_FEED
                + "--" + BOUNDARY + LINE_FEED
                + "Content-Disposition: form-data; name=\"vehicle\"" + LINE_FEED + LINE_FEED + "BICYCLE" + LINE_FEED;
        assertThat(header, is(equalTo(expectedHeader)));
    }

    /**
     * Tests that the number of bytes written to {@code OutputStream} is calculated correctly.
     * <p>
     * This test is indented to avoid MOV-669 where we accidentally used the number of characters in the header
     * instead of the number of bytes to set the fixed content length for steaming mode.
     */
    @Test
    public void testCalculateBytesWrittenToOutputStream() {

        // Arrange
        final SyncAdapter.MetaData metaData = new SyncAdapter.MetaData(generateGeoLocation(0), generateGeoLocation(10),
                "test-did", 78, "test_deviceType", "test_osVersion", "test_appVersion", 10.0, 5, Modality.BICYCLE);
        final String header = oocut.generateHeader(metaData);

        final String fileHeaderPart = generateFileHeaderPart("fileToUpload", "test-did_78.cyf");
        final String eventsFileHeaderPart = generateFileHeaderPart("eventsFile", "test-did_78.cyfe");

        final byte[] testFile = "TEST_FÄ`&ô»ω_CONTENT".getBytes(); // with chars which require > 1 Byte
        final long filePartSize = testFile.length;

        final byte[] eventsTestFile = "TEST_FÄ`&ô»ω_EVENTS".getBytes(); // with chars which require > 1 Byte
        final long eventsPartSize = eventsTestFile.length;

        // Act
        final long requestLength = oocut.calculateBytesWrittenToOutputStream(header.getBytes(),
                fileHeaderPart.getBytes().length, eventsFileHeaderPart.getBytes().length, filePartSize,
                eventsPartSize);

        // Assert
        assertThat((int)requestLength,
                is(equalTo(header.getBytes().length + fileHeaderPart.getBytes().length + testFile.length
                        + LINE_FEED.getBytes().length
                        + eventsFileHeaderPart.getBytes().length + eventsTestFile.length + LINE_FEED.getBytes().length
                        + TAIL.getBytes().length)));
    }
}
