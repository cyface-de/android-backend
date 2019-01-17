package de.cyface.testutils;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import androidx.annotation.NonNull;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.Point3d;
import de.cyface.persistence.model.PointMetaData;
import de.cyface.persistence.model.Vehicle;
import de.cyface.persistence.serialization.MeasurementSerializer;
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
    public static void insertTestPoint3d(@NonNull final Context context, final long measurementId,
            @NonNull final String folderName, @NonNull final String fileExtension, final long timestamp, final double x,
            final double y, final double z) {
        Point3dFile file = new Point3dFile(context, measurementId, folderName, fileExtension);
        List<Point3d> points = new ArrayList<>();
        points.add(new Point3d((float)x, (float)y, (float)z, timestamp));
        file.append(points);
    }

    /**
     * Inserts a test {@link GeoLocation} into the database content provider accessed by the test.
     *
     * @param measurement The test {@link Measurement}.
     * @param timestamp A fake test timestamp of the {@code GeoLocation}.
     * @param lat The fake test latitude of the {@code GeoLocation}.
     * @param lon The fake test longitude of the {@code GeoLocation}.
     * @param speed The fake test speed of the {@code GeoLocation}.
     * @param accuracy The fake test accuracy of the {@code GeoLocation}.
     */
    public static void insertTestGeoLocation(final Measurement measurement, final long timestamp, final double lat,
            final double lon, final double speed, final int accuracy) {
        // FIXME:
        GeoLocationsFile geoLocationsFile = new GeoLocationsFile(measurement);
        geoLocationsFile.append(new GeoLocation(lat, lon, timestamp, speed, accuracy));
    }

    /**
     * Inserts a test {@code Measurement} into the database content provider accessed by the test. To add data to the
     * {@code Measurement} use some or all of
     * {@link #insertTestGeoLocation(Measurement, long, double, double, double, int)}},
     * {@link #insertTestPoint3d(Context, long, String, String, long, double, double, double)}
     *
     * @param vehicle The {@link Vehicle} type of the {@code Measurement}. A common value is {@link Vehicle#UNKNOWN} if
     *            you do not care.
     * @return The database identifier of the created {@link Measurement}.
     */
    public static Measurement insertTestMeasurement(final @NonNull Persistence persistence, // FIXME: (measurement)
                                                                                            // persistence
            final @NonNull Vehicle vehicle) {
        // usually called in DataCapturingService#Constructor
        persistence.restoreOrCreateDeviceId();

        return persistence.newMeasurement(vehicle);
    }

    public static Measurement insertSampleMeasurement(@NonNull final Context context, final boolean finished,
            final boolean synced, final Persistence persistence) throws NoSuchMeasurementException {
        Validate.isTrue(!synced || finished,
                "You can only create a finished synced measurement, not a unfinished synced one.");

        final Measurement measurement = insertTestMeasurement(persistence, Vehicle.UNKNOWN);
        final long measurementIdentifier = measurement.getIdentifier();
        insertTestGeoLocation(measurement, 1503055141000L, 49.9304133333333, 8.82831833333333, 0.0, 940);
        insertTestPoint3d(context, measurementIdentifier, Point3dFile.ACCELERATIONS_FOLDER_NAME,
                Point3dFile.ACCELERATIONS_FILE_EXTENSION, 1501662635973L, 10.1189575, -0.15088624, 0.2921924);
        insertTestPoint3d(context, measurementIdentifier, Point3dFile.ROTATIONS_FOLDER_NAME,
                Point3dFile.ROTATION_FILE_EXTENSION, 1501662635981L, 0.001524045, 0.0025423833, -0.0010279021);
        insertTestPoint3d(context, measurementIdentifier, Point3dFile.DIRECTIONS_FOLDER_NAME,
                Point3dFile.DIRECTION_FILE_EXTENSION, 1501662636010L, 7.65, -32.4, -71.4);

        if (finished) {
            // Store PointMetaData
            final PointMetaData pointMetaData = new PointMetaData(1, 1, 1,
                    MeasurementSerializer.PERSISTENCE_FILE_FORMAT_VERSION);
            persistence.storePointMetaData(pointMetaData, measurementIdentifier);
            // Finish measurement
            persistence.finishMeasurement(measurementIdentifier);
        }

        if (synced) {
            persistence.markAsSynchronized(measurementIdentifier);
        }

        // Assert that data is in the database
        final Measurement loadedMeasurement;
        if (finished) {
            if (!synced) {
                loadedMeasurement = persistence.loadMeasurement(measurementIdentifier,
                        Measurement.MeasurementStatus.FINISHED);
            } else {
                loadedMeasurement = persistence.loadMeasurement(measurementIdentifier,
                        Measurement.MeasurementStatus.SYNCED);
            }
            // We can only check the number of points for finished measurements (i.e. the point counter is in meta file)
            List<GeoLocation> geoLocations = persistence.loadTrack(loadedMeasurement);
            assertThat(geoLocations.size(), is(1));
        } else {
            loadedMeasurement = persistence.loadMeasurement(measurementIdentifier, Measurement.MeasurementStatus.OPEN);
        }
        assertThat(loadedMeasurement, notNullValue());
        return measurement;
    }
}