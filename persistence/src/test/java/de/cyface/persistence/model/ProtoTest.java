/*
 * Copyright (C) 2021 Cyface GmbH - All Rights Reserved
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package de.cyface.persistence.model;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

import de.cyface.protos.model.Accelerations;
import de.cyface.protos.model.LocationRecords;
import de.cyface.protos.model.Measurement;
import de.cyface.protos.model.MeasurementBytes;
import de.cyface.utils.Validate;

public class ProtoTest {

    /**
     * Number of bytes of a serialized measurement (1 Byte) which contains:
     * - the format version (2 Bytes)
     */
    private static final Integer SERIALIZED_SIZE_FORMAT_VERSION_ONLY = 3;

    @Test
    public void test_serializedSize_forEmptyMeasurement() {
        // Arrange
        final Measurement measurement = Measurement.newBuilder().setFormatVersion(2).build();
        Validate.isTrue(measurement.isInitialized());
        // Act
        final int serializedSize = measurement.getSerializedSize();

        // Assert
        assertThat(serializedSize, is(equalTo(SERIALIZED_SIZE_FORMAT_VERSION_ONLY)));
    }

    /**
     * Test the merging of sensor data (as collected by the data-capturing) into a measurement for synchronization.
     */
    @Test
    public void testMergeData() throws InvalidProtocolBufferException {
        // Arrange
        final Accelerations accelerations = Accelerations.newBuilder()
                .addTimestamp(12345678901234L).addTimestamp(1000L)
                .addX(-9).addX(359)
                .addY(-5179).addY(-100)
                .addZ(0).addZ(10)
                .build();
        // FIXME: ADD A FORMAT VERSION TO THIS FILE TO MAKE SURE WE ONLY INJECT COMPATIBLE BYTES
        final byte[] accelerationBytes = accelerations.toByteArray();
        final LocationRecords.Builder locations = LocationRecords.newBuilder()
                .addTimestamp(1621582427000L)
                .addLatitude(51_064590)
                .addLongitude(13_699045)
                .addAccuracy(800)
                .addSpeed(1000);

        // Act
        // Using the modified `MeasurementBytes` class to inject the bytes without parsing
        final MeasurementBytes transmission = MeasurementBytes.newBuilder()
                .setFormatVersion(2)
                .setAccelerations(ByteString.copyFrom(accelerationBytes))
                .setLocationRecords(locations)
                .build();

        // Assert
        final Measurement transmitted = Measurement.parseFrom(transmission.toByteArray());
        assertThat(transmitted.getFormatVersion(), is(equalTo(2)));
        assertThat(transmitted.getLocationRecords().getTimestampCount(), is(equalTo(1)));
        assertThat(transmitted.getAccelerations().getTimestampCount(), is(equalTo(2)));
        assertThat(transmitted.getAccelerations().getTimestamp(0), is(equalTo(12345678901234L)));
        assertThat(transmitted.getLocationRecords().getTimestamp(0), is(equalTo(1621582427000L)));
    }
}
