package de.cyface.testutils;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import androidx.annotation.NonNull;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.Point3d;
import de.cyface.persistence.serialization.Point3dFile;

/**
 * This class (and the module testutils) exist to be able to share test code between modules.
 * It's located in the main folder to be compiled and imported as dependency in the testImplementations.
 *
 * FIXME: I did split the MeasurementPersistence class into Persistence which was located in persistence module
 * this way I was able to reference the Persistence.newMeasurement etc. methods in here. This module is used in
 * a synchronization test which cannot see the datacapturing module!
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
        insertTestPoint3d(context, measurementId, Point3dFile.ACCELERATIONS_FOLDER_NAME,
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
        insertTestPoint3d(context, measurementId, Point3dFile.ROTATIONS_FOLDER_NAME,
                Point3dFile.ROTATION_FILE_EXTENSION, timestamp, x, y, z);
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
        insertTestPoint3d(context, measurementId, Point3dFile.DIRECTIONS_FOLDER_NAME,
                Point3dFile.DIRECTION_FILE_EXTENSION, timestamp, x, y, z);
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
    private static void insertTestPoint3d(@NonNull final Context context, final long measurementId,
            @NonNull final String folderName, @NonNull final String fileExtension, final long timestamp, final double x,
            final double y, final double z) {
        final Point3dFile file = new Point3dFile(context, measurementId, folderName, fileExtension);
        final List<Point3d> points = new ArrayList<>();
        points.add(new Point3d((float)x, (float)y, (float)z, timestamp));
        file.append(points);
    }
}