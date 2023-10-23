/*
 * Copyright 2018-2023 Cyface GmbH
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
package de.cyface.synchronization

import android.content.Context
import android.os.RemoteException
import androidx.test.platform.app.InstrumentationRegistry
import com.google.protobuf.InvalidProtocolBufferException
import de.cyface.deserializer.LocationDeserializer
import de.cyface.deserializer.Point3DDeserializer
import de.cyface.model.Point3D
import de.cyface.model.Point3DImpl
import de.cyface.persistence.DefaultPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.PersistenceBehaviour
import de.cyface.persistence.PersistenceLayer
import de.cyface.persistence.io.DefaultFileIOHandler
import de.cyface.persistence.io.FileIOHandler
import de.cyface.persistence.model.GeoLocation
import de.cyface.persistence.model.Modality
import de.cyface.persistence.serialization.MeasurementSerializer
import de.cyface.persistence.serialization.Point3DFile
import de.cyface.persistence.serialization.TransferFileSerializer.loadSerialized
import de.cyface.protos.model.Measurement
import de.cyface.serializer.Point3DSerializer
import de.cyface.serializer.model.Point3DType
import de.cyface.testutils.SharedTestUtils
import de.cyface.utils.Validate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * Tests whether serialization and deserialization of the Cyface binary format is successful.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 4.0.0
 * @since 2.0.0
 */
class MeasurementSerializerTest {

    private lateinit var persistence: PersistenceLayer<PersistenceBehaviour>

    /**
     * The [Context] required to access the persistence layer.
     */
    private var context: Context? = null

    private var oocut: MeasurementSerializer? = null

    private var measurementId: Long? = null

    @Before
    @Throws(RemoteException::class)
    fun setUp() = runBlocking {

        // Mock file access layer
        val accelerations = serializeSamplePoints(SAMPLE_ACCELERATIONS, Point3DType.ACCELERATION)
        val serializedRotations = serializeSamplePoints(SAMPLE_ROTATIONS, Point3DType.ROTATION)
        val serializedDirections = serializeSamplePoints(SAMPLE_DIRECTIONS, Point3DType.DIRECTION)
        val mockFolder = mock<File> {
            on { exists() } doReturn true
        }
        val mockAccelerationFile = mock<File> {
            on { exists() } doReturn true
            on { length() } doReturn accelerations.size.toLong()
        }
        val mockRotationFile = mock<File> {
            on { exists() } doReturn true
            on { length() } doReturn serializedRotations.size.toLong()
        }
        val mockDirectionFile = mock<File> {
            on { exists() } doReturn true
            on { length() } doReturn serializedDirections.size.toLong()
        }
        val mockFileIOHandler = mock<FileIOHandler> {
            on { getFolderPath(any(), any()) } doReturn mockFolder
            on {
                getFilePath(
                    any(),
                    any(),
                    eq(Point3DFile.ACCELERATIONS_FOLDER_NAME),
                    eq(Point3DFile.ACCELERATIONS_FILE_EXTENSION)
                )
            } doReturn mockAccelerationFile
            on {
                getFilePath(
                    any(),
                    any(),
                    eq(Point3DFile.ROTATIONS_FOLDER_NAME),
                    eq(Point3DFile.ROTATION_FILE_EXTENSION)
                )
            } doReturn mockRotationFile
            on {
                getFilePath(
                    any(),
                    any(),
                    eq(Point3DFile.DIRECTIONS_FOLDER_NAME),
                    eq(Point3DFile.DIRECTION_FILE_EXTENSION)
                )
            } doReturn mockDirectionFile
            on { loadBytes(eq(mockAccelerationFile)) } doReturn accelerations
            on { loadBytes(eq(mockRotationFile)) } doReturn serializedRotations
            on { loadBytes(eq(mockDirectionFile)) } doReturn serializedDirections
        }

        // Initialization
        oocut = MeasurementSerializer()
        context = InstrumentationRegistry.getInstrumentation().targetContext
        persistence = DefaultPersistenceLayer(context!!, DefaultPersistenceBehaviour(), mockFileIOHandler)
        SharedTestUtils.clearPersistenceLayer(context!!, persistence)

        // Insert sample data into database
        measurementId = persistence.newMeasurement(Modality.UNKNOWN).id
        persistence.locationDao!!.insertAll(
            sampleLocation(measurementId!!),
            sampleLocation(measurementId!!),
            sampleLocation(measurementId!!)
        )
    }

    /**
     * Deletes all content from the database to make sure it is empty for the next test.
     */
    @After
    fun tearDown() {
        runBlocking { SharedTestUtils.clearPersistenceLayer(context!!, persistence) }
    }

    /**
     * Tests if serialization of a measurement is successful.
     *
     * This test checks that the binary header contains the correct `ParcelablePoint3D` counters.
     */
    @Test
    @Throws(IOException::class)
    fun testSerializeMeasurement() = runBlocking {

        // Arrange
        val serializedFile = withContext(Dispatchers.IO) {
            File.createTempFile("serializedTestFile", ".tmp")
        }
        try {
            val fileOutputStream =
                withContext(Dispatchers.IO) {
                    FileOutputStream(serializedFile)
                }
            val bufferedFileOutputStream = BufferedOutputStream(fileOutputStream)

            // Act
            loadSerialized(bufferedFileOutputStream, measurementId!!, persistence)

            // Assert
            MatcherAssert.assertThat(serializedFile.exists(), CoreMatchers.`is`(true))
            MatcherAssert.assertThat(
                serializedFile.length(), CoreMatchers.`is`(
                    CoreMatchers.equalTo(
                        SERIALIZED_MEASUREMENT_FILE_SIZE
                    )
                )
            )
        } finally {
            if (serializedFile.exists()) {
                Validate.isTrue(serializedFile.delete())
            }
        }
    }

    /**
     * Tests whether deserialization of measurements from a serialized measurement is successful and provides the
     * expected result.
     */
    @Test
    @Throws(IOException::class)
    fun testDeserializeMeasurement() = runBlocking {

        // Arrange
        val serializedFile = withContext(Dispatchers.IO) {
            File.createTempFile("serializedTestFile", ".tmp")
        }
        try {
            val fileOutputStream =
                withContext(Dispatchers.IO) {
                    FileOutputStream(serializedFile)
                }
            val bufferedFileOutputStream = BufferedOutputStream(fileOutputStream)

            // Already tested by `testSerializeMeasurement` - but not the deserialized bytes
            loadSerialized(bufferedFileOutputStream, measurementId!!, persistence)
            MatcherAssert.assertThat(
                serializedFile.length(), CoreMatchers.`is`(
                    CoreMatchers.equalTo(
                        SERIALIZED_MEASUREMENT_FILE_SIZE
                    )
                )
            )

            // Act & Assert - check the deserialized bytes
            deserializeAndCheck(DefaultFileIOHandler().loadBytes(serializedFile))
        } finally {
            if (serializedFile.exists()) {
                Validate.isTrue(serializedFile.delete())
            }
        }
    }

    /**
     * Tests successful serialization of a measurement to a compressed state.
     */
    @Test
    fun testSerializeCompressedMeasurement() = runBlocking {

        // If you need to change the sample point counts (and this) make sure the test work with the previous counts
        val serializedCompressedSize = 97L // When compression Deflater(level 9, true)
        val compressedTransferBytes =
            oocut!!.writeSerializedCompressed(measurementId!!, persistence)
        MatcherAssert.assertThat(
            compressedTransferBytes!!.length(),
            CoreMatchers.`is`(CoreMatchers.equalTo(serializedCompressedSize))
        )
    }

    /**
     * Tests that the serialized and compressed data can be decompressed and deserialized again.
     */
    @Test
    @Throws(IOException::class, DataFormatException::class)
    fun testDecompressDeserialize() = runBlocking {

        // Assemble serialized compressed bytes
        val compressedTransferTempFile =
            oocut!!.writeSerializedCompressed(measurementId!!, persistence)
        // Load bytes from compressedTransferFile
        val compressedBytes = ByteArray(compressedTransferTempFile!!.length().toInt())
        val dis = DataInputStream(withContext(Dispatchers.IO) {
            FileInputStream(compressedTransferTempFile)
        })
        withContext(Dispatchers.IO) {
            dis.readFully(compressedBytes)
        }

        // Decompress the compressed bytes and check length and bytes
        val inflater = Inflater(MeasurementSerializer.COMPRESSION_NOWRAP)
        inflater.setInput(compressedBytes, 0, compressedBytes.size)
        val decompressedBytes = ByteArray(2000)
        val decompressedLength = inflater.inflate(decompressedBytes).toLong()
        inflater.end()
        MatcherAssert.assertThat(
            decompressedLength, CoreMatchers.`is`(
                CoreMatchers.equalTo(
                    SERIALIZED_MEASUREMENT_FILE_SIZE
                )
            )
        )

        // Deserialize
        val decompressedTransferFileBytes =
            decompressedBytes.copyOfRange(0, SERIALIZED_MEASUREMENT_FILE_SIZE.toInt())

        // Deserialize bytes back to Objects and check their values
        deserializeAndCheck(decompressedTransferFileBytes)

        // Prepare comparison bytes which were not compressed at all
        val serializedFile = withContext(Dispatchers.IO) {
            File.createTempFile("serializedTestFile", ".tmp")
        }
        val uncompressedTransferFileBytes: ByteArray
        try {
            val fileOutputStream =
                withContext(Dispatchers.IO) {
                    FileOutputStream(serializedFile)
                }
            val bufferedFileOutputStream = BufferedOutputStream(fileOutputStream)
            runBlocking {
                loadSerialized(bufferedFileOutputStream, measurementId!!, persistence)
            }
            MatcherAssert.assertThat(
                serializedFile.length(), CoreMatchers.`is`(
                    CoreMatchers.equalTo(
                        SERIALIZED_MEASUREMENT_FILE_SIZE
                    )
                )
            )
            uncompressedTransferFileBytes = DefaultFileIOHandler().loadBytes(serializedFile)
            deserializeAndCheck(uncompressedTransferFileBytes) // just to be sure
        } finally {
            if (serializedFile.exists()) {
                Validate.isTrue(serializedFile.delete())
            }
        }

        // We check the bytes after we checked the object values because in the bytes it's hard to find the error
        MatcherAssert.assertThat(
            decompressedTransferFileBytes,
            CoreMatchers.`is`(CoreMatchers.equalTo(uncompressedTransferFileBytes))
        )
    }

    /**
     * @param bytes The `byte`s containing as stored in the uncompressed transfer file
     */
    @Throws(InvalidProtocolBufferException::class)
    private fun deserializeAndCheck(bytes: ByteArray) {
        Validate.isTrue(bytes.isNotEmpty())
        val measurement = deserializeTransferFile(bytes)
        MatcherAssert.assertThat(
            measurement.formatVersion,
            CoreMatchers.`is`(MeasurementSerializer.TRANSFER_FILE_FORMAT_VERSION.toInt())
        )
        MatcherAssert.assertThat(
            measurement.locationRecords.speedCount, CoreMatchers.`is`(
                SAMPLE_GEO_LOCATIONS
            )
        )
        MatcherAssert.assertThat(
            measurement.accelerationsBinary.getAccelerations(0).xCount, CoreMatchers.`is`(
                SAMPLE_ACCELERATIONS
            )
        )
        MatcherAssert.assertThat(
            measurement.rotationsBinary.getRotations(0).yCount, CoreMatchers.`is`(
                SAMPLE_ROTATIONS
            )
        )
        MatcherAssert.assertThat(
            measurement.directionsBinary.getDirections(0).zCount, CoreMatchers.`is`(
                SAMPLE_DIRECTIONS
            )
        )

        // Convert back to the model format (from the offset/diff proto format)
        val locations = LocationDeserializer.deserialize(measurement.locationRecords)
        val accelerations =
            Point3DDeserializer.accelerations(measurement.accelerationsBinary.accelerationsList)
        val rotations = Point3DDeserializer.rotations(measurement.rotationsBinary.rotationsList)
        val directions = Point3DDeserializer.directions(measurement.directionsBinary.directionsList)
        for (i in 0 until SAMPLE_GEO_LOCATIONS) {
            val entry = locations[i]
            MatcherAssert.assertThat(entry.accuracy, CoreMatchers.`is`(SAMPLE_DOUBLE_VALUE))
            MatcherAssert.assertThat(entry.lat, CoreMatchers.`is`(SAMPLE_DOUBLE_VALUE))
            MatcherAssert.assertThat(entry.lon, CoreMatchers.`is`(SAMPLE_DOUBLE_VALUE))
            MatcherAssert.assertThat(entry.speed, CoreMatchers.`is`(SAMPLE_DOUBLE_VALUE))
            MatcherAssert.assertThat(entry.timestamp, CoreMatchers.`is`(SAMPLE_LONG_VALUE))
        }
        for (i in 0 until SAMPLE_ACCELERATIONS) {
            val entry: Point3D = accelerations[i]
            MatcherAssert.assertThat(entry.x, CoreMatchers.`is`(SAMPLE_DOUBLE_VALUE.toFloat()))
            MatcherAssert.assertThat(entry.y, CoreMatchers.`is`(SAMPLE_DOUBLE_VALUE.toFloat()))
            MatcherAssert.assertThat(entry.z, CoreMatchers.`is`(SAMPLE_DOUBLE_VALUE.toFloat()))
            MatcherAssert.assertThat(entry.timestamp, CoreMatchers.`is`(SAMPLE_LONG_VALUE))
        }
        for (i in 0 until SAMPLE_ROTATIONS) {
            val entry: Point3D = rotations[i]
            MatcherAssert.assertThat(entry.x, CoreMatchers.`is`(SAMPLE_DOUBLE_VALUE.toFloat()))
            MatcherAssert.assertThat(entry.y, CoreMatchers.`is`(SAMPLE_DOUBLE_VALUE.toFloat()))
            MatcherAssert.assertThat(entry.z, CoreMatchers.`is`(SAMPLE_DOUBLE_VALUE.toFloat()))
            MatcherAssert.assertThat(entry.timestamp, CoreMatchers.`is`(SAMPLE_LONG_VALUE))
        }
        for (i in 0 until SAMPLE_DIRECTIONS) {
            val entry: Point3D = directions[i]
            MatcherAssert.assertThat(entry.x, CoreMatchers.`is`(SAMPLE_DOUBLE_VALUE.toFloat()))
            MatcherAssert.assertThat(entry.y, CoreMatchers.`is`(SAMPLE_DOUBLE_VALUE.toFloat()))
            MatcherAssert.assertThat(entry.z, CoreMatchers.`is`(SAMPLE_DOUBLE_VALUE.toFloat()))
            MatcherAssert.assertThat(entry.timestamp, CoreMatchers.`is`(SAMPLE_LONG_VALUE))
        }
    }

    /**
     * @param bytes The `byte`s containing as stored in the uncompressed transfer file
     */
    @Throws(InvalidProtocolBufferException::class)
    private fun deserializeTransferFile(bytes: ByteArray): Measurement {
        val buffer = ByteBuffer.wrap(bytes)
        val formatVersion = buffer.order(ByteOrder.BIG_ENDIAN).getShort(0)
        MatcherAssert.assertThat(
            formatVersion,
            CoreMatchers.`is`(CoreMatchers.equalTo(3.toShort()))
        )

        // slice from index 5 to index 9
        val protoBytes =
            bytes.copyOfRange(MeasurementSerializer.BYTES_IN_HEADER, bytes.size)
        val deserialized = Measurement.parseFrom(protoBytes)
        MatcherAssert.assertThat(
            deserialized.formatVersion,
            CoreMatchers.`is`(CoreMatchers.equalTo(3))
        )
        MatcherAssert.assertThat(
            deserialized.locationRecords.timestampCount, CoreMatchers.`is`(
                CoreMatchers.equalTo(SAMPLE_GEO_LOCATIONS)
            )
        )
        MatcherAssert.assertThat(
            deserialized.accelerationsBinary.getAccelerations(0).timestampCount, CoreMatchers.`is`(
                CoreMatchers.equalTo(SAMPLE_ACCELERATIONS)
            )
        )
        MatcherAssert.assertThat(
            deserialized.rotationsBinary.getRotations(0).timestampCount, CoreMatchers.`is`(
                CoreMatchers.equalTo(SAMPLE_ROTATIONS)
            )
        )
        MatcherAssert.assertThat(
            deserialized.directionsBinary.getDirections(0).timestampCount, CoreMatchers.`is`(
                CoreMatchers.equalTo(SAMPLE_DIRECTIONS)
            )
        )
        return deserialized
    }

    private fun serializeSamplePoints(numberOfPoints: Int, type: Point3DType): ByteArray {
        val points: MutableList<Point3D> = ArrayList()
        for (i in 0 until numberOfPoints) {
            points.add(
                Point3DImpl(
                    SAMPLE_DOUBLE_VALUE.toFloat(),
                    SAMPLE_DOUBLE_VALUE.toFloat(),
                    SAMPLE_DOUBLE_VALUE.toFloat(),
                    SAMPLE_LONG_VALUE
                )
            )
        }
        return Point3DSerializer.serialize(points, type)!!
    }

    private fun sampleLocation(measurementId: Long): GeoLocation {
        return GeoLocation(
            SAMPLE_LONG_VALUE,
            SAMPLE_DOUBLE_VALUE,
            SAMPLE_DOUBLE_VALUE,
            SAMPLE_DOUBLE_VALUE,
            SAMPLE_DOUBLE_VALUE,
            SAMPLE_DOUBLE_VALUE,
            null,
            measurementId
        )
    }

    companion object {
        private const val SAMPLE_ACCELERATIONS = 21
        private const val SAMPLE_ROTATIONS = 20
        private const val SAMPLE_DIRECTIONS = 10
        private const val SAMPLE_GEO_LOCATIONS = 3
        private const val SAMPLE_DOUBLE_VALUE = 1.0
        private const val SAMPLE_LONG_VALUE = 1L
        private const val SERIALIZED_MEASUREMENT_FILE_SIZE = 291L
    }
}