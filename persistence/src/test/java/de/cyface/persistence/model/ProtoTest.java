/*
 * Copyright 2021 Cyface GmbH
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
package de.cyface.persistence.model;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import de.cyface.protos.model.Accelerations;
import de.cyface.protos.model.AccelerationsBinary;
import de.cyface.protos.model.LocationRecords;
import de.cyface.protos.model.Measurement;
import de.cyface.protos.model.MeasurementBytes;
import de.cyface.utils.Validate;

/**
 * Tests that captured data can be serialized to a transferable file and deserialized.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.0.0
 */
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
        final var aBatch1 = Accelerations.newBuilder()
                .addTimestamp(12345678901234L).addTimestamp(1000L)
                .addX(-9).addX(359)
                .addY(-5179).addY(-100)
                .addZ(0).addZ(10)
                .build();
        final var aBatch2 = Accelerations.newBuilder()
                .addTimestamp(12345678910234L).addTimestamp(1000L)
                .addX(0).addX(-244)
                .addY(+9810).addY(239)
                .addZ(-10).addZ(-310)
                .build();
        final var accelerationBytes = AccelerationsBinary.newBuilder()
                .addAccelerations(aBatch1).addAccelerations(aBatch2).build().toByteArray();
        final var locations = LocationRecords.newBuilder()
                .addTimestamp(1621582427000L)
                .addLatitude(51_064590)
                .addLongitude(13_699045)
                .addAccuracy(800)
                .addSpeed(1000);

        // Act
        // Using the modified `MeasurementBytes` class to inject the bytes without parsing
        final MeasurementBytes transmission = MeasurementBytes.newBuilder()
                .setFormatVersion(2)
                .setAccelerationsBinary(ByteString.copyFrom(accelerationBytes))
                .setLocationRecords(locations)
                .build();

        // Assert
        final Measurement transmitted = Measurement.parseFrom(transmission.toByteArray());
        assertThat(transmitted.getFormatVersion(), is(equalTo(2)));
        assertThat(transmitted.getLocationRecords().getTimestampCount(), is(equalTo(1)));
        assertThat(transmitted.getAccelerationsBinary().getAccelerations(0).getTimestampCount(), is(equalTo(2)));
        assertThat(transmitted.getAccelerationsBinary().getAccelerations(0).getTimestamp(0), is(equalTo(12345678901234L)));
        assertThat(transmitted.getLocationRecords().getTimestamp(0), is(equalTo(1621582427000L)));
    }
}