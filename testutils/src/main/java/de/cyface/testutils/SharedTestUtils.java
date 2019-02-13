package de.cyface.testutils;

import static de.cyface.persistence.Constants.TAG;
import static de.cyface.persistence.Utils.getGeoLocationsUri;
import static de.cyface.persistence.Utils.getMeasurementUri;
import static de.cyface.persistence.model.MeasurementStatus.FINISHED;
import static de.cyface.persistence.model.MeasurementStatus.OPEN;
import static de.cyface.persistence.model.MeasurementStatus.SYNCED;
import static de.cyface.persistence.serialization.MeasurementSerializer.BYTES_IN_ONE_POINT_3D_ENTRY;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import de.cyface.persistence.DefaultFileAccess;
import de.cyface.persistence.DefaultPersistenceBehaviour;
import de.cyface.persistence.FileAccessLayer;
import de.cyface.persistence.GeoLocationsTable;
import de.cyface.persistence.IdentifierTable;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.Point3d;
import de.cyface.persistence.model.PointMetaData;
import de.cyface.persistence.model.Vehicle;
import de.cyface.persistence.serialization.MeasurementSerializer;
import de.cyface.persistence.serialization.Point3dFile;
import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;

/**
 * This class (and the module testutils) exist to be able to share test code between modules.
 * It's located in the main folder to be compiled and imported as dependency in the testImplementations.
 *
 * @author Armin Schnabel
 * @version 3.0.0
 * @since 3.0.0
 */
public class SharedTestUtils {

    /**
     * Inserts a test {@link Point3d} into the database content provider accessed by the test.
     *
     * @param point3dFile existing file to append the data to
     * @param timestamp A fake test timestamp of the {@code Point3d}.
     * @param x A fake test x coordinate of the {@code Point3d}.
     * @param y A fake test y coordinate of the {@code Point3d}.
     * @param z A fake test z coordinate of the {@code Point3d}.
     */
    public static void insertPoint3d(@NonNull final Point3dFile point3dFile, final long timestamp, final double x,
            final double y, final double z) {
        final List<Point3d> points = new ArrayList<>();
        points.add(new Point3d((float)x, (float)y, (float)z, timestamp));
        insertPoint3ds(point3dFile, points);
    }

    /**
     * Inserts {@link Point3d}s into the database content provider accessed by the test.
     * <p>
     * This increases the performance of large tests.
     *
     * @param point3dFile existing file to append the data to
     * @param point3ds Test fake {@code Point3d}s.
     */
    private static void insertPoint3ds(@NonNull final Point3dFile point3dFile, final List<Point3d> point3ds) {

        // Avoid OOM when adding too much data at once
        final int insertLimit = 100_000;
        int nextInsertedIndex = 0;
        while (nextInsertedIndex < point3ds.size()) {
            final List<Point3d> sublist = point3ds.subList(nextInsertedIndex,
                    Math.min(nextInsertedIndex + insertLimit, point3ds.size()));
            point3dFile.append(sublist);
            nextInsertedIndex += sublist.size();
            Log.d(TAG, "Inserted " + nextInsertedIndex);
        }
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
    public static int clearPersistenceLayer(@NonNull final Context context, @NonNull final ContentResolver resolver,
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
        // TODO: why does this break the life-cycle tests in DataCapturingServiceTest? - can't find an answer ...
        // However this should be okay to ignore for now as the identifier table should never be reset unless the
        // database itself is removed when the app is uninstalled or the app data is deleted.
        // final int removedIdentifierRows = resolver.delete(getIdentifierUri(authority), null, null);
        return removedFiles + /* removedIdentifierRows + */ removedGeoLocations + removedMeasurements;
    }

    /**
     * This method inserts a {@link Measurement} into the persistence layer. Does not use the
     * {@code CapturingPersistenceBehaviour} but the {@link DefaultPersistenceBehaviour}.
     *
     * @param point3dCount The number of point3ds to insert (of each sensor type).
     * @param locationCount The number of location points to insert.
     * @throws NoSuchMeasurementException – if there was no measurement with the id .
     * @throws CursorIsNullException – If ContentProvider was inaccessible
     */
    public static Measurement insertSampleMeasurementWithData(@NonNull final Context context, final String authority,
            final MeasurementStatus status, final PersistenceLayer<DefaultPersistenceBehaviour> persistence,
            final int point3dCount, final int locationCount) throws NoSuchMeasurementException, CursorIsNullException {
        Validate.isTrue(point3dCount > 0);
        Validate.isTrue(locationCount > 0);

        final List<GeoLocation> geoLocations = new ArrayList<>();
        Measurement measurement = insertMeasurementEntry(persistence, Vehicle.UNKNOWN);
        final long measurementIdentifier = measurement.getIdentifier();
        for (int i = 0; i < locationCount; i++) {
            // We add some salt to make sure the compression of the data is realistic
            // This is required as the testOnPerformSyncWithLargeData test requires large data
            final double salt = Math.random();
            geoLocations.add(new GeoLocation(49.9304133333333 + salt, 8.82831833333333 + salt, 1503055141000L + i,
                    0.0 + salt, 940f + (float)salt));
        }
        insertGeoLocations(context.getContentResolver(), authority, measurement.getIdentifier(), geoLocations);

        // Insert file base data
        final Point3dFile accelerationsFile = new Point3dFile(context, measurementIdentifier,
                Point3dFile.ACCELERATIONS_FOLDER_NAME, Point3dFile.ACCELERATIONS_FILE_EXTENSION);
        final Point3dFile rotationsFile = new Point3dFile(context, measurementIdentifier,
                Point3dFile.ROTATIONS_FOLDER_NAME, Point3dFile.ROTATION_FILE_EXTENSION);
        final Point3dFile directionsFile = new Point3dFile(context, measurementIdentifier,
                Point3dFile.DIRECTIONS_FOLDER_NAME, Point3dFile.DIRECTION_FILE_EXTENSION);

        final List<Point3d> aPoints = new ArrayList<>();
        final List<Point3d> rPoints = new ArrayList<>();
        final List<Point3d> dPoints = new ArrayList<>();

        for (int i = 0; i < point3dCount; i++) {
            // We add some salt to make sure the compression of the data is realistic
            // This is required as the testOnPerformSyncWithLargeData test requires large data
            final float salt = (float)Math.random();
            aPoints.add(new Point3d(10.1189575f + salt, -0.15088624f + salt, 0.2921924f + salt, 1501662635973L + i));
            rPoints.add(
                    new Point3d(0.001524045f + salt, 0.0025423833f + salt, -0.0010279021f + salt, 1501662635981L + i));
            dPoints.add(new Point3d(7.65f + salt, -32.4f + salt, -71.4f + salt, 1501662636010L + i));
        }
        insertPoint3ds(accelerationsFile, aPoints);
        insertPoint3ds(rotationsFile, rPoints);
        insertPoint3ds(directionsFile, dPoints);

        if (status == FINISHED || status == MeasurementStatus.SYNCED) {
            // Store PointMetaData
            final PointMetaData pointMetaData = new PointMetaData(1, 1, 1,
                    MeasurementSerializer.PERSISTENCE_FILE_FORMAT_VERSION);
            persistence.storePointMetaData(pointMetaData, measurementIdentifier);
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
        List<GeoLocation> loadedGeoLocations = persistence.loadTrack(measurementIdentifier);
        assertThat(loadedGeoLocations.size(), is(locationCount));

        // We can only check the PointMetaData for measurements which are not open anymore (else it's still in cache)
        if (status != OPEN) {
            // we explicitly reload the measurement to make sure we have it's current attributes
            measurement = persistence.loadMeasurement(measurementIdentifier);
            assertThat(measurement.getAccelerations(), is(equalTo(1)));
            assertThat(measurement.getRotations(), is(equalTo(1)));
            assertThat(measurement.getDirections(), is(equalTo(1)));
            assertThat(measurement.getFileFormatVersion(),
                    is(equalTo(MeasurementSerializer.PERSISTENCE_FILE_FORMAT_VERSION)));
        }
        return measurement;
    }

    /**
     * Inserts a test {@code Measurement} into the database content provider accessed by the test. To add data to the
     * {@code Measurement} use some or all of
     * {@link #insertGeoLocation(ContentResolver, String, long, long, double, double, double, int)}
     * {@link SharedTestUtils#insertPoint3d(Point3dFile, long, double, double, double)} (Context, long, long, double,
     * double, double)},
     *
     * @param vehicle The {@link Vehicle} type of the {@code Measurement}. A common value is {@link Vehicle#UNKNOWN} if
     *            you do not care.
     * @return The database identifier of the created {@link Measurement}.
     */
    public static Measurement insertMeasurementEntry(
            final @NonNull PersistenceLayer<DefaultPersistenceBehaviour> persistence, final @NonNull Vehicle vehicle)
            throws CursorIsNullException {

        // usually called in DataCapturingService#Constructor
        persistence.restoreOrCreateDeviceId();

        return persistence.newMeasurement(vehicle);
    }

    /**
     * Inserts a test {@link GeoLocation} into the database content provider accessed by the test.
     *
     * @param resolver The {@link ContentResolver} required to access the database
     * @param authority The authority string required to access the database
     * @param measurementIdentifier The identifier of the test {@link Measurement}.
     * @param timestamp A fake test timestamp of the {@code GeoLocation}.
     * @param lat The fake test latitude of the {@code GeoLocation}.
     * @param lon The fake test longitude of the {@code GeoLocation}.
     * @param speed The fake test speed of the {@code GeoLocation}.
     * @param accuracy The fake test accuracy of the {@code GeoLocation}.
     */
    public static void insertGeoLocation(final ContentResolver resolver, final String authority,
            final long measurementIdentifier, final long timestamp, final double lat, final double lon,
            final double speed, final int accuracy) {

        ContentValues values = new ContentValues();
        values.put(GeoLocationsTable.COLUMN_ACCURACY, accuracy);
        values.put(GeoLocationsTable.COLUMN_GEOLOCATION_TIME, timestamp);
        values.put(GeoLocationsTable.COLUMN_LAT, lat);
        values.put(GeoLocationsTable.COLUMN_LON, lon);
        values.put(GeoLocationsTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
        values.put(GeoLocationsTable.COLUMN_SPEED, speed);
        resolver.insert(getGeoLocationsUri(authority), values);
    }

    /**
     * Inserts test {@link GeoLocation}s into the database content provider accessed by the test.
     * <p>
     * This increases the performance of large tests and avoids "failed binder transaction - parcel size ..." error.
     *
     * @param resolver The {@link ContentResolver} required to access the database
     * @param authority The authority string required to access the database
     * @param measurementIdentifier The identifier of the test {@link Measurement}.
     * @param geoLocations Test fake {@code GeoLocation}s to add.
     */
    private static void insertGeoLocations(final ContentResolver resolver, final String authority,
            final long measurementIdentifier, final List<GeoLocation> geoLocations) {
        final List<ContentValues> valuesList = new ArrayList<>();
        for (final GeoLocation geoLocation : geoLocations) {
            final ContentValues values = new ContentValues();
            values.put(GeoLocationsTable.COLUMN_ACCURACY, geoLocation.getAccuracy());
            values.put(GeoLocationsTable.COLUMN_GEOLOCATION_TIME, geoLocation.getTimestamp());
            values.put(GeoLocationsTable.COLUMN_LAT, geoLocation.getLat());
            values.put(GeoLocationsTable.COLUMN_LON, geoLocation.getLon());
            values.put(GeoLocationsTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
            values.put(GeoLocationsTable.COLUMN_SPEED, geoLocation.getSpeed());
            valuesList.add(values);
        }

        // This avoids "failed binder transaction - parcel size ..." error
        final int maxBatchSize = 2_000;
        int nextInsertIndex = 0;
        while (nextInsertIndex < valuesList.size()) {
            final List<ContentValues> sublist = valuesList.subList(nextInsertIndex,
                    Math.min(nextInsertIndex + maxBatchSize, valuesList.size()));
            ContentValues[] subArray = new ContentValues[sublist.size()];
            subArray = sublist.toArray(subArray);
            resolver.bulkInsert(getGeoLocationsUri(authority), subArray);
            nextInsertIndex += subArray.length;
            Log.d(TAG, "Inserted " + nextInsertIndex);
        }
    }
}
