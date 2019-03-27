package de.cyface.synchronization;

import static de.cyface.synchronization.HttpConnection.TAIL;
import static de.cyface.testutils.SharedTestUtils.generateGeoLocation;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import org.junit.Before;
import org.junit.Test;

import de.cyface.persistence.model.GeoLocation;
import de.cyface.utils.Validate;

/**
 * Tests whether our default implementation of the {@link Http} protocol works as expected.
 *
 * @author Armin Schnabel
 * @version 1.0.0
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

        // Arrange
        final byte[] testFile = "TEST_FILE_CONTENT".getBytes();
        final long filePartSize = testFile.length + TAIL.length();
        Validate.isTrue(filePartSize > TAIL.length());
        final GeoLocation startLocation = generateGeoLocation(0);
        final GeoLocation endLocation = generateGeoLocation(10);
        final SyncAdapter.MetaData metaData = new SyncAdapter.MetaData(startLocation, endLocation, "test-did", 78,
                "test_deviceType", "test_osVersion", "test_appVersion", 10.0, 5);

        // Act
        final String header = oocut.generateHeader(filePartSize, metaData, "test-did_78.cyf");

        // Assert
        final String expectedHeader = "-----------------------------boundary\r\n"
                + "Content-Disposition: form-data; name=\"startLocation\"\r\n" + "\r\n"
                + "51.1, 13.1, 1000000000\r\n" + "-----------------------------boundary\r\n"
                + "Content-Disposition: form-data; name=\"endLocation\"\r\n" + "\r\n"
                + "51.10008993199995, 13.100000270697, 1000010000\r\n" + "-----------------------------boundary\r\n"
                + "Content-Disposition: form-data; name=\"deviceId\"\r\n" + "\r\n" + "test-did\r\n"
                + "-----------------------------boundary\r\n" + "Content-Disposition: form-data; name=\"measurementId\"\r\n"
                + "\r\n" + "78\r\n" + "-----------------------------boundary\r\n"
                + "Content-Disposition: form-data; name=\"deviceType\"\r\n" + "\r\n" + "test_deviceType\r\n"
                + "-----------------------------boundary\r\n" + "Content-Disposition: form-data; name=\"osVersion\"\r\n"
                + "\r\n" + "test_osVersion\r\n" + "-----------------------------boundary\r\n"
                + "Content-Disposition: form-data; name=\"appVersion\"\r\n" + "\r\n" + "test_appVersion\r\n"
                + "-----------------------------boundary\r\n" + "Content-Disposition: form-data; name=\"length\"\r\n" + "\r\n"
                + "10.0\r\n" + "-----------------------------boundary\r\n"
                + "Content-Disposition: form-data; name=\"locationCount\"\r\n" + "\r\n" + "5\r\n"
                + "-----------------------------boundary\r\n"
                + "Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"test-did_78.cyf\"\r\n"
                + "Content-Type: application/octet-stream\r\n" + "Content-Transfer-Encoding: binary\r\n"
                + "Content-length: 60\r\n" + "\r\n";
        assertThat(header, is(equalTo(expectedHeader)));
    }
}
