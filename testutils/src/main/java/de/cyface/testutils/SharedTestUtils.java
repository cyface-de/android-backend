/*
 * Copyright 2018 Cyface GmbH
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
package de.cyface.testutils;

import static de.cyface.persistence.Constants.TAG;
import static de.cyface.persistence.Utils.getEventUri;
import static de.cyface.persistence.Utils.getGeoLocationsUri;
import static de.cyface.persistence.Utils.getMeasurementUri;
import static de.cyface.persistence.model.MeasurementStatus.FINISHED;
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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

import de.cyface.persistence.DefaultFileAccess;
import de.cyface.persistence.DefaultLocationCleaningStrategy;
import de.cyface.persistence.DefaultPersistenceBehaviour;
import de.cyface.persistence.FileAccessLayer;
import de.cyface.persistence.GeoLocationsTable;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.Event;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.Point3d;
import de.cyface.persistence.model.Track;
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
 * @version 4.2.6
 * @since 3.0.0
 */
public class SharedTestUtils {

    /**
     * The following constants were selected so that adding each base+constant results in coordinates with approximately
     * 1 meter distance between base coordinates and base+1*constant coordinates
     */
    private final static double BASE_LAT = 51.100;
    /**
     * see {@link #BASE_LAT}
     */
    private final static double BASE_LON = 13.100;
    /**
     * see {@link #BASE_LAT}
     */
    private final static double LAT_CONSTANT = 0.000008993199995;
    /**
     * see {@link #BASE_LAT}
     */
    private final static double LON_CONSTANT = 0.0000000270697;

    /**
     * To make integration tests reproducible we need to ensure old account (after stopped tests) are cleaned up.
     *
     * @param accountManager The {@link AccountManager} to be used to access accounts.
     * @param accountType The account type to search for.
     * @param authority The authority to access the accounts.
     */
    public static void cleanupOldAccounts(@NonNull final AccountManager accountManager,
            @NonNull final String accountType, @NonNull final String authority) {

        // To make these tests reproducible make sure we don't reuse old sync accounts
        for (final Account account : accountManager.getAccountsByType(accountType)) {
            ContentResolver.removePeriodicSync(account, authority, Bundle.EMPTY);

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
                accountManager.removeAccount(account, null, null);
            } else {
                Validate.isTrue(accountManager.removeAccountExplicitly(account));
            }
        }
        // To ensure reproducibility make sure there is no old account registered
        final Account[] oldAccounts = accountManager.getAccountsByType(accountType);
        assertThat(oldAccounts.length, is(equalTo(0)));
    }

    /**
     * Generates {@link GeoLocation}s with coordinates for testing.
     * <p>
     * See {@param relativeDistance} if you need locations with a specific distance from each other.
     *
     * @param distanceFromBase an integer which defines how much the generated {@code GeoLocation}s are away from each
     *            other. E.g.: If you generate {@code GeoLocation}s using {@param relativeDistance} 1, 3, 5 the
     *            generated locations will be approximately 1 meter per {@param relativeDistance}-difference away from
     *            each other. In this case (1, 5) = 4m distance and (1, 3) or (3, 5) = 2m distance.
     * @return the generated {@code GeoLocation}
     */
    public static GeoLocation generateGeoLocation(final int distanceFromBase) {
        final double salt = Math.random();
        return new GeoLocation(BASE_LAT + distanceFromBase * LAT_CONSTANT, BASE_LON + distanceFromBase * LON_CONSTANT,
                1000000000L + distanceFromBase * 1000L,
                Math.max(DefaultLocationCleaningStrategy.LOWER_SPEED_THRESHOLD,
                        salt * DefaultLocationCleaningStrategy.UPPER_SPEED_THRESHOLD),
                (float)salt * (DefaultLocationCleaningStrategy.UPPER_ACCURACY_THRESHOLD - 1));
    }

    /**
     * Inserts a test {@link Point3d} into the database content provider accessed by the test.
     *
     * @param point3dFile existing file to append the data to
     * @param timestamp A fake test timestamp of the {@code Point3d}.
     * @param x A fake test x coordinate of the {@code Point3d}.
     * @param y A fake test y coordinate of the {@code Point3d}.
     * @param z A fake test z coordinate of the {@code Point3d}.
     */
    @SuppressWarnings({"unused", "WeakerAccess", "RedundantSuppression"})
    // Used by the cyface flavour tests
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
            Log.v(TAG, "Inserted " + nextInsertedIndex);
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
     *         includes {@link Measurement}s, {@link GeoLocation}s and {@link Event}s.
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
        final int removedEvents = resolver.delete(getEventUri(authority), null, null);
        final int removedMeasurements = resolver.delete(getMeasurementUri(authority), null, null);
        // Unclear why this breaks the life-cycle tests in DataCapturingServiceTest.
        // However this should be okay to ignore for now as the identifier table should never be reset unless the
        // database itself is removed when the app is uninstalled or the app data is deleted.
        // final int removedIdentifierRows = resolver.delete(getIdentifierUri(authority), null, null);
        return removedFiles + /* removedIdentifierRows + */ removedGeoLocations + removedEvents + removedMeasurements;
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
    @NonNull
    public static Measurement insertSampleMeasurementWithData(@NonNull final Context context, final String authority,
            final MeasurementStatus status, final PersistenceLayer persistence, final int point3dCount,
            final int locationCount) throws NoSuchMeasurementException, CursorIsNullException {
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
        final int createLimit = 100_000;
        int alreadyInserted = 0;
        for (int i = 0; i + alreadyInserted < point3dCount; i++) {
            // We add some salt to make sure the compression of the data is realistic
            // This is required as the testOnPerformSyncWithLargeData test requires large data
            final float salt = (float)Math.random();
            aPoints.add(new Point3d(10.1189575f + salt, -0.15088624f + salt, 0.2921924f + salt, 1501662635973L + i));
            rPoints.add(
                    new Point3d(0.001524045f + salt, 0.0025423833f + salt, -0.0010279021f + salt, 1501662635981L + i));
            dPoints.add(new Point3d(7.65f + salt, -32.4f + salt, -71.4f + salt, 1501662636010L + i));

            // Avoid OOM when creating too much test data at once
            if (i >= createLimit - 1) {
                insertPoint3ds(accelerationsFile, aPoints);
                insertPoint3ds(rotationsFile, rPoints);
                insertPoint3ds(directionsFile, dPoints);
                alreadyInserted += aPoints.size();
                aPoints.clear();
                rPoints.clear();
                dPoints.clear();
                i = -1; // because "i" is incremented just after this statement (end of loop iteration)
            }
        }
        insertPoint3ds(accelerationsFile, aPoints);
        insertPoint3ds(rotationsFile, rPoints);
        insertPoint3ds(directionsFile, dPoints);

        if (status == FINISHED || status == MeasurementStatus.SYNCED) {
            persistence.storePersistenceFileFormatVersion(MeasurementSerializer.PERSISTENCE_FILE_FORMAT_VERSION,
                    measurementIdentifier);
            persistence.setStatus(measurementIdentifier, FINISHED, false);
        }

        // Check the sensor data (must be before measurements are marked as sync which deletes the data)
        // noinspection ConstantConditions - we may add tests with a 0 count later
        if (point3dCount > 0) {
            assertThat((int)(accelerationsFile.getFile().length() / MeasurementSerializer.BYTES_IN_ONE_POINT_3D_ENTRY),
                    is(equalTo(point3dCount)));
            assertThat((int)(rotationsFile.getFile().length() / MeasurementSerializer.BYTES_IN_ONE_POINT_3D_ENTRY),
                    is(equalTo(point3dCount)));
            assertThat((int)(directionsFile.getFile().length() / MeasurementSerializer.BYTES_IN_ONE_POINT_3D_ENTRY),
                    is(equalTo(point3dCount)));
        }

        if (status == SYNCED) {
            persistence.markAsSynchronized(measurement);
        }

        // Check the measurement entry
        final Measurement loadedMeasurement;
        loadedMeasurement = persistence.loadMeasurement(measurementIdentifier);
        assertThat(loadedMeasurement, notNullValue());
        assertThat(persistence.loadMeasurementStatus(measurementIdentifier), is(equalTo(status)));
        assertThat(loadedMeasurement.getFileFormatVersion(),
                is(equalTo(MeasurementSerializer.PERSISTENCE_FILE_FORMAT_VERSION)));

        // Check the Tracks
        // noinspection unchecked
        final List<Track> loadedTracks = persistence.loadTracks(measurementIdentifier);
        assertThat(loadedTracks.get(0).getGeoLocations().size(), is(locationCount));

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
    @SuppressWarnings({"WeakerAccess", "RedundantSuppression"}) // Used by the cyface flavour tests
    @NonNull
    public static Measurement insertMeasurementEntry(@NonNull final PersistenceLayer persistence,
            @NonNull final Vehicle vehicle) throws CursorIsNullException {

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
    @SuppressWarnings({"WeakerAccess", "unused", "RedundantSuppression"})
    // Used by the cyface flavour tests
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
            Log.v(TAG, "Inserted " + nextInsertIndex);
        }
    }
}
