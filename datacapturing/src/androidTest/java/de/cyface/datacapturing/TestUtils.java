package de.cyface.datacapturing;

import java.util.ArrayList;
import java.util.List;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;

import de.cyface.persistence.IdentifierTable;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.Persistence;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.Point3D;
import de.cyface.persistence.model.Vehicle;
import de.cyface.persistence.serialization.AccelerationsFile;
import de.cyface.persistence.serialization.DirectionsFile;
import de.cyface.persistence.serialization.GeoLocationsFile;
import de.cyface.persistence.serialization.MetaFile;
import de.cyface.persistence.serialization.RotationsFile;
import de.cyface.utils.Validate;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * Contains utility methods and constants required by the tests within the persistence project.
 * FIXME: this is a duplicate of synchronization/androidTest/TestUtils because IntelliJ somehow cannot find the symbol although it should be visible
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 2.1.0
 */
public final class TestUtils {
    /**
     * The content provider authority used during tests. This must be the same as in the manifest and the authenticator
     * configuration.
     */
    final static String AUTHORITY = "de.cyface.persistence.test.provider";

    static Uri getIdentifierUri() {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(IdentifierTable.URI_PATH).build();
    }

    /**
     * Inserts a test direction into the database content provider accessed by the test.
     *
     * @param resolver The client to access the content provider storing the data.
     * @param deviceId unique id for the device
     * @param nextMeasurementIdentifier The device wide unique identifier of the next test measurement.
     */
    static void insertTestIndentifiers(final @NonNull ContentResolver resolver, final String deviceId,
            final long nextMeasurementIdentifier) {
        ContentValues values = new ContentValues();
        values.put(IdentifierTable.COLUMN_DEVICE_ID, deviceId);
        values.put(IdentifierTable.COLUMN_NEXT_MEASUREMENT_ID, nextMeasurementIdentifier);
        resolver.insert(getIdentifierUri(), values);
    }

    /**
     * Inserts a test direction into the database content provider accessed by the test.
     *
     * @param context The context to access the persistence layer.
     * @param measurementIdentifier The device wide unique identifier of the test measurement.
     * @param timestamp A fake test timestamp of the direction.
     * @param x A fake test x coordinate of the direction.
     * @param y A fake test y coordinate of the direction.
     * @param z A fake test z coordinate of the direction.
     */
    public static void insertTestDirection(final @NonNull Context context, final long measurementIdentifier,
                                           final long timestamp, final double x, final double y, final double z) {
        DirectionsFile directionsFile = new DirectionsFile(context, measurementIdentifier);
        List<Point3D> points = new ArrayList<>();
        points.add(new Point3D((float)x, (float)y, (float)z, timestamp));
        directionsFile.append(points);
    }

    /**
     * Inserts a test rotation into the database content provider accessed by the test.
     *
     * @param context The context to access the persistence layer.
     * @param measurementIdentifier The device wide unique identifier of the test measurement.
     * @param timestamp A fake test timestamp of the direction.
     * @param x A fake test x coordinate of the direction.
     * @param y A fake test y coordinate of the direction.
     * @param z A fake test z coordinate of the direction.
     */
    public static void insertTestRotation(final @NonNull Context context, final long measurementIdentifier,
                                          final long timestamp, final double x, final double y, final double z) {
        RotationsFile rotationsFile = new RotationsFile(context, measurementIdentifier);
        List<Point3D> points = new ArrayList<>();
        points.add(new Point3D((float)x, (float)y, (float)z, timestamp));
        rotationsFile.append(points);
    }

    /**
     * Inserts a test acceleration into the database content provider accessed by the test.
     *
     * @param context The context to access the persistence layer.
     * @param measurementIdentifier The device wide unique identifier of the test measurement.
     * @param timestamp A fake test timestamp of the acceleration.
     * @param x A fake test x coordinate of the acceleration.
     * @param y A fake test y coordinate of the acceleration.
     * @param z A fake test z coordinate of the acceleration.
     */
    public static void insertTestAcceleration(final @NonNull Context context, final long measurementIdentifier,
                                              final long timestamp, final double x, final double y, final double z) {
        AccelerationsFile accelerationsFile = new AccelerationsFile(context, measurementIdentifier);
        List<Point3D> points = new ArrayList<>();
        points.add(new Point3D((float)x, (float)y, (float)z, timestamp));
        accelerationsFile.append(points);
    }

    /**
     * Inserts a test geo location into the database content provider accessed by the test.
     *
     * @param context The context to access the persistence layer.
     * @param measurementIdentifier The device wide unique identifier of the test measurement.
     * @param timestamp A fake test timestamp of the geo location.
     * @param lat The fake test latitude of the geo location.
     * @param lon The fake test longitude of the geo location.
     * @param speed The fake test speed of the geo location.
     * @param accuracy The fake test accuracy of the geo location.
     */
    public static void insertTestGeoLocation(final @NonNull Context context, final long measurementIdentifier,
                                             final long timestamp, final double lat, final double lon, final double speed, final int accuracy) {
        GeoLocationsFile geoLocationsFile = new GeoLocationsFile(context, measurementIdentifier);
        geoLocationsFile.append(new GeoLocation(lat, lon, timestamp, speed, accuracy));
    }

    /**
     * Inserts a test measurement into the database content provider accessed by the test. To add data to the
     * measurement use some or all of
     * {@link #insertTestGeoLocation(Context, long, long, double, double, double, int)}},
     * {@link #insertTestAcceleration(Context, long, long, double, double, double)},
     * {@link #insertTestDirection(Context, long, long, double, double, double)} and
     * {@link #insertTestRotation(Context, long, long, double, double, double)}.
     *
     * @param vehicle The vehicle type of the measurement. A common value is "UNKNOWN" if you do not care.
     * @return The database identifier of the created measurement.
     */
    public static Measurement insertTestMeasurement(final @NonNull Persistence persistence,
                                                    @NonNull final ContentResolver resolver, final @NonNull Vehicle vehicle) {
        // usually called in DataCapturingService#Constructor
        persistence.restoreOrCreateDeviceId(resolver);

        return persistence.newMeasurement(vehicle);
    }

    /**
     * Delete all persistent storage such as identifiers and measurements.
     *
     * @param resolver The {@link ContentResolver} to access the identifier counters
     * @return the number of measurements deleted
     */
    static int clear(final @NonNull Context context, final @NonNull ContentResolver resolver) {
        int ret = 0;
        Persistence persistence = new Persistence(context, resolver, AUTHORITY);
        persistence.clear();
        resolver.delete(getIdentifierUri(), null, null);
        return ret;
    }

    public static Measurement insertSampleMeasurement(final boolean finished, final boolean synced,
                                               final Persistence persistence) throws NoSuchMeasurementException {
        Validate.isTrue(!synced || finished,
                "You can only create a finished synced measurement, not a unfinished synced one.");

        final Measurement measurement = insertTestMeasurement(persistence, persistence.getResolver(), Vehicle.UNKNOWN);
        final long measurementIdentifier = measurement.getIdentifier();
        insertTestGeoLocation(persistence.getContext(), measurementIdentifier, 1503055141000L, 49.9304133333333,
                8.82831833333333, 0.0, 940);
        insertTestAcceleration(persistence.getContext(), measurementIdentifier, 1501662635973L, 10.1189575, -0.15088624,
                0.2921924);
        insertTestRotation(persistence.getContext(), measurementIdentifier, 1501662635981L, 0.001524045, 0.0025423833,
                -0.0010279021);
        insertTestDirection(persistence.getContext(), measurementIdentifier, 1501662636010L, 7.65, -32.4, -71.4);

        if (finished) {
            // Write point counters to MetaFile
            MetaFile.append(persistence.getContext(), measurementIdentifier, new MetaFile.PointMetaData(1, 1, 1, 1));
            // Finish measurement
            persistence.closeMeasurement(measurement);
        }

        if (synced) {
            persistence.markAsSynchronized(measurement);
        }

        // Assert that data is in the database
        final Measurement loadedMeasurement;
        if (finished) {
            if (!synced) {
                loadedMeasurement = persistence.loadFinishedMeasurement(measurementIdentifier);
            } else {
                loadedMeasurement = persistence.loadSyncedMeasurement(measurementIdentifier);
            }
            // We cannot check this for an open, un-paused measurement because the deserialization does
            // not work without the point counts been written to the MetaFile
            List<GeoLocation> geoLocations = persistence.loadTrack(loadedMeasurement);
            assertThat(geoLocations.size(), is(1));
        } else {
            loadedMeasurement = persistence.loadOpenMeasurement(measurementIdentifier);
        }
        assertThat(loadedMeasurement, notNullValue());
        return measurement;
    }
}
