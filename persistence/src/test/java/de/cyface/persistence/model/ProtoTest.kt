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
package de.cyface.persistence.model

import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import de.cyface.protos.model.Accelerations
import de.cyface.protos.model.AccelerationsBinary
import de.cyface.protos.model.File
import de.cyface.protos.model.LocationRecords
import de.cyface.protos.model.Measurement
import de.cyface.protos.model.MeasurementBytes
import de.cyface.utils.Validate
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.Test
import java.util.Random

/**
 * Tests that captured data can be serialized to a transferable file and deserialized.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.0.0
 */
class ProtoTest {
    @Test
    fun test_serializedSize_forEmptyMeasurement() {
        // Arrange
        val measurement = Measurement.newBuilder().setFormatVersion(2).build()
        Validate.isTrue(measurement.isInitialized)
        // Act
        val serializedSize = measurement.serializedSize

        // Assert
        MatcherAssert.assertThat(
            serializedSize, CoreMatchers.`is`(
                CoreMatchers.equalTo(
                    SERIALIZED_SIZE_FORMAT_VERSION_ONLY
                )
            )
        )
    }

    /**
     * Test the merging of sensor data (as collected by the data-capturing) into a measurement for synchronization.
     */
    @Test
    @Throws(InvalidProtocolBufferException::class)
    fun testMergeData() {
        // Arrange
        val aBatch1 = Accelerations.newBuilder()
            .addTimestamp(12345678901234L).addTimestamp(1000L)
            .addX(-9).addX(359)
            .addY(-5179).addY(-100)
            .addZ(0).addZ(10)
            .build()
        val aBatch2 = Accelerations.newBuilder()
            .addTimestamp(12345678910234L).addTimestamp(1000L)
            .addX(0).addX(-244)
            .addY(+9810).addY(239)
            .addZ(-10).addZ(-310)
            .build()
        val accelerationBytes = AccelerationsBinary.newBuilder()
            .addAccelerations(aBatch1).addAccelerations(aBatch2).build().toByteArray()
        val locations = LocationRecords.newBuilder()
            .addTimestamp(1621582427000L)
            .addLatitude(51064590)
            .addLongitude(13699045)
            .addAccuracy(800)
            .addSpeed(1000)

        // Act
        // Using the modified `MeasurementBytes` class to inject the bytes without parsing
        val transmission = MeasurementBytes.newBuilder()
            .setFormatVersion(2)
            .setAccelerationsBinary(ByteString.copyFrom(accelerationBytes))
            .setLocationRecords(locations)
            .build()

        // Assert
        val transmitted = Measurement.parseFrom(transmission.toByteArray())
        MatcherAssert.assertThat(
            transmitted.formatVersion,
            CoreMatchers.`is`(CoreMatchers.equalTo(2))
        )
        MatcherAssert.assertThat(
            transmitted.locationRecords.timestampCount, CoreMatchers.`is`(
                CoreMatchers.equalTo(1)
            )
        )
        MatcherAssert.assertThat(
            transmitted.accelerationsBinary.getAccelerations(0).timestampCount, CoreMatchers.`is`(
                CoreMatchers.equalTo(2)
            )
        )
        MatcherAssert.assertThat(
            transmitted.accelerationsBinary.getAccelerations(0).getTimestamp(0), CoreMatchers.`is`(
                CoreMatchers.equalTo(12345678901234L)
            )
        )
        MatcherAssert.assertThat(
            transmitted.locationRecords.getTimestamp(0), CoreMatchers.`is`(
                CoreMatchers.equalTo(1621582427000L)
            )
        )
    }

    /**
     * Test the serialized size with files attached.
     */
    @Test
    @Throws(InvalidProtocolBufferException::class)
    fun testWithFileAttachments() {
        // Arrange
        val sampleFileBytes = generateRandomBytes(10)
        val logFileBuilder = File.newBuilder()
            .setTimestamp(1000L)
            .setType(File.FileType.CSV)
            .setBytes(ByteString.copyFrom(sampleFileBytes))
        val jpgFileBuilder = File.newBuilder()
            .setTimestamp(1000L)
            .setType(File.FileType.JPG)
            .setBytes(ByteString.copyFrom(sampleFileBytes))

        // Act
        val transmission = MeasurementBytes.newBuilder()
            .setFormatVersion(3)
            .setCapturingLog(logFileBuilder)
            .addImages(jpgFileBuilder)
            .build()

        // Assert
        val transmitted = Measurement.parseFrom(transmission.toByteArray())
        MatcherAssert.assertThat(
            transmitted.formatVersion,
            CoreMatchers.`is`(CoreMatchers.equalTo(3))
        )
        MatcherAssert.assertThat(
            transmitted.capturingLog.timestamp, CoreMatchers.`is`(
                CoreMatchers.equalTo(1000L)
            )
        )
        MatcherAssert.assertThat(
            transmitted.capturingLog.type, CoreMatchers.`is`(
                CoreMatchers.equalTo(File.FileType.CSV)
            )
        )
        MatcherAssert.assertThat(
            transmitted.capturingLog.bytes.toByteArray(), CoreMatchers.`is`(
                CoreMatchers.equalTo(sampleFileBytes)
            )
        )
        MatcherAssert.assertThat(
            transmitted.imagesCount, CoreMatchers.`is`(
                CoreMatchers.equalTo(1)
            )
        )
        MatcherAssert.assertThat(
            transmitted.getImages(0).timestamp, CoreMatchers.`is`(
                CoreMatchers.equalTo(1000L)
            )
        )
        MatcherAssert.assertThat(
            transmitted.getImages(0).type, CoreMatchers.`is`(
                CoreMatchers.equalTo(File.FileType.JPG)
            )
        )
        MatcherAssert.assertThat(
            transmitted.getImages(0).bytes.toByteArray(), CoreMatchers.`is`(
                CoreMatchers.equalTo(sampleFileBytes)
            )
        )
    }

    private fun generateRandomBytes(size: Int): ByteArray {
        val byteArray = ByteArray(size)
        Random().nextBytes(byteArray)
        return byteArray
    }

    companion object {
        /**
         * Number of bytes of a serialized measurement (1 Byte) which contains:
         * - the format version (2 Bytes)
         */
        private const val SERIALIZED_SIZE_FORMAT_VERSION_ONLY = 3
    }
}