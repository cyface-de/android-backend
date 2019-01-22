package de.cyface.synchronization;

import static de.cyface.persistence.model.MeasurementStatus.FINISHED;
import static de.cyface.persistence.model.MeasurementStatus.OPEN;
import static de.cyface.persistence.model.MeasurementStatus.SYNCED;
import static de.cyface.testutils.SharedTestUtils.getGeoLocationsUri;
import static de.cyface.testutils.SharedTestUtils.getIdentifierUri;
import static de.cyface.testutils.SharedTestUtils.getMeasurementUri;
import static de.cyface.testutils.SharedTestUtils.insertTestPoint3d;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import androidx.annotation.NonNull;
import de.cyface.persistence.FileUtils;
import de.cyface.persistence.GeoLocationsTable;
import de.cyface.persistence.IdentifierTable;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.PointMetaData;
import de.cyface.persistence.model.Vehicle;
import de.cyface.persistence.serialization.MeasurementSerializer;
import de.cyface.persistence.serialization.Point3dFile;
import de.cyface.testutils.SharedTestUtils;
import de.cyface.utils.DataCapturingException;
import de.cyface.utils.Validate;

/**
 * Contains utility methods and constants required by the tests within the synchronization project.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.3.0
 * @since 2.1.0
 */
public final class TestUtils {
    /**
     * The tag used to identify Logcat messages from this module.
     */
    final static String TAG = Constants.TAG + ".test";
    /**
     * The content provider authority used during tests. This must be the same as in the manifest and the authenticator
     * configuration.
     */
    final static String AUTHORITY = "de.cyface.synchronization.test.provider";
    /**
     * The account type used during testing. This must be the same as in the authenticator configuration.
     */
    final static String ACCOUNT_TYPE = "de.cyface.synchronization.test";
    /**
     * An username used by the tests to set up a Cyface account for synchronization.
     */
    final static String DEFAULT_USERNAME = "admin";
    /**
     * A password used by the tests to set up a Cyface account for synchronization.
     */
    final static String DEFAULT_PASSWORD = "secret";
    /**
     * Path to an API available for testing.
     */
    final static String TEST_API_URL = "https://s1.cyface.de:9090/api/v2";

    /**
     * Inserts a test {@link GeoLocation} into the database content provider accessed by the test.
     *
     * @param measurementIdentifier The identifier of the test {@link Measurement}.
     * @param timestamp A fake test timestamp of the {@code GeoLocation}.
     * @param lat The fake test latitude of the {@code GeoLocation}.
     * @param lon The fake test longitude of the {@code GeoLocation}.
     * @param speed The fake test speed of the {@code GeoLocation}.
     * @param accuracy The fake test accuracy of the {@code GeoLocation}.
     */
    static void insertTestGeoLocation(final ContentResolver resolver, final String authority,
            final long measurementIdentifier, final long timestamp, final double lat, final double lon,
            final double speed, final int accuracy) {

        ContentValues values = new ContentValues();
        values.put(GeoLocationsTable.COLUMN_ACCURACY, accuracy);
        values.put(GeoLocationsTable.COLUMN_GPS_TIME, timestamp);
        values.put(GeoLocationsTable.COLUMN_LAT, lat);
        values.put(GeoLocationsTable.COLUMN_LON, lon);
        values.put(GeoLocationsTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
        values.put(GeoLocationsTable.COLUMN_SPEED, speed);
        resolver.insert(getGeoLocationsUri(authority), values);
    }

    // FIXME: the following methods insertTestMeasurement and insertSampleMeasurement where in the declined PR in
    // SharedTestUtils but now it's not possible without splitting Persistence and MeasurementPersistence

    /**
     * Inserts a test {@code Measurement} into the database content provider accessed by the test. To add data to the
     * {@code Measurement} use some or all of
     * {@link #insertTestGeoLocation(ContentResolver, String, long, long, double, double, double, int)}
     * {@link SharedTestUtils#insertTestAcceleration(Context, long, long, double, double, double)},
     * {@link SharedTestUtils#insertTestRotation(Context, long, long, double, double, double)},
     * {@link SharedTestUtils#insertTestDirection(Context, long, long, double, double, double)}
     *
     * @param vehicle The {@link Vehicle} type of the {@code Measurement}. A common value is {@link Vehicle#UNKNOWN} if
     *            you do not care.
     * @return The database identifier of the created {@link Measurement}.
     */
    public static Measurement insertTestMeasurement(final @NonNull PersistenceLayer persistence,
            final @NonNull Vehicle vehicle) throws DataCapturingException {

        // usually called in DataCapturingService#Constructor
        persistence.restoreOrCreateDeviceId();

        return persistence.newMeasurement(vehicle);
    }

    public static Measurement insertSampleMeasurement(@NonNull final Context context, final String authority,
            final MeasurementStatus status, final PersistenceLayer persistence)
            throws NoSuchMeasurementException, DataCapturingException {

        final Measurement measurement = insertTestMeasurement(persistence, Vehicle.UNKNOWN);
        final long measurementIdentifier = measurement.getIdentifier();
        insertTestGeoLocation(context.getContentResolver(), authority, measurement.getIdentifier(), 1503055141000L,
                49.9304133333333, 8.82831833333333, 0.0, 940);
        insertTestPoint3d(context, measurementIdentifier, Point3dFile.ACCELERATIONS_FOLDER_NAME,
                Point3dFile.ACCELERATIONS_FILE_EXTENSION, 1501662635973L, 10.1189575, -0.15088624, 0.2921924);
        insertTestPoint3d(context, measurementIdentifier, Point3dFile.ROTATIONS_FOLDER_NAME,
                Point3dFile.ROTATION_FILE_EXTENSION, 1501662635981L, 0.001524045, 0.0025423833, -0.0010279021);
        insertTestPoint3d(context, measurementIdentifier, Point3dFile.DIRECTIONS_FOLDER_NAME,
                Point3dFile.DIRECTION_FILE_EXTENSION, 1501662636010L, 7.65, -32.4, -71.4);

        if (status == FINISHED || status == MeasurementStatus.SYNCED) {
            // Store PointMetaData
            final PointMetaData pointMetaData = new PointMetaData(1, 1, 1,
                    MeasurementSerializer.PERSISTENCE_FILE_FORMAT_VERSION);
            persistence.storePointMetaData(pointMetaData, measurementIdentifier);
            // Finish measurement - this was the deprecated finishMeasurement() before CapturingPersistenceBehaviour
            // thus, it will now not update the currentMeasurementIdentifier anymore which should not have been
            // necessary anyway FIXME: ensure that this method is not called from somewhere where capturing is expected
            persistence.setStatus(measurementIdentifier, FINISHED);
        }

        if (status == SYNCED) {
            persistence.markAsSynchronized(measurement);
        }

        // Assert that data is in the database
        final Measurement loadedMeasurement;
        loadedMeasurement = persistence.loadMeasurement(measurementIdentifier);
        assertThat(loadedMeasurement, notNullValue());
        assertThat(persistence.loadMeasurementStatus(measurementIdentifier), is(equalTo(status)));

        // Check the GeoLocations
        List<GeoLocation> geoLocations = persistence.loadTrack(loadedMeasurement);
        assertThat(geoLocations.size(), is(1));

        // We can only check the PointMetaData for measurements which are not open anymore (else it's still in cache)
        if (status != OPEN) {
            final PointMetaData pointMetaData = persistence.loadPointMetaData(measurementIdentifier);
            assertThat(pointMetaData.getAccelerationPointCounter(), is(equalTo(1)));
            assertThat(pointMetaData.getRotationPointCounter(), is(equalTo(1)));
            assertThat(pointMetaData.getDirectionPointCounter(), is(equalTo(1)));
            assertThat(pointMetaData.getPersistenceFileFormatVersion(),
                    is(equalTo(MeasurementSerializer.PERSISTENCE_FILE_FORMAT_VERSION)));
        }
        return measurement;
    }

    /**
     * Removes everything from the local persistent data storage to allow reproducible test results.
     * (!) This removes both the data from file persistence and the database which will also reset the device id.
     * This is not part of the persistence layer as we want to avoid that this is used outside the test code.
     *
     * @param context The {@link Context} required to access the file persistence layer
     * @param resolver The {@link ContentResolver} required to access the database
     * @return number of rows removed from the database and number of files deleted. The earlier includes
     *         {@link Measurement}s and {@link GeoLocation}s and the {@link IdentifierTable} (i.e. device id). The later
     *         includes the {@link Point3dFile}s.
     */
    public static int clear(@NonNull final Context context, @NonNull final ContentResolver resolver,
            final String authority) {

        // Remove {@code Point3dFile}s and their parent folders
        int removedFiles = 0;
        final File accelerationFolder = FileUtils.getFolderPath(context, Point3dFile.ACCELERATIONS_FOLDER_NAME);
        final File rotationFolder = FileUtils.getFolderPath(context, Point3dFile.ROTATIONS_FOLDER_NAME);
        final File directionFolder = FileUtils.getFolderPath(context, Point3dFile.DIRECTIONS_FOLDER_NAME);
        final List<File> accelerationFiles = new ArrayList<>(Arrays.asList(accelerationFolder.listFiles()));
        final List<File> rotationFiles = new ArrayList<>(Arrays.asList(rotationFolder.listFiles()));
        final List<File> directionFiles = new ArrayList<>(Arrays.asList(directionFolder.listFiles()));
        for (File file : accelerationFiles) {
            Validate.isTrue(file.delete());
        }
        removedFiles += accelerationFiles.size();
        for (File file : rotationFiles) {
            Validate.isTrue(file.delete());
        }
        removedFiles += rotationFiles.size();
        for (File file : directionFiles) {
            Validate.isTrue(file.delete());
        }
        removedFiles += directionFiles.size();
        Validate.isTrue(accelerationFolder.delete());
        Validate.isTrue(rotationFolder.delete());
        Validate.isTrue(directionFolder.delete());

        // Remove database entries
        int removedDatabaseRows = 0;
        removedDatabaseRows += resolver.delete(getGeoLocationsUri(authority), null, null);
        removedDatabaseRows += resolver.delete(getMeasurementUri(authority), null, null);
        removedDatabaseRows += resolver.delete(getIdentifierUri(authority), null, null);
        return removedFiles + removedDatabaseRows;
    }
}
