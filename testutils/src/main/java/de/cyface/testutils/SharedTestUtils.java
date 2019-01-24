package de.cyface.testutils;

import static de.cyface.persistence.Constants.TAG;
import static de.cyface.persistence.Utils.getGeoLocationsUri;
import static de.cyface.persistence.Utils.getIdentifierUri;
import static de.cyface.persistence.Utils.getMeasurementUri;
import static de.cyface.persistence.serialization.MeasurementSerializer.BYTES_IN_ONE_POINT_3D_ENTRY;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import android.content.ContentResolver;
import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import de.cyface.persistence.DefaultFileAccess;
import de.cyface.persistence.FileAccessLayer;
import de.cyface.persistence.IdentifierTable;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.Point3d;
import de.cyface.persistence.serialization.Point3dFile;
import de.cyface.utils.Validate;

/**
 * This class (and the module testutils) exist to be able to share test code between modules.
 * It's located in the main folder to be compiled and imported as dependency in the testImplementations.
 *
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 3.0.0
 */
public class SharedTestUtils {

    /**
     * Inserts a test acceleration into the database content provider accessed by the test.
     *
     * @param context The {@link Context} required to access the persistence layer
     * @param measurementId The id of the test {@link Measurement}.
     * @param timestamp A fake test timestamp of the acceleration.
     * @param x A fake test x coordinate of the acceleration.
     * @param y A fake test y coordinate of the acceleration.
     * @param z A fake test z coordinate of the acceleration.
     */
    public static void insertTestAcceleration(@NonNull final Context context, final long measurementId,
            final long timestamp, final double x, final double y, final double z) {
        insertPoint3d(context, measurementId, Point3dFile.ACCELERATIONS_FOLDER_NAME,
                Point3dFile.ACCELERATIONS_FILE_EXTENSION, timestamp, x, y, z);
    }

    /**
     * Inserts a test rotation into the database content provider accessed by the test.
     *
     * @param context The {@link Context} required to access the persistence layer
     * @param measurementId The id of the test {@link Measurement}.
     * @param timestamp A fake test timestamp of the direction.
     * @param x A fake test x coordinate of the direction.
     * @param y A fake test y coordinate of the direction.
     * @param z A fake test z coordinate of the direction.
     */
    public static void insertTestRotation(@NonNull final Context context, final long measurementId,
            final long timestamp, final double x, final double y, final double z) {
        insertPoint3d(context, measurementId, Point3dFile.ROTATIONS_FOLDER_NAME, Point3dFile.ROTATION_FILE_EXTENSION,
                timestamp, x, y, z);
    }

    /**
     * Inserts a test direction into the database content provider accessed by the test.
     *
     * @param context The {@link Context} required to access the persistence layer
     * @param measurementId The id of the test {@link Measurement}.
     * @param timestamp A fake test timestamp of the direction.
     * @param x A fake test x coordinate of the direction.
     * @param y A fake test y coordinate of the direction.
     * @param z A fake test z coordinate of the direction.
     */
    public static void insertTestDirection(@NonNull final Context context, final long measurementId,
            final long timestamp, final double x, final double y, final double z) {
        insertPoint3d(context, measurementId, Point3dFile.DIRECTIONS_FOLDER_NAME, Point3dFile.DIRECTION_FILE_EXTENSION,
                timestamp, x, y, z);
    }

    /**
     * Inserts a test {@link Point3d} into the database content provider accessed by the test.
     *
     * @param context The {@link Context} required to access the persistence layer
     * @param measurementId The id of the test {@link Measurement}.
     * @param folderName The folder name defining the {@link Point3d} type of the file
     * @param fileExtension the extension of the file type
     * @param timestamp A fake test timestamp of the {@code Point3d}.
     * @param x A fake test x coordinate of the {@code Point3d}.
     * @param y A fake test y coordinate of the {@code Point3d}.
     * @param z A fake test z coordinate of the {@code Point3d}.
     */
    public static void insertPoint3d(@NonNull final Context context, final long measurementId,
            @NonNull final String folderName, @NonNull final String fileExtension, final long timestamp, final double x,
            final double y, final double z) {
        final Point3dFile file = new Point3dFile(context, measurementId, folderName, fileExtension);
        final List<Point3d> points = new ArrayList<>();
        points.add(new Point3d((float)x, (float)y, (float)z, timestamp));
        file.append(points);
    }

    /**
     * This deserializes a {@link File} for testing.
     *
     * @param fileAccessLayer The {@link FileAccessLayer} used to access the files.
     * @param file The {@link File} to access
     * @param pointCount The number of points in this file. This number is stored in the associated measurement
     * @return the {@link Point3d} data restored from the {@code Point3dFile}
     */
    public static List<Point3d> deserialize(@NonNull final FileAccessLayer fileAccessLayer, @NonNull File file,
            final int pointCount) {
        final byte[] bytes = fileAccessLayer.loadBytes(file);
        return deserializePoint3dData(bytes, pointCount);
    }

    /**
     * Deserialized {@link Point3d} data.
     *
     * @param point3dFileBytes The bytes loaded from the {@link Point3dFile}
     * @return The {@link Point3d} loaded from the file
     */
    private static List<Point3d> deserializePoint3dData(final byte[] point3dFileBytes, final int pointCount) {

        Validate.isTrue(point3dFileBytes.length == pointCount * BYTES_IN_ONE_POINT_3D_ENTRY);
        if (pointCount == 0) {
            return new ArrayList<>();
        }

        // Deserialize bytes
        final List<Point3d> points = new ArrayList<>();
        final ByteBuffer buffer = ByteBuffer.wrap(point3dFileBytes);
        for (int i = 0; i < pointCount; i++) {
            final long timestamp = buffer.order(ByteOrder.BIG_ENDIAN).getLong();
            final double x = buffer.order(ByteOrder.BIG_ENDIAN).getDouble();
            final double y = buffer.order(ByteOrder.BIG_ENDIAN).getDouble();
            final double z = buffer.order(ByteOrder.BIG_ENDIAN).getDouble();
            // final long timestamp = buffer.order(ByteOrder.BIG_ENDIAN).getLong();
            points.add(new Point3d((float)x, (float)y, (float)z, timestamp));
        }

        Log.d(TAG, "Deserialized Points: " + points.size());
        return points;
    }

    /**
     * Removes everything from the local persistent data storage to allow reproducible test results.
     *
     * (!) This removes both the data from file persistence and the database which will also reset the device id.
     * This is not part of the persistence layer as we want to avoid that this is used outside the test code.
     *
     * This method mut be in the {@link SharedTestUtils} to ensure multiple modules can access it in androidTests!
     *
     * @param context The {@link Context} required to access the file persistence layer
     * @param resolver The {@link ContentResolver} required to access the database
     * @return number of rows removed from the database and number of <b>FILES</b> (not points) deleted. The earlier
     *         includes {@link Measurement}s and {@link GeoLocation}s and the {@link IdentifierTable} (i.e. device id).
     *         The later includes the {@link Point3dFile}s.
     */
    public static int clear(@NonNull final Context context, @NonNull final ContentResolver resolver,
            @NonNull final String authority) {

        final FileAccessLayer fileAccessLayer = new DefaultFileAccess();

        // Remove {@code Point3dFile}s and their parent folders
        int removedFiles = 0;

        final File accelerationFolder = fileAccessLayer.getFolderPath(context, Point3dFile.ACCELERATIONS_FOLDER_NAME);
        if (accelerationFolder.exists()) {
            Validate.isTrue(accelerationFolder.isDirectory());
            final File[] accelerationFiles = accelerationFolder.listFiles();
            for (File file : accelerationFiles) {
                Validate.isTrue(file.delete());
            }
            removedFiles += accelerationFiles.length;
            Validate.isTrue(accelerationFolder.delete());
        }

        final File rotationFolder = fileAccessLayer.getFolderPath(context, Point3dFile.ROTATIONS_FOLDER_NAME);
        if (rotationFolder.exists()) {
            Validate.isTrue(rotationFolder.isDirectory());
            final File[] rotationFiles = rotationFolder.listFiles();
            for (File file : rotationFiles) {
                Validate.isTrue(file.delete());
            }
            removedFiles += rotationFiles.length;
            Validate.isTrue(rotationFolder.delete());
        }

        final File directionFolder = fileAccessLayer.getFolderPath(context, Point3dFile.DIRECTIONS_FOLDER_NAME);
        if (directionFolder.exists()) {
            Validate.isTrue(directionFolder.isDirectory());
            final File[] directionFiles = directionFolder.listFiles();
            for (File file : directionFiles) {
                Validate.isTrue(file.delete());
            }
            removedFiles += directionFiles.length;
            Validate.isTrue(directionFolder.delete());
        }

        // Remove database entries
        final int removedGeoLocations = resolver.delete(getGeoLocationsUri(authority), null, null);
        final int removedMeasurements = resolver.delete(getMeasurementUri(authority), null, null);
        int removedDatabaseRows = 0;
        removedDatabaseRows += resolver.delete(getIdentifierUri(authority), null, null);
        removedDatabaseRows += removedGeoLocations;
        removedDatabaseRows += removedMeasurements;
        return removedFiles + removedDatabaseRows;
    }
}