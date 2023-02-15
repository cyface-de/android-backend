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
package de.cyface.testutils;

import static de.cyface.persistence.Constants.TAG;
import static de.cyface.persistence.PersistenceLayer.PERSISTENCE_FILE_FORMAT_VERSION;
import static de.cyface.persistence.Utils.getEventUri;
import static de.cyface.persistence.Utils.getGeoLocationsUri;
import static de.cyface.persistence.Utils.getMeasurementUri;
import static de.cyface.persistence.model.MeasurementStatus.FINISHED;
import static de.cyface.persistence.model.MeasurementStatus.SKIPPED;
import static de.cyface.persistence.model.MeasurementStatus.SYNCED;
import static de.cyface.protos.model.Measurement.parseFrom;
import static de.cyface.serializer.model.Point3DType.ACCELERATION;
import static de.cyface.serializer.model.Point3DType.DIRECTION;
import static de.cyface.serializer.model.Point3DType.ROTATION;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

import de.cyface.model.Point3D;
import de.cyface.model.Point3DImpl;
import de.cyface.model.RequestMetaData;
import de.cyface.persistence.DefaultFileAccess;
import de.cyface.persistence.DefaultLocationCleaningStrategy;
import de.cyface.persistence.DefaultPersistenceBehaviour;
import de.cyface.persistence.FileAccessLayer;
import de.cyface.persistence.GeoLocationsTable;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.exception.NoSuchMeasurementException;
import de.cyface.persistence.model.Event;
import de.cyface.persistence.model.GeoLocationV6;
import de.cyface.persistence.model.ParcelableGeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.Modality;
import de.cyface.persistence.model.Track;
import de.cyface.persistence.serialization.Point3DFile;
import de.cyface.protos.model.MeasurementBytes;
import de.cyface.serializer.model.Point3DType;
import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;

/**
 * This class (and the module test-utils) exist to be able to share test code between modules.
 * It's located in the main folder to be compiled and imported as dependency in the testImplementations.
 *
 * @author Armin Schnabel
 * @version 6.1.0
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
    @SuppressWarnings({"WeakerAccess", "RedundantSuppression", "unused"}) // Used by the cyface flavour tests
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
     * Generates {@link ParcelableGeoLocation}s with coordinates for testing.
     * <p>
     * See {@param relativeDistance} if you need locations with a specific distance from each other.
     *
     * @param distanceFromBase an integer which defines how much the generated {@code GeoLocation}s are away from each
     *            other. E.g.: If you generate {@code GeoLocation}s using {@param relativeDistance} 1, 3, 5 the
     *            generated locations will be approximately 1 meter per {@param relativeDistance}-difference away from
     *            each other. In this case (1, 5) = 4m distance and (1, 3) or (3, 5) = 2m distance.
     * @return the generated {@code GeoLocation}
     */
    public static ParcelableGeoLocation generateGeoLocation(final int distanceFromBase) {
        final double salt = Math.random();
        return new ParcelableGeoLocation(BASE_LAT + distanceFromBase * LAT_CONSTANT, BASE_LON + distanceFromBase * LON_CONSTANT,
                1000000000L + distanceFromBase * 1000L,
                Math.max(DefaultLocationCleaningStrategy.LOWER_SPEED_THRESHOLD,
                        salt * DefaultLocationCleaningStrategy.UPPER_SPEED_THRESHOLD),
                salt * (DefaultLocationCleaningStrategy.UPPER_ACCURACY_THRESHOLD - 1));
    }

    /**
     * Generates a {@link GeoLocationV6} from a {@link ParcelableGeoLocation}, adding altitude information.
     *
     * @param location The {@code GeoLocation} to generate the {@code GeoLocationV6} from.
     * @param altitude The altitude in meters above WGS 64 to add.
     * @param verticalAccuracy The vertical accuracy in meters to add.
     * @return The generated location with altitude data.
     */
    public static GeoLocationV6 geoLocationV6(final ParcelableGeoLocation location, final double altitude, double verticalAccuracy) {
        return new GeoLocationV6(location.getTimestamp(), location.getLat(), location.getLon(), altitude, location.getSpeed(), location.getAccuracy(),
                verticalAccuracy);
    }

    public static RequestMetaData.GeoLocation generateRequestMetaDataGeoLocation(final int distanceFromBase) {
        final var location = generateGeoLocation(distanceFromBase);
        return new RequestMetaData.GeoLocation(location.getTimestamp(), location.getLat(), location.getLon());
    }

    /**
     * Inserts a test {@link Point3D} into the database content provider accessed by the test.
     *
     * @param point3DFile existing file to append the data to
     * @param timestamp A fake test timestamp of the {@code Point3D}.
     * @param x A fake test x coordinate of the {@code Point3D}.
     * @param y A fake test y coordinate of the {@code Point3D}.
     * @param z A fake test z coordinate of the {@code Point3D}.
     */
    @SuppressWarnings({"unused", "WeakerAccess", "RedundantSuppression"})
    // Used by the cyface flavour tests
    public static void insertPoint3D(@NonNull final Point3DFile point3DFile, final long timestamp, final double x,
                                     final double y, final double z) {
        final var points = new ArrayList<Point3DImpl>();
        points.add(new Point3DImpl((float)x, (float)y, (float)z, timestamp));
        insertPoint3Ds(point3DFile, points);
    }

    /**
     * Inserts {@link Point3D}s into the database content provider accessed by the test.
     * <p>
     * This increases the performance of large tests.
     *
     * @param point3DFile existing file to append the data to
     * @param point3Ds Test fake {@code Point3D}s.
     */
    private static void insertPoint3Ds(@NonNull final Point3DFile point3DFile, final List<Point3DImpl> point3Ds) {

        // Avoid OOM when adding too much data at once
        final int insertLimit = 100_000;
        int nextInsertedIndex = 0;
        while (nextInsertedIndex < point3Ds.size()) {
            final var sublist = point3Ds.subList(nextInsertedIndex,
                    Math.min(nextInsertedIndex + insertLimit, point3Ds.size()));
            point3DFile.append(sublist);
            nextInsertedIndex += sublist.size();
            Log.v(TAG, "Inserted " + nextInsertedIndex);
        }
    }

    /**
     * This deserializes a {@link Point3DFile} for testing.
     *
     * @param fileAccessLayer The {@link FileAccessLayer} used to access the files.
     * @param file The {@code Point3DFile} to access
     * @param type The {@link Point3DType} for the {@code file} passed as parameter
     * @return the data restored from the {@code Point3DFile}
     * @throws InvalidProtocolBufferException if the {@code Point3DFile} format is unknown
     */
    public static de.cyface.protos.model.Measurement deserialize(final FileAccessLayer fileAccessLayer, final File file,
            final Point3DType type) throws InvalidProtocolBufferException {

        final byte[] bytes = fileAccessLayer.loadBytes(file);
        final MeasurementBytes.Builder measurementBytes = MeasurementBytes.newBuilder()
                .setFormatVersion(PERSISTENCE_FILE_FORMAT_VERSION);

        switch (type) {
            case ACCELERATION:
                measurementBytes.setAccelerationsBinary(ByteString.copyFrom(bytes));
                break;
            case ROTATION:
                measurementBytes.setRotationsBinary(ByteString.copyFrom(bytes));
                break;
            case DIRECTION:
                measurementBytes.setDirectionsBinary(ByteString.copyFrom(bytes));
                break;
            default:
                throw new IllegalArgumentException("Unknown type: " + type);
        }

        final MeasurementBytes data = measurementBytes.build();
        return parseFrom(data.toByteArray());
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
     *         includes {@link Measurement}s, {@link ParcelableGeoLocation}s and {@link Event}s.
     *         The later includes the {@link Point3DFile}s.
     */
    public static int clearPersistenceLayer(@NonNull final Context context, @NonNull final ContentResolver resolver,
            @NonNull final String authority) {

        final FileAccessLayer fileAccessLayer = new DefaultFileAccess();

        // Remove {@code Point3DFile}s and their parent folders
        int removedFiles = 0;

        final File accelerationFolder = fileAccessLayer.getFolderPath(context, Point3DFile.ACCELERATIONS_FOLDER_NAME);
        if (accelerationFolder.exists()) {
            Validate.isTrue(accelerationFolder.isDirectory());
            final File[] accelerationFiles = accelerationFolder.listFiles();
            if (accelerationFiles != null) {
                for (final File file : accelerationFiles) {
                    Validate.isTrue(file.delete());
                }
                removedFiles += accelerationFiles.length;
            }
            Validate.isTrue(accelerationFolder.delete());
        }

        final File rotationFolder = fileAccessLayer.getFolderPath(context, Point3DFile.ROTATIONS_FOLDER_NAME);
        if (rotationFolder.exists()) {
            Validate.isTrue(rotationFolder.isDirectory());
            final File[] rotationFiles = rotationFolder.listFiles();
            if (rotationFiles != null) {
                for (final File file : rotationFiles) {
                    Validate.isTrue(file.delete());
                }
                removedFiles += rotationFiles.length;
            }
            Validate.isTrue(rotationFolder.delete());
        }

        final File directionFolder = fileAccessLayer.getFolderPath(context, Point3DFile.DIRECTIONS_FOLDER_NAME);
        if (directionFolder.exists()) {
            Validate.isTrue(directionFolder.isDirectory());
            final File[] directionFiles = directionFolder.listFiles();
            if (directionFiles != null) {
                for (final File file : directionFiles) {
                    Validate.isTrue(file.delete());
                }
                removedFiles += directionFiles.length;
            }
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
     * @param point3DCount The number of point3Ds to insert (of each sensor type).
     * @param locationCount The number of location points to insert.
     * @throws NoSuchMeasurementException – if there was no measurement with the id .
     * @throws CursorIsNullException – If ContentProvider was inaccessible
     */
    @NonNull
    public static Measurement insertSampleMeasurementWithData(@NonNull final Context context, final String authority,
            final MeasurementStatus status, @SuppressWarnings("rawtypes") final PersistenceLayer persistence,
            final int point3DCount,
            final int locationCount) throws NoSuchMeasurementException, CursorIsNullException {
        Validate.isTrue(point3DCount > 0);
        Validate.isTrue(locationCount > 0);

        final List<ParcelableGeoLocation> geoLocations = new ArrayList<>();
        Measurement measurement = insertMeasurementEntry(persistence, Modality.UNKNOWN);
        final long measurementIdentifier = measurement.getIdentifier();
        for (int i = 0; i < locationCount; i++) {
            // We add some salt to make sure the compression of the data is realistic
            // This is required as the testOnPerformSyncWithLargeData test requires large data
            final double salt = Math.random();
            geoLocations.add(new ParcelableGeoLocation(49.9304133333333 + salt, 8.82831833333333 + salt, 1503055141000L + i,
                    0.0 + salt, 940 + salt));
        }
        insertGeoLocations(context.getContentResolver(), authority, measurement.getIdentifier(), geoLocations);

        // Insert file base data
        final Point3DFile accelerationsFile = new Point3DFile(context, measurementIdentifier, ACCELERATION);
        final Point3DFile rotationsFile = new Point3DFile(context, measurementIdentifier, ROTATION);
        final Point3DFile directionsFile = new Point3DFile(context, measurementIdentifier, DIRECTION);
        final var aPoints = new ArrayList<Point3DImpl>();
        final var rPoints = new ArrayList<Point3DImpl>();
        final var dPoints = new ArrayList<Point3DImpl>();
        final int createLimit = 100_000;
        int alreadyInserted = 0;
        for (int i = 0; i + alreadyInserted < point3DCount; i++) {
            // We add some salt to make sure the compression of the data is realistic
            // This is required as the testOnPerformSyncWithLargeData test requires large data
            final float salt = (float)Math.random();
            aPoints.add(
                    new Point3DImpl(10.1189575f + salt, -0.15088624f + salt, 0.2921924f + salt, 1501662635973L + i));
            rPoints.add(
                    new Point3DImpl(0.001524045f + salt, 0.0025423833f + salt, -0.0010279021f + salt,
                            1501662635981L + i));
            dPoints.add(new Point3DImpl(7.65f + salt, -32.4f + salt, -71.4f + salt, 1501662636010L + i));

            // Avoid OOM when creating too much test data at once
            if (i >= createLimit - 1) {
                insertPoint3Ds(accelerationsFile, aPoints);
                insertPoint3Ds(rotationsFile, rPoints);
                insertPoint3Ds(directionsFile, dPoints);
                alreadyInserted += aPoints.size();
                aPoints.clear();
                rPoints.clear();
                dPoints.clear();
                i = -1; // because "i" is incremented just after this statement (end of loop iteration)
            }
        }
        insertPoint3Ds(accelerationsFile, aPoints);
        insertPoint3Ds(rotationsFile, rPoints);
        insertPoint3Ds(directionsFile, dPoints);

        if (status == FINISHED || status == MeasurementStatus.SYNCED || status == SKIPPED) {
            persistence.storePersistenceFileFormatVersion(PERSISTENCE_FILE_FORMAT_VERSION,
                    measurementIdentifier);
            persistence.setStatus(measurementIdentifier, FINISHED, false);
        }
        if (status == SYNCED) {
            persistence.markFinishedAs(SYNCED, measurement.getIdentifier());
        } else if (status == SKIPPED) {
            persistence.markFinishedAs(SKIPPED, measurement.getIdentifier());
        }

        // Check the measurement entry
        final Measurement loadedMeasurement = persistence.loadMeasurement(measurementIdentifier);
        assertThat(loadedMeasurement, notNullValue());
        assertThat(persistence.loadMeasurementStatus(measurementIdentifier), is(equalTo(status)));
        assertThat(loadedMeasurement.getFileFormatVersion(), is(equalTo(PERSISTENCE_FILE_FORMAT_VERSION)));

        // Check the Tracks
        // noinspection unchecked
        final List<Track> loadedTracks = persistence.loadTracks(measurementIdentifier);
        assertThat(loadedTracks.get(0).getGeoLocations().size(), is(locationCount));

        return measurement;
    }

    /**
     * Inserts a test {@code Measurement} into the database content provider accessed by the test. To add data to the
     * {@code Measurement} use some or all of
     * {@link #insertGeoLocation(ContentResolver, String, long, long, double, double, double, double)}
     * {@link SharedTestUtils#insertPoint3D(Point3DFile, long, double, double, double)} (Context, long, long, double,
     * double, double)},
     *
     * @param modality The {@link Modality} type of the {@code Measurement}. A common value is {@link Modality#UNKNOWN}
     *            if
     *            you do not care.
     * @return The database identifier of the created {@link Measurement}.
     */
    @SuppressWarnings({"WeakerAccess", "RedundantSuppression"}) // Used by the cyface flavour tests
    @NonNull
    public static Measurement insertMeasurementEntry(
            @SuppressWarnings("rawtypes") @NonNull final PersistenceLayer persistence,
            @NonNull final Modality modality) throws CursorIsNullException {

        // usually called in DataCapturingService#Constructor
        persistence.restoreOrCreateDeviceId();

        return persistence.newMeasurement(modality);
    }

    /**
     * Inserts a test {@link ParcelableGeoLocation} into the database content provider accessed by the test.
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
    // Used by the cyface flavour tests
    @SuppressWarnings({"WeakerAccess", "unused", "RedundantSuppression"})
    public static void insertGeoLocation(final ContentResolver resolver, final String authority,
            final long measurementIdentifier, final long timestamp, final double lat, final double lon,
            final double speed, final double accuracy) {

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
     * Inserts test {@link ParcelableGeoLocation}s into the database content provider accessed by the test.
     * <p>
     * This increases the performance of large tests and avoids "failed binder transaction - parcel size ..." error.
     *
     * @param resolver The {@link ContentResolver} required to access the database
     * @param authority The authority string required to access the database
     * @param measurementIdentifier The identifier of the test {@link Measurement}.
     * @param geoLocations Test fake {@code GeoLocation}s to add.
     */
    private static void insertGeoLocations(final ContentResolver resolver, final String authority,
            final long measurementIdentifier, final List<ParcelableGeoLocation> geoLocations) {
        final List<ContentValues> valuesList = new ArrayList<>();
        for (final ParcelableGeoLocation geoLocation : geoLocations) {
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