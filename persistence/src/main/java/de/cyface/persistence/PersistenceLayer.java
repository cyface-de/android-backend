/*
 * Copyright 2017-2023 Cyface GmbH
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
package de.cyface.persistence;

import static android.provider.BaseColumns._ID;
import static de.cyface.persistence.Constants.TAG;
import static de.cyface.persistence.MeasurementTable.COLUMN_DISTANCE;
import static de.cyface.persistence.MeasurementTable.COLUMN_MODALITY;
import static de.cyface.persistence.MeasurementTable.COLUMN_PERSISTENCE_FILE_FORMAT_VERSION;
import static de.cyface.persistence.MeasurementTable.COLUMN_STATUS;
import static de.cyface.persistence.MeasurementTable.COLUMN_TIMESTAMP;
import static de.cyface.persistence.model.Event.EventType.LIFECYCLE_PAUSE;
import static de.cyface.persistence.model.Event.EventType.LIFECYCLE_RESUME;
import static de.cyface.persistence.model.Event.EventType.LIFECYCLE_START;
import static de.cyface.persistence.model.Event.EventType.LIFECYCLE_STOP;
import static de.cyface.persistence.model.MeasurementStatus.FINISHED;
import static de.cyface.persistence.model.MeasurementStatus.OPEN;
import static de.cyface.persistence.model.MeasurementStatus.SYNCED;
import static de.cyface.utils.CursorIsNullException.softCatchNullCursor;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.cyface.persistence.model.Event;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.GeoLocationV6;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.Modality;
import de.cyface.persistence.model.Point3d;
import de.cyface.persistence.model.Track;
import de.cyface.persistence.serialization.MeasurementSerializer;
import de.cyface.persistence.serialization.NoSuchFileException;
import de.cyface.persistence.serialization.Point3dFile;
import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;

/**
 * This class wraps the Cyface Android persistence API as required by the {@code DataCapturingListener} and its delegate
 * objects.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 17.0.3
 * @since 2.0.0
 */
public class PersistenceLayer<B extends PersistenceBehaviour> {

    /**
     * The {@link Context} required to locate the app's internal storage directory.
     */
    private final Context context;
    /**
     * <code>ContentResolver</code> that provides access to the {@link MeasuringPointsContentProvider}.
     */
    private final ContentResolver resolver;
    /**
     * The authority used to identify the Android content provider to persist data to or load it from.
     */
    private final String authority;
    /**
     * The authority used to identify the Android content provider to persist V6 data to or load it from.
     */
    private final String authorityV6;
    /**
     * The {@link PersistenceBehaviour} defines how the {@code Persistence} layer is works. We need this behaviour to
     * differentiate if the {@link PersistenceLayer} is used for live capturing and or to load existing data.
     */
    private B persistenceBehaviour;
    /**
     * The {@link FileAccessLayer} used to interact with files.
     */
    private FileAccessLayer fileAccessLayer;

    /**
     * <b>This constructor is only for testing.</b>
     * <p>
     * It's required by the {@code DataCapturingLocalTest} to be able to {@link @Spy} on this object.
     */
    public PersistenceLayer() {
        this.context = null;
        this.resolver = null;
        this.authority = null;
        this.authorityV6 = null;
        this.fileAccessLayer = new DefaultFileAccess();
    }

    /**
     * Creates a new completely initialized <code>PersistenceLayer</code>.
     *
     * @param context The {@link Context} required to locate the app's internal storage directory.
     * @param resolver {@link ContentResolver} that provides access to the {@link MeasuringPointsContentProvider}. This
     *            is required as an explicit parameter to allow test to inject a mocked {@code ContentResolver}.
     * @param authority The authority used to identify the Android content provider.
     * @param authorityV6 The authority used to identify the Android content provider V6.
     * @param persistenceBehaviour A {@link PersistenceBehaviour} which tells if this {@link PersistenceLayer} is used
     *            to capture live data.
     */
    public PersistenceLayer(@NonNull final Context context, @NonNull final ContentResolver resolver,
            @NonNull final String authority, @NonNull final String authorityV6, @NonNull final B persistenceBehaviour) {
        this.context = context;
        this.resolver = resolver;
        this.authority = authority;
        this.authorityV6 = authorityV6;
        this.persistenceBehaviour = persistenceBehaviour;
        this.fileAccessLayer = new DefaultFileAccess();
        final File accelerationsFolder = fileAccessLayer.getFolderPath(context, Point3dFile.ACCELERATIONS_FOLDER_NAME);
        final File rotationsFolder = fileAccessLayer.getFolderPath(context, Point3dFile.ROTATIONS_FOLDER_NAME);
        final File directionsFolder = fileAccessLayer.getFolderPath(context, Point3dFile.DIRECTIONS_FOLDER_NAME);
        checkOrCreateFolder(accelerationsFolder);
        checkOrCreateFolder(rotationsFolder);
        checkOrCreateFolder(directionsFolder);
        persistenceBehaviour.onStart(this);
    }

    /**
     * Ensures that the specified exists.
     *
     * @param folder The {@link File} pointer to the folder which is created if it does not yet exist
     */
    private void checkOrCreateFolder(@NonNull final File folder) {
        if (!folder.exists()) {
            Validate.isTrue(folder.mkdir());
        }
    }

    /**
     * Creates a new, {@link MeasurementStatus#OPEN} {@link Measurement} for the provided {@link Modality}.
     * <p>
     * <b>ATTENTION:</b> This method should not be called from outside the SDK.
     *
     * @param modality The {@code Modality} to create a new {@code Measurement} for.
     * @return The newly created {@code Measurement}.
     */
    public Measurement newMeasurement(@NonNull final Modality modality) {

        final long timestamp = System.currentTimeMillis();

        final ContentValues measurementValues = new ContentValues();
        measurementValues.put(COLUMN_MODALITY, modality.getDatabaseIdentifier());
        measurementValues.put(COLUMN_STATUS, MeasurementStatus.OPEN.getDatabaseIdentifier());
        measurementValues.put(COLUMN_PERSISTENCE_FILE_FORMAT_VERSION,
                MeasurementSerializer.PERSISTENCE_FILE_FORMAT_VERSION);
        measurementValues.put(COLUMN_DISTANCE, 0.0);
        measurementValues.put(COLUMN_TIMESTAMP, timestamp);

        // Synchronized to make sure there can't be two measurements with the same id
        synchronized (this) {
            Uri resultUri = resolver.insert(getMeasurementUri(), measurementValues);
            Validate.notNull("New measurement could not be created!", resultUri);
            Validate.notNull(resultUri.getLastPathSegment());

            final long measurementId = Long.parseLong(resultUri.getLastPathSegment());
            persistenceBehaviour.onNewMeasurement(measurementId);
            return new Measurement(measurementId, OPEN, modality, MeasurementSerializer.PERSISTENCE_FILE_FORMAT_VERSION,
                    0.0, timestamp);
        }
    }

    /**
     * Provides information about whether there is currently a {@link Measurement} in the specified
     * {@link MeasurementStatus}.
     *
     * @param status The {@code MeasurementStatus} in question
     * @return <code>true</code> if a {@code Measurement} of the {@param status} exists.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    public boolean hasMeasurement(@NonNull MeasurementStatus status) throws CursorIsNullException {
        Log.v(TAG, "Checking if app has an " + status + " measurement.");

        Cursor cursor = null;
        try {
            synchronized (this) {
                cursor = resolver.query(getMeasurementUri(), null, COLUMN_STATUS + "=?",
                        new String[] {status.getDatabaseIdentifier()}, null);
                softCatchNullCursor(cursor);

                final boolean hasMeasurement = cursor.getCount() > 0;
                Log.v(TAG, hasMeasurement ? "At least one measurement is " + status + "."
                        : "No measurement is " + status + ".");
                return hasMeasurement;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Returns all {@link Measurement}s, no matter the current {@link MeasurementStatus}.
     * If you only want measurements of a specific {@link MeasurementStatus} call
     * {@link #loadMeasurements(MeasurementStatus)} instead.
     *
     * @return A list containing all {@code Measurement}s currently stored on this device by this application. An empty
     *         list if there are no such measurements, but never <code>null</code>.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @SuppressWarnings({"unused"}) // Used by cyface flavour tests and possibly by implementing apps
    public @NonNull List<Measurement> loadMeasurements() throws CursorIsNullException {
        Cursor cursor = null;
        try {
            List<Measurement> ret = new ArrayList<>();
            cursor = resolver.query(getMeasurementUri(), null, null, null, null);
            softCatchNullCursor(cursor);

            while (cursor.moveToNext()) {
                final Measurement measurement = loadMeasurement(cursor);
                ret.add(measurement);
            }

            return ret;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Loads a {@link Measurement} object from a {@link Cursor} which points to a {@code Measurement}.
     *
     * @param cursor a {@code Cursor} which points to a {@code Measurement}
     * @return the {@code Measurement} of the {@code Cursor}
     */
    private Measurement loadMeasurement(@NonNull final Cursor cursor) {
        final long measurementIdentifier = cursor.getLong(cursor.getColumnIndex(_ID));
        final MeasurementStatus status = MeasurementStatus
                .valueOf(cursor.getString(cursor.getColumnIndex(COLUMN_STATUS)));
        final Modality modality = Modality.valueOf(cursor.getString(cursor.getColumnIndex(COLUMN_MODALITY)));
        final short fileFormatVersion = cursor.getShort(cursor.getColumnIndex(COLUMN_PERSISTENCE_FILE_FORMAT_VERSION));
        final double distance = cursor.getDouble(cursor.getColumnIndex(COLUMN_DISTANCE));
        final long timestamp = cursor.getLong(cursor.getColumnIndex(COLUMN_TIMESTAMP));
        return new Measurement(measurementIdentifier, status, modality, fileFormatVersion, distance, timestamp);
    }

    /**
     * Loads an {@link Event} object from a {@link Cursor}.
     *
     * @param cursor a {@code Cursor} which points to a {@code Event}
     * @return the {@code Event} of the {@code Cursor}
     */
    private Event loadEvent(@NonNull final Cursor cursor) {
        final long id = cursor.getLong(cursor.getColumnIndex(_ID));
        final long timestamp = cursor.getLong(cursor.getColumnIndex(EventTable.COLUMN_TIMESTAMP));
        final Event.EventType eventType = Event.EventType
                .valueOf(cursor.getString(cursor.getColumnIndex(EventTable.COLUMN_TYPE)));
        final String value = cursor.getString(cursor.getColumnIndex(EventTable.COLUMN_VALUE));
        return new Event(id, eventType, timestamp, value);
    }

    /**
     * Provide one specific {@link Measurement} from the data storage if it exists.
     *
     * Attention: At the loaded {@code Measurement} object and the persistent version of it in the
     * {@link PersistenceLayer} are not directly connected the loaded object is not notified when
     * the it's counterpart in the {@code PersistenceLayer} is changed (e.g. the {@link MeasurementStatus}).
     *
     * @param measurementIdentifier The device wide unique identifier of the {@code Measurement} to load.
     * @return The loaded {@code Measurement} if it exists; <code>null</code> otherwise.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @SuppressWarnings("unused") // Sdk implementing apps (SR) use this to load single measurements
    public Measurement loadMeasurement(final long measurementIdentifier) throws CursorIsNullException {
        final Uri measurementUri = getMeasurementUri().buildUpon().appendPath(Long.toString(measurementIdentifier))
                .build();

        try (final Cursor cursor = resolver.query(measurementUri, null, _ID + "=?",
                new String[] {String.valueOf(measurementIdentifier)}, null)) {
            softCatchNullCursor(cursor);
            if (cursor.getCount() > 1) {
                throw new IllegalStateException("Too many measurements loaded from URI: " + measurementUri);
            }

            if (cursor.moveToFirst()) {
                return loadMeasurement(cursor);
            } else {
                return null;
            }
        }
    }

    /**
     * Provide one specific {@link Event} from the data storage if it exists.
     *
     * Attention: At the loaded {@code Event} object and the persistent version of it in the
     * {@link PersistenceLayer} are not directly connected the loaded object is not notified when
     * the it's counterpart in the {@code PersistenceLayer} is changed (e.g. the {@code Event.EventType}).
     *
     * @param eventId The device wide unique identifier of the {@code Event} to load.
     * @return The loaded {@code Event} if it exists; <code>null</code> otherwise.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @SuppressWarnings("unused") // Sdk implementing apps (CY)
    public Event loadEvent(final long eventId) throws CursorIsNullException {
        final Uri eventUri = getEventUri().buildUpon().appendPath(Long.toString(eventId)).build();

        try (final Cursor cursor = resolver.query(eventUri, null, _ID + "=?",
                new String[] {String.valueOf(eventId)}, null)) {
            softCatchNullCursor(cursor);
            if (cursor.getCount() > 1) {
                throw new IllegalStateException("Too many Events loaded from URI: " + eventUri);
            }

            if (cursor.moveToFirst()) {
                return loadEvent(cursor);
            } else {
                return null;
            }
        }
    }

    /**
     * Provide the {@link MeasurementStatus} of one specific {@link Measurement} from the data storage.
     * <p>
     * <b>ATTENTION:</b> Please be aware that the returned status is only valid at the time this
     * method is called. Changes of the {@code MeasurementStatus} in the persistence layer are not pushed
     * to the {@code MeasurementStatus} returned by this method.
     *
     * @param measurementIdentifier The device wide unique identifier of the measurement to load.
     * @return The loaded {@code MeasurementStatus}
     * @throws NoSuchMeasurementException If the {@link Measurement} does not exist.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    public MeasurementStatus loadMeasurementStatus(final long measurementIdentifier)
            throws NoSuchMeasurementException, CursorIsNullException {
        Uri measurementUri = getMeasurementUri().buildUpon().appendPath(Long.toString(measurementIdentifier)).build();

        try (final Cursor cursor = resolver.query(measurementUri, null, null, null, null)) {
            softCatchNullCursor(cursor);
            if (cursor.getCount() > 1) {
                throw new IllegalStateException("Too many measurements loaded from URI: " + measurementUri);
            }
            if (!cursor.moveToFirst()) {
                throw new NoSuchMeasurementException("Failed to load MeasurementStatus.");
            }

            return MeasurementStatus.valueOf(cursor.getString(cursor.getColumnIndex(COLUMN_STATUS)));
        }
    }

    /**
     * Loads all {@link Measurement} which are in a specific {@link MeasurementStatus} from the data
     * storage.
     *
     * @param status the {@code MeasurementStatus} for which all {@code Measurement}s are to be loaded
     * @return All the {code Measurement}s in the specified {@param state}. An empty list if there are no
     *         such measurements, but never <code>null</code>.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @SuppressWarnings("unused") // Implementing apps (SR) use this api to load the finished measurements
    public List<Measurement> loadMeasurements(@NonNull final MeasurementStatus status) throws CursorIsNullException {
        Cursor cursor = null;

        try {
            final List<Measurement> measurements = new ArrayList<>();
            cursor = resolver.query(getMeasurementUri(), null, COLUMN_STATUS + "=?",
                    new String[] {status.getDatabaseIdentifier()}, null);
            softCatchNullCursor(cursor);

            while (cursor.moveToNext()) {
                final Measurement measurement = loadMeasurement(cursor);
                measurements.add(measurement);
            }

            return measurements;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Marks a {@link MeasurementStatus#FINISHED} {@link Measurement} as {@link MeasurementStatus#SYNCED} and deletes
     * the sensor data.
     * <p>
     * <b>ATTENTION:</b> This method should not be called from outside the SDK.
     *
     * @param measurement The {@link Measurement} to remove.
     * @throws NoSuchMeasurementException If the {@link Measurement} does not exist.
     */
    public void markAsSynchronized(final Measurement measurement)
            throws NoSuchMeasurementException, CursorIsNullException {

        // The status in the database could be different from the one in the object so load it again
        final long measurementId = measurement.getIdentifier();
        Validate.isTrue(loadMeasurementStatus(measurementId) == FINISHED);
        setStatus(measurementId, SYNCED, false);

        // TODO [CY-4359]: implement cyface variant where not only sensor data but also GeoLocations are deleted

        try {
            final File accelerationFile = Point3dFile.loadFile(context, fileAccessLayer, measurementId,
                    Point3dFile.ACCELERATIONS_FOLDER_NAME, Point3dFile.ACCELERATIONS_FILE_EXTENSION).getFile();
            Validate.isTrue(accelerationFile.delete());
        } catch (final NoSuchFileException e) {
            Log.v(TAG, "markAsSynchronized: No acceleration file found to delete, nothing to do");
        }

        try {
            final File rotationFile = Point3dFile.loadFile(context, fileAccessLayer, measurementId,
                    Point3dFile.ROTATIONS_FOLDER_NAME, Point3dFile.ROTATION_FILE_EXTENSION).getFile();
            Validate.isTrue(rotationFile.delete());
        } catch (final NoSuchFileException e) {
            Log.v(TAG, "markAsSynchronized: No rotation file found to delete, nothing to do");
        }

        try {
            final File directionFile = Point3dFile.loadFile(context, fileAccessLayer, measurementId,
                    Point3dFile.DIRECTIONS_FOLDER_NAME, Point3dFile.DIRECTION_FILE_EXTENSION).getFile();
            Validate.isTrue(directionFile.delete());
        } catch (final NoSuchFileException e) {
            Log.v(TAG, "markAsSynchronized: No direction file found to delete, nothing to do");
        }
    }

    /**
     * We want to make sure the device id is stored at the same location as the next measurement id counter.
     * This way we ensure ether both or none of both is reset upon re-installation or app reset.
     * <p>
     * <b>ATTENTION:</b> This method should not be called from outside the SDK. Use
     * {@code DataCapturingService#getDeviceIdentifier()} instead.
     *
     * @return The device is as string
     */
    @NonNull
    public final String restoreOrCreateDeviceId() throws CursorIsNullException {
        try {
            return loadDeviceId();
        } catch (final NoDeviceIdException e) {
            // Create a new device id
            final String deviceId = UUID.randomUUID().toString();
            final ContentValues identifierValues = new ContentValues();
            identifierValues.put(IdentifierTable.COLUMN_DEVICE_ID, deviceId);
            final Uri resultUri = resolver.insert(getIdentifierUri(), identifierValues);
            Validate.notNull("New device id could not be created!", resultUri);
            // Show info in log so that we see if this happens more than once (or on app data reset)
            Log.i(TAG, "Created new device id: " + deviceId);
            return deviceId;
        }
    }

    /**
     * Loads the device identifier from the persistence layer.
     * <p>
     * <b>ATTENTION:</b> This method should not be called from outside the SDK. Use
     * {@code DataCapturingService#getDeviceIdentifier()} instead.
     *
     * @return The device is as string
     * @throws CursorIsNullException when accessing the {@link ContentProvider} failed
     * @throws NoDeviceIdException when there are no entries in the {@link IdentifierTable}
     */
    @NonNull
    private String loadDeviceId() throws CursorIsNullException, NoDeviceIdException {
        Cursor deviceIdentifierQueryCursor = null;
        try {
            synchronized (this) {
                // Try to get device id from database
                deviceIdentifierQueryCursor = resolver.query(getIdentifierUri(),
                        new String[] {IdentifierTable.COLUMN_DEVICE_ID}, null, null, null);
                softCatchNullCursor(deviceIdentifierQueryCursor);
                if (deviceIdentifierQueryCursor.getCount() > 1) {
                    throw new IllegalStateException("More entries than expected");
                }
                if (deviceIdentifierQueryCursor.moveToFirst()) {
                    final int indexOfMeasurementIdentifierColumn = deviceIdentifierQueryCursor
                            .getColumnIndex(IdentifierTable.COLUMN_DEVICE_ID);
                    final String did = deviceIdentifierQueryCursor.getString(indexOfMeasurementIdentifierColumn);
                    Log.v(TAG, "Providing device identifier " + did);
                    Validate.notNull(did);
                    return did;
                }

                throw new NoDeviceIdException("No entries in IdentifierTable.");
            }
        } finally {
            // This can be null, see documentation
            if (deviceIdentifierQueryCursor != null) {
                deviceIdentifierQueryCursor.close();
            }
        }
    }

    /**
     * Removes one {@link Measurement} from the local persistent data storage.
     *
     * @param measurementIdentifier The id of the {@code Measurement} to remove.
     */
    @SuppressWarnings("unused") // Sdk implementing apps (SR) use this to delete measurements
    public void delete(final long measurementIdentifier) {

        deletePoint3dData(measurementIdentifier);

        // Delete {@link GeoLocation}s, {@link Event}s and {@link Measurement} entry from database
        resolver.delete(getGeoLocationsUri(), GeoLocationsTable.COLUMN_MEASUREMENT_FK + "=?",
                new String[] {Long.valueOf(measurementIdentifier).toString()});
        resolver.delete(getGeoLocationsV6Uri(), GeoLocationsTableV6.COLUMN_MEASUREMENT_FK + "=?",
                new String[] {Long.valueOf(measurementIdentifier).toString()});
        resolver.delete(getEventUri(), EventTable.COLUMN_MEASUREMENT_FK + "=?",
                new String[] {Long.valueOf(measurementIdentifier).toString()});
        resolver.delete(getPressuresUri(), PressuresTable.COLUMN_MEASUREMENT_FK + "=?",
                new String[] {Long.valueOf(measurementIdentifier).toString()});
        resolver.delete(getMeasurementUri(), _ID + "=?", new String[] {Long.valueOf(measurementIdentifier).toString()});
    }

    /**
     * Removes one {@link Event} from the local persistent data storage.
     *
     * @param eventId The id of the {@code Event} to remove.
     */
    @SuppressWarnings("unused") // Sdk implementing apps (CY) use this
    public void deleteEvent(final long eventId) {
        resolver.delete(getEventUri(), _ID + "=?", new String[] {Long.valueOf(eventId).toString()});
    }

    /**
     * Removes the {@link Point3d}s for one {@link Measurement} from the local persistent data storage.
     *
     * @param measurementIdentifier The {@code Measurement} id of the data to remove.
     */
    private void deletePoint3dData(final long measurementIdentifier) {
        final File accelerationFolder = fileAccessLayer.getFolderPath(context, Point3dFile.ACCELERATIONS_FOLDER_NAME);
        final File rotationFolder = fileAccessLayer.getFolderPath(context, Point3dFile.ROTATIONS_FOLDER_NAME);
        final File directionFolder = fileAccessLayer.getFolderPath(context, Point3dFile.DIRECTIONS_FOLDER_NAME);

        if (accelerationFolder.exists()) {
            final File accelerationFile = fileAccessLayer.getFilePath(context, measurementIdentifier,
                    Point3dFile.ACCELERATIONS_FOLDER_NAME, Point3dFile.ACCELERATIONS_FILE_EXTENSION);
            if (accelerationFile.exists()) {
                Validate.isTrue(accelerationFile.delete());
            }
        }
        if (rotationFolder.exists()) {
            final File rotationFile = fileAccessLayer.getFilePath(context, measurementIdentifier,
                    Point3dFile.ROTATIONS_FOLDER_NAME, Point3dFile.ROTATION_FILE_EXTENSION);
            if (rotationFile.exists()) {
                Validate.isTrue(rotationFile.delete());
            }
        }
        if (directionFolder.exists()) {
            final File directionFile = fileAccessLayer.getFilePath(context, measurementIdentifier,
                    Point3dFile.DIRECTIONS_FOLDER_NAME, Point3dFile.DIRECTION_FILE_EXTENSION);
            if (directionFile.exists()) {
                Validate.isTrue(directionFile.delete());
            }
        }
    }

    /**
     * Returns the average speed of the measurement with the provided measurement identifier.
     * <p>
     * Loads the {@link Track}s from the database to calculate the metric on the fly [STAD-384].
     *
     * @param measurementIdentifier The id of the {@code Measurement} to load the track for.
     * @param locationCleaningStrategy The {@link LocationCleaningStrategy} used to filter the
     *            {@link GeoLocation}s
     * @return The average speed in meters per second.
     * @throws CursorIsNullException when accessing the {@code ContentProvider} failed
     */
    public double loadAverageSpeed(final long measurementIdentifier,
            @NonNull final LocationCleaningStrategy locationCleaningStrategy) throws CursorIsNullException {

        final List<Track> tracks = loadTracks(measurementIdentifier);
        double speedSum = 0.0;
        int speedCounter = 0;
        for (final Track track : tracks) {
            double sum = 0.0;
            int counter = 0;
            for (final GeoLocation location : track.getGeoLocations()) {
                if (locationCleaningStrategy.isClean(location)) {
                    sum += location.getSpeed();
                    counter += 1;
                }
            }
            speedSum += sum;
            speedCounter += counter;
        }
        return speedCounter > 0 ? speedSum / (double)speedCounter : 0.0;
    }

    /**
     * Returns the duration of the measurement with the provided measurement identifier without the time between pause
     * and resume. [STAD-367]
     * <p>
     * Loads the {@link Event}s from the database to remove the time between pause and resume.
     *
     * @param measurementIdentifier The id of the {@code Measurement} to load the track for.
     * @return The average speed in meters per second.
     * @throws CursorIsNullException when accessing the {@code ContentProvider} failed
     * @throws NoSuchMeasurementException If the {@link Measurement} does not exist.
     */
    public long loadDuration(final long measurementIdentifier)
            throws CursorIsNullException, NoSuchMeasurementException {

        // Extract lifecycle events only
        final List<Event> lifecycleEvents = new ArrayList<>();
        for (final Event event : loadEvents(measurementIdentifier)) {
            final Event.EventType type = event.getType();
            if (type.equals(LIFECYCLE_START) || type.equals(LIFECYCLE_PAUSE) || type.equals(LIFECYCLE_RESUME)
                    || type.equals(LIFECYCLE_STOP)) {
                lifecycleEvents.add(event);
            }
        }

        // Add duration for each lifecycle event pair which fits:
        // - START-STOP, START-PAUSE, RESUME-PAUSE, RESUME-STOP
        // - for ongoing measurements when the last event is START or RESUME (for live duration)
        long duration = 0L;
        Event previousEvent = null;
        for (int i = 0; i < lifecycleEvents.size(); i++) {
            final Event event = lifecycleEvents.get(i);
            final boolean isLast = i == lifecycleEvents.size() - 1;
            final boolean isOngoing = loadMeasurementStatus(measurementIdentifier).equals(OPEN);
            if (isLast && isOngoing && (event.getType() == LIFECYCLE_START || event.getType() == LIFECYCLE_RESUME)) {
                final long newDuration = System.currentTimeMillis() - event.getTimestamp();
                Validate.isTrue(newDuration >= 0, "Invalid duration: " + newDuration);
                duration += newDuration;
            } else if (previousEvent != null) {
                final Event.EventType previousType = previousEvent.getType();
                final Event.EventType type = event.getType();
                final boolean startStop = previousType == LIFECYCLE_START && type == LIFECYCLE_STOP;
                final boolean startPause = previousType == LIFECYCLE_START && type == LIFECYCLE_PAUSE;
                final boolean resumePause = previousType == LIFECYCLE_RESUME && type == LIFECYCLE_PAUSE;
                final boolean resumeStop = previousType == LIFECYCLE_RESUME && type == LIFECYCLE_STOP;
                if (startStop || startPause || resumePause || resumeStop) {
                    final long newDuration = event.getTimestamp() - previousEvent.getTimestamp();
                    Validate.isTrue(newDuration >= 0, "Invalid duration: " + newDuration);
                    duration += newDuration;
                    Log.e(TAG, "event pair " + previousType + " -> " + event.getType() + ": +" + newDuration);
                }
            }
            previousEvent = event;
        }
        return duration;
    }

    /**
     * Loads the {@link Track}s for the provided {@link Measurement}.
     *
     * TODO [CY-4438]: From the current implementations (MeasurementContentProviderClient loader and resolver.query) is
     * the loader the faster solution. However, we should upgrade the database access as Android changed it's API.
     *
     * @param measurementIdentifier The id of the {@code Measurement} to load the track for.
     * @return The {@link Track}s associated with the {@code Measurement}. If no {@code GeoLocation}s exists, an empty
     *         list is returned.
     * @throws CursorIsNullException when accessing the {@code ContentProvider} failed
     */
    @SuppressWarnings("unused") // May be used by SDK implementing app
    public List<Track> loadTracks(final long measurementIdentifier) throws CursorIsNullException {

        Cursor geoLocationCursor = null;
        Cursor eventCursor = null;
        try {
            eventCursor = loadEventsCursor(measurementIdentifier);
            softCatchNullCursor(eventCursor);

            // Load GeoLocations
            geoLocationCursor = resolver.query(getGeoLocationsUri(), null,
                    GeoLocationsTable.COLUMN_MEASUREMENT_FK + "=?",
                    new String[] {Long.valueOf(measurementIdentifier).toString()},
                    GeoLocationsTable.COLUMN_GEOLOCATION_TIME + " ASC");
            softCatchNullCursor(geoLocationCursor);
            if (geoLocationCursor.getCount() == 0) {
                return Collections.emptyList();
            }
            return loadTracks(geoLocationCursor, eventCursor);
        } finally {
            if (geoLocationCursor != null) {
                geoLocationCursor.close();
            }
            if (eventCursor != null) {
                eventCursor.close();
            }
        }
    }

    /**
     * Loads the "cleaned" {@link Track}s for the provided {@link Measurement}.
     *
     * @param measurementIdentifier The id of the {@code Measurement} to load the track for.
     * @param locationCleaningStrategy The {@link LocationCleaningStrategy} used to filter the
     *            {@link GeoLocation}s
     * @return The {@link Track}s associated with the {@code Measurement}. If no {@code GeoLocation}s exists, an empty
     *         list is returned.
     * @throws CursorIsNullException when accessing the {@code ContentProvider} failed
     */
    @SuppressWarnings("unused") // Used by SDK implementing apps (SR, CY)
    public List<Track> loadTracks(final long measurementIdentifier,
            @NonNull final LocationCleaningStrategy locationCleaningStrategy) throws CursorIsNullException {

        Cursor geoLocationCursor = null;
        Cursor eventCursor = null;
        try {
            eventCursor = loadEventsCursor(measurementIdentifier);
            softCatchNullCursor(eventCursor);

            geoLocationCursor = locationCleaningStrategy.loadCleanedLocations(resolver, measurementIdentifier,
                    getGeoLocationsUri());
            softCatchNullCursor(geoLocationCursor);
            if (geoLocationCursor.getCount() == 0) {
                return Collections.emptyList();
            }
            return loadTracks(geoLocationCursor, eventCursor);

        } finally {
            if (eventCursor != null) {
                eventCursor.close();
            }
            if (geoLocationCursor != null) {
                geoLocationCursor.close();
            }
        }
    }

    /**
     * Loads the {@link Event}s for the provided {@link Measurement}.
     * <p>
     * <b>Attention: The caller needs to wrap this method call with a try-finally block to ensure the returned
     * {@code Cursor} is always closed after use.</b>
     *
     * @param measurementIdentifier The id of the {@code Measurement} to load the {@code Event}s for.
     * @return The {@code Cursor} pointing to the {@code Event}s of of the {@code Measurement} with the provided
     *         {@param measurementId}.
     */
    @Nullable
    private Cursor loadEventsCursor(final long measurementIdentifier) {

        return resolver.query(getEventUri(), null, EventTable.COLUMN_MEASUREMENT_FK + "=?",
                new String[] {Long.valueOf(measurementIdentifier).toString()},
                EventTable.COLUMN_TIMESTAMP + " ASC");
    }

    /**
     * Loads the {@link Event}s for the provided {@link Measurement}.
     * <p>
     * <b>Attention: The caller needs to wrap this method call with a try-finally block to ensure the returned
     * {@code Cursor} is always closed after use.</b>
     *
     * @param measurementIdentifier The id of the {@code Measurement} to load the {@code Event}s for.
     * @return The {@code Cursor} pointing to the {@code Event}s of the {@code Measurement} with the provided
     *         {@param measurementId}.
     * @throws CursorIsNullException when accessing the {@code ContentProvider} failed
     */
    @SuppressWarnings("unused") // Used by implementing apps (SR) to calculate the time
    @NonNull
    public List<Event> loadEvents(final long measurementIdentifier) throws CursorIsNullException {

        try (final Cursor cursor = loadEventsCursor(measurementIdentifier)) {
            softCatchNullCursor(cursor);

            final List<Event> events = new ArrayList<>();
            while (cursor.moveToNext()) {
                final Event event = loadEvent(cursor);
                events.add(event);
            }
            return events;
        }
    }

    /**
     * Loads all {@link Event}s of a specific {@link Event.EventType} for the provided {@link Measurement} from the data
     * storage.
     *
     * @param measurementId The id of the {@code Measurement} to load the {@code Event}s for.
     * @param eventType the {@code EventType} of which all {@code Event}s are to be loaded
     * @return All the {code Event}s of the {@code Measurement} with the provided {@param measurementId} of the
     *         specified {@param eventType}. An empty list if there are no such Events, but never <code>null</code>.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @SuppressWarnings("unused") // Implementing apps (CY) use this
    public List<Event> loadEvents(final long measurementId, @NonNull final Event.EventType eventType)
            throws CursorIsNullException {
        Cursor cursor = null;

        try {
            final List<Event> events = new ArrayList<>();

            cursor = resolver.query(getEventUri(), null,
                    EventTable.COLUMN_MEASUREMENT_FK + "=? AND " + EventTable.COLUMN_TYPE + "=?",
                    new String[] {Long.valueOf(measurementId).toString(), eventType.getDatabaseIdentifier()},
                    EventTable.COLUMN_TIMESTAMP + " ASC");
            softCatchNullCursor(cursor);

            while (cursor.moveToNext()) {
                final Event event = loadEvent(cursor);
                events.add(event);
            }

            return events;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Loads the {@link Track}s for the provided {@link GeoLocation} cursor sliced using the provided {@link Event}
     * cursor.
     *
     * @param geoLocationCursor The {@code GeoLocation} cursor which points to the locations to be loaded.
     * @param eventCursor The {@code Event} cursor which points to the events annotation the corresponding
     *            {@link Measurement}.
     * @return The {@link Track}s for the corresponding {@code Measurement} loaded.
     */
    @NonNull
    private List<Track> loadTracks(@NonNull final Cursor geoLocationCursor, @NonNull final Cursor eventCursor) {
        final List<Track> tracks = new ArrayList<>();

        // The geoLocationCursor always needs to point to the first GeoLocation of the next sub track
        if (!geoLocationCursor.moveToNext()) {
            return tracks;
        }

        // Slice Tracks before resume events
        Long pauseEventTime = null;
        while (eventCursor.moveToNext() && !geoLocationCursor.isAfterLast()) {
            final Event.EventType eventType = Event.EventType
                    .valueOf(eventCursor.getString(eventCursor.getColumnIndex(EventTable.COLUMN_TYPE)));

            // Search for next resume event and capture it's previous pause event
            if (eventType != Event.EventType.LIFECYCLE_RESUME) {
                if (eventType == Event.EventType.LIFECYCLE_PAUSE) {
                    pauseEventTime = eventCursor.getLong(eventCursor.getColumnIndex(EventTable.COLUMN_TIMESTAMP));
                }
                continue;
            }
            Validate.notNull(pauseEventTime);
            final long resumeEventTime = eventCursor.getLong(eventCursor.getColumnIndex(EventTable.COLUMN_TIMESTAMP));

            // Collect all GeoLocations until the pause event
            final Track track = collectNextSubTrack(geoLocationCursor, pauseEventTime);
            // Add sub-track to track
            if (track.getGeoLocations().size() > 0) {
                tracks.add(track);
            }

            // Pause reached: Move geoLocationCursor to the first location of the next sub-track
            // We do this to ignore locations between pause and resume event (STAD-140)
            moveCursorToFirstAfter(geoLocationCursor, resumeEventTime);
        }

        // Return if there is no tail (sub track ending at LIFECYCLE_STOP instead of LIFECYCLE_PAUSE)
        if (geoLocationCursor.isAfterLast()) {
            return tracks;
        }

        // Collect tail sub track
        // This is ether the track between start[, pause] and stop or resume[, pause] and stop.
        final Track track = new Track();
        do {
            final GeoLocation location = loadGeoLocation(geoLocationCursor);
            if (location != null) {
                track.add(location);
            }

        } while (geoLocationCursor.moveToNext());
        Validate.isTrue(track.getGeoLocations().size() > 0);
        tracks.add(track);
        return tracks;
    }

    /**
     * Collects a sub {@link Track} of a {@code Measurement}.
     *
     * @param geoLocationCursor The {@code Cursor} pointing to the {@code GeoLocation}s. The {@param geoLocationCursor}
     *            must point to the first {@code GeoLocation} of the sub track to be collected.
     * @param pauseEventTime the Unix timestamp of the {@link Event.EventType#LIFECYCLE_PAUSE} which defines the end of
     *            this sub Track.
     * @return The sub {@code Track}. The {@param geoLocationCursor} points to the first {@code GeoLocation} which is
     *         later in time than the {@param pauseEventTime} or to the last GeoLocation if the earlier does not exist.
     */
    private Track collectNextSubTrack(@NonNull final Cursor geoLocationCursor, @NonNull final Long pauseEventTime) {
        final Track track = new Track();

        GeoLocation location = loadGeoLocation(geoLocationCursor);
        while (location != null && location.getTimestamp() <= pauseEventTime) {

            track.add(location);

            // Load next GeoLocation to check it's timestamp in next iteration
            geoLocationCursor.moveToNext();
            location = loadGeoLocation(geoLocationCursor);
        }

        return track;
    }

    /**
     * Moves the {@param geoLocationCursor} to the first GeoLocation starting at {@param resumeEventTime}.
     * <p>
     * If there is no such {@code GeoLocation} then the cursor points to {@link Cursor#isAfterLast()}.
     *
     * @param geoLocationCursor The {@code Cursor} pointing to the {@code GeoLocation}s.
     * @param resumeEventTime the Unix timestamp, e.g. of {@link Event.EventType#LIFECYCLE_RESUME}
     */
    private void moveCursorToFirstAfter(@NonNull final Cursor geoLocationCursor, final long resumeEventTime) {

        @Nullable
        GeoLocation location = loadGeoLocation(geoLocationCursor);
        while (location != null && location.getTimestamp() < resumeEventTime && geoLocationCursor.moveToNext()) {

            // Load next location to check it's timestamp
            location = loadGeoLocation(geoLocationCursor);
        }
    }

    /**
     * Loads the {@link GeoLocation} at the {@code Cursor}'s current position.
     *
     * @param geoLocationCursor the {@code Cursor} pointing to a {@code GeoLocation} in the database.
     * @return the {@code GeoLocation} loaded or {@code Null} if the cursor points to the entry after the last one.
     */
    @Nullable
    private GeoLocation loadGeoLocation(@NonNull final Cursor geoLocationCursor) {
        if (geoLocationCursor.isAfterLast()) {
            return null;
        }

        final double lat = geoLocationCursor.getDouble(geoLocationCursor.getColumnIndex(GeoLocationsTable.COLUMN_LAT));
        final double lon = geoLocationCursor.getDouble(geoLocationCursor.getColumnIndex(GeoLocationsTable.COLUMN_LON));
        final long timestamp = geoLocationCursor
                .getLong(geoLocationCursor.getColumnIndex(GeoLocationsTable.COLUMN_GEOLOCATION_TIME));
        final double speed = geoLocationCursor
                .getDouble(geoLocationCursor.getColumnIndex(GeoLocationsTable.COLUMN_SPEED));
        final float accuracy = geoLocationCursor
                .getFloat(geoLocationCursor.getColumnIndex(GeoLocationsTable.COLUMN_ACCURACY));
        return new GeoLocation(lat, lon, timestamp, speed, accuracy);
    }

    /**
     * Loads the {@link GeoLocationV6} at the {@code Cursor}'s current position.
     *
     * @param geoLocationCursor the {@code Cursor} pointing to a {@code GeoLocationV6} in the database.
     * @return the {@code GeoLocationV6} loaded or {@code Null} if the cursor points to the entry after the last one.
     */
    @Nullable
    private GeoLocationV6 loadGeoLocationV6(@NonNull final Cursor geoLocationCursor) {
        if (geoLocationCursor.isAfterLast()) {
            return null;
        }

        final double lat = geoLocationCursor
                .getDouble(geoLocationCursor.getColumnIndex(GeoLocationsTableV6.COLUMN_LAT));
        final double lon = geoLocationCursor
                .getDouble(geoLocationCursor.getColumnIndex(GeoLocationsTableV6.COLUMN_LON));
        final double altitude = geoLocationCursor
                .getDouble(geoLocationCursor.getColumnIndex(GeoLocationsTableV6.COLUMN_ALTITUDE));
        final long timestamp = geoLocationCursor
                .getLong(geoLocationCursor.getColumnIndex(GeoLocationsTableV6.COLUMN_GEOLOCATION_TIME));
        final double speed = geoLocationCursor
                .getDouble(geoLocationCursor.getColumnIndex(GeoLocationsTableV6.COLUMN_SPEED));
        final float accuracy = geoLocationCursor
                .getFloat(geoLocationCursor.getColumnIndex(GeoLocationsTableV6.COLUMN_ACCURACY));
        final float verticalAccuracy = geoLocationCursor
                .getFloat(geoLocationCursor.getColumnIndex(GeoLocationsTableV6.COLUMN_VERTICAL_ACCURACY));
        return new GeoLocationV6(lat, lon, altitude, timestamp, speed, accuracy, verticalAccuracy);
    }

    /**
     * This method cleans up when the persistence layer is no longer needed by the caller.
     * <p>
     * <b>ATTENTION:</b> This method is called automatically and should not be called from outside the SDK.
     */
    public void shutdown() {
        persistenceBehaviour.shutdown();
    }

    /**
     * @return The content provider {@link Uri} for the {@link MeasurementTable}.
     *         <p>
     *         <b>ATTENTION:</b> This method should not be needed from outside the SDK.
     */
    @SuppressWarnings({"WeakerAccess", "RedundantSuppression"}) // Used by SDK implementing apps
    public Uri getMeasurementUri() {
        return Utils.getMeasurementUri(authority);
    }

    /**
     * @return The content provider {@link Uri} for the {@link GeoLocationsTable}.
     *         <p>
     *         <b>ATTENTION:</b> This method should not be needed from outside the SDK.
     */
    public Uri getGeoLocationsUri() {
        return Utils.getGeoLocationsUri(authority);
    }

    /**
     * @return The content provider {@link Uri} for the {@link GeoLocationsTableV6}.
     *         <p>
     *         <b>ATTENTION:</b> This method should not be needed from outside the SDK.
     */
    public Uri getGeoLocationsV6Uri() {
        return Utils.getGeoLocationsV6Uri(authorityV6);
    }

    /**
     * @return The content provider {@link Uri} for the {@link EventTable}.
     */
    public Uri getEventUri() {
        return Utils.getEventUri(authority);
    }

    /**
     * @return The content provider {@link Uri} for the {@link PressuresTable}.
     */
    public Uri getPressuresUri() {
        return Utils.getPressuresUri(authorityV6);
    }

    /**
     * @return The content provider URI for the {@link IdentifierTable}
     */
    private Uri getIdentifierUri() {
        return Utils.getIdentifierUri(authority);
    }

    /**
     * When pausing or stopping a {@link Measurement} we store the
     * {@link MeasurementSerializer#PERSISTENCE_FILE_FORMAT_VERSION} in the {@link Measurement} to make sure we can
     * deserialize the {@link Point3dFile}s with previous {@code PERSISTENCE_FILE_FORMAT_VERSION}s.
     * <p>
     * <b>ATTENTION:</b> This method should not be called from outside the SDK.
     *
     * @param persistenceFileFormatVersion The {@code MeasurementSerializer#PERSISTENCE_FILE_FORMAT_VERSION} required
     *            for deserialization
     * @param measurementId The id of the measurement to update
     */
    public void storePersistenceFileFormatVersion(final short persistenceFileFormatVersion, final long measurementId) {
        Log.d(TAG, "Storing persistenceFileFormatVersion.");

        final ContentValues contentValues = new ContentValues();
        contentValues.put(COLUMN_PERSISTENCE_FILE_FORMAT_VERSION, persistenceFileFormatVersion);

        final int updatedRows = resolver.update(getMeasurementUri(), contentValues, _ID + "=" + measurementId, null);
        Validate.isTrue(updatedRows == 1);
    }

    /**
     * Loads the currently captured {@link Measurement} from the cache, if possible, or from the
     * {@link PersistenceLayer}.
     *
     * @throws NoSuchMeasurementException If this method has been called while no {@code Measurement} was active. To
     *             avoid this use {@link PersistenceLayer#hasMeasurement(MeasurementStatus)} to check whether there is
     *             an actual {@link MeasurementStatus#OPEN} or {@link MeasurementStatus#PAUSED} measurement.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     * @return the currently captured {@link Measurement}
     */
    @SuppressWarnings("unused") // Implementing apps use this to get the ongoing measurement info
    public Measurement loadCurrentlyCapturedMeasurement() throws NoSuchMeasurementException, CursorIsNullException {
        return persistenceBehaviour.loadCurrentlyCapturedMeasurement();
    }

    /**
     * Loads the currently captured {@link Measurement} explicitly from the {@link PersistenceLayer}.
     * <p>
     * <b>ATTENTION:</b> SDK implementing apps should use {@link #loadCurrentlyCapturedMeasurement()} instead.
     *
     * @throws NoSuchMeasurementException If this method has been called while no {@code Measurement} was active. To
     *             avoid this use {@link PersistenceLayer#hasMeasurement(MeasurementStatus)} to check whether there is
     *             an actual {@link MeasurementStatus#OPEN} or {@link MeasurementStatus#PAUSED} measurement.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     * @return the currently captured {@link Measurement}
     */
    public Measurement loadCurrentlyCapturedMeasurementFromPersistence()
            throws NoSuchMeasurementException, CursorIsNullException {
        Log.v(Constants.TAG, "Trying to load currently captured measurement from PersistenceLayer!");

        final List<Measurement> openMeasurements = loadMeasurements(MeasurementStatus.OPEN);
        final List<Measurement> pausedMeasurements = loadMeasurements(MeasurementStatus.PAUSED);
        if (openMeasurements.size() == 0 && pausedMeasurements.size() == 0) {
            throw new NoSuchMeasurementException("No currently captured measurement found!");
        }
        if (openMeasurements.size() + pausedMeasurements.size() > 1) {
            throw new IllegalStateException("More than one currently captured measurement found!");
        }

        return (openMeasurements.size() == 1 ? openMeasurements : pausedMeasurements).get(0);
    }

    /**
     * Updates the {@link MeasurementStatus} in the data persistence layer.
     * <p>
     * <b>ATTENTION:</b> This should not be used by SDK implementing apps.
     *
     * @param measurementIdentifier The id of the {@link Measurement} to be updated
     * @param newStatus The new {@code MeasurementStatus}
     * @param allowCorruptedState {@code True} if this method is called to clean up corrupted measurements
     *            and, thus, it's possible that there are still unfinished measurements
     *            after updating one unfinished measurement to finished. Default is {@code False}.
     * @throws NoSuchMeasurementException if there was no {@code Measurement} with the id
     *             {@param measurementIdentifier}.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    public void setStatus(final long measurementIdentifier, final MeasurementStatus newStatus,
            final boolean allowCorruptedState)
            throws NoSuchMeasurementException, CursorIsNullException {

        final ContentValues values = new ContentValues();
        values.put(COLUMN_STATUS, newStatus.getDatabaseIdentifier());
        updateMeasurement(measurementIdentifier, values);

        // Make sure the database state **after** the status update is still valid
        switch (newStatus) {
            case OPEN:
                Validate.isTrue(!hasMeasurement(MeasurementStatus.PAUSED));
                break;
            case PAUSED:
                Validate.isTrue(!hasMeasurement(MeasurementStatus.OPEN));
                break;
            case FINISHED:
                // Because of MOV-790 we don't check this when cleaning up corrupted measurement*s*
                if (!allowCorruptedState) {
                    Validate.isTrue(!hasMeasurement(MeasurementStatus.OPEN));
                    Validate.isTrue(!hasMeasurement(MeasurementStatus.PAUSED));
                }
                break;
            case SYNCED:
                break;
            default:
                throw new IllegalArgumentException("Not supported");
        }

        Log.d(TAG, "Set measurement " + measurementIdentifier + " to " + newStatus);
    }

    /**
     * Updates the {@link Measurement#getDistance()} entry of the currently captured {@link Measurement}.
     * <p>
     * <b>ATTENTION:</b> This should not be used by SDK implementing apps.
     *
     * @param measurementIdentifier The id of the {@link Measurement} to be updated
     * @param newDistance The new {@code Measurement#distance} to be stored.
     * @throws NoSuchMeasurementException if there was no {@code Measurement} with the id
     *             {@param measurementIdentifier}.
     */
    public void setDistance(final long measurementIdentifier, final double newDistance)
            throws NoSuchMeasurementException {
        final ContentValues values = new ContentValues();
        values.put(COLUMN_DISTANCE, newDistance);
        updateMeasurement(measurementIdentifier, values);
    }

    /**
     * Updates the {@link Measurement#getDistance()} entry of the currently captured {@link Measurement}.
     *
     * @param measurementIdentifier The id of the {@link Measurement} to be updated
     * @param values The new {@link ContentValues} to be stored.
     * @throws NoSuchMeasurementException if there was no {@code Measurement} with the id
     *             {@param measurementIdentifier}.
     */
    private void updateMeasurement(final long measurementIdentifier, @NonNull final ContentValues values)
            throws NoSuchMeasurementException {

        final int updatedRows = resolver.update(getMeasurementUri(), values, _ID + "=" + measurementIdentifier, null);
        Validate.isTrue(updatedRows < 2, "Duplicate measurement id entries.");
        if (updatedRows == 0) {
            throw new NoSuchMeasurementException("The measurement could not be updated as it does not exist.");
        }
    }

    /**
     * @return the {@link #context}
     */
    public Context getContext() {
        return context;
    }

    /**
     * @return the {@link #resolver}
     */
    public ContentResolver getResolver() {
        return resolver;
    }

    /**
     * <b>ATTENTION:</b> This should not be used by SDK implementing apps.
     *
     * @return the {@link #fileAccessLayer}
     */
    public FileAccessLayer getFileAccessLayer() {
        return fileAccessLayer;
    }

    /**
     * <b>ATTENTION:</b> This should not be used by SDK implementing apps.
     *
     * @return the {@link #persistenceBehaviour}
     */
    public B getPersistenceBehaviour() {
        return persistenceBehaviour;
    }

    /**
     * Stores a new {@link Event} in the {@link PersistenceLayer} which is linked to a {@link Measurement}.
     *
     * @param eventType The {@link Event.EventType} to be logged.
     * @param measurement The {@code Measurement} which is linked to the {@code Event}.
     */
    public void logEvent(@NonNull final Event.EventType eventType, @NonNull final Measurement measurement) {
        logEvent(eventType, measurement, System.currentTimeMillis());
    }

    /**
     * Stores a new {@link Event} in the {@link PersistenceLayer} which is linked to a {@link Measurement}.
     * <p>
     * This interface allows tests to define the timestamp to be used for this event log entry.
     *
     * @param eventType The {@link Event.EventType} to be logged.
     * @param measurement The {@code Measurement} which is linked to the {@code Event}.
     * @param timestamp The timestamp in ms at which the event was triggered
     */
    public void logEvent(@NonNull final Event.EventType eventType, @NonNull final Measurement measurement,
            final long timestamp) {
        logEvent(eventType, measurement, timestamp, null);
    }

    /**
     * Stores a new {@link Event} in the {@link PersistenceLayer} which is linked to a {@link Measurement}.
     *
     * @param eventType The {@link Event.EventType} to be logged.
     * @param measurement The {@code Measurement} which is linked to the {@code Event}.
     * @param timestamp The timestamp in ms at which the event was triggered
     * @param value The (optional) {@link Event#getValue()}
     * @return The id of the added {@code Event}
     */
    public long logEvent(@NonNull final Event.EventType eventType, @NonNull final Measurement measurement,
            final long timestamp, @Nullable final String value) {
        Log.v(TAG,
                "Storing Event:" + eventType + (value == null ? "" : " (" + value + ")") + " for Measurement "
                        + measurement.getIdentifier() + " at " + timestamp);

        final ContentValues contentValues = new ContentValues();
        contentValues.put(EventTable.COLUMN_TYPE, eventType.getDatabaseIdentifier());
        contentValues.put(EventTable.COLUMN_TIMESTAMP, timestamp);
        contentValues.put(EventTable.COLUMN_MEASUREMENT_FK, measurement.getIdentifier());
        if (value != null) {
            Validate.isTrue(!value.isEmpty());
            contentValues.put(EventTable.COLUMN_VALUE, value);
        }

        final Uri resultUri = resolver.insert(getEventUri(), contentValues);
        Validate.notNull("New Event could not be created!", resultUri);
        Validate.notNull(resultUri.getLastPathSegment());

        return Long.parseLong(resultUri.getLastPathSegment());
    }

    /**
     * Returns the directory used to store temporary files such as the files prepared for synchronization.
     *
     * @return The directory to be used for temporary files
     */
    public File getCacheDir() {
        return getContext().getCacheDir();
    }
}
