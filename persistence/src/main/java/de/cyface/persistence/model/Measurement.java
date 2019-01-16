package de.cyface.persistence.model;

import static de.cyface.persistence.FileUtils.generateMeasurementFilePath;
import static de.cyface.persistence.FileUtils.generateMeasurementFolderPath;

import java.io.File;
import java.io.IOException;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import de.cyface.persistence.Constants;
import de.cyface.persistence.Persistence;
import de.cyface.persistence.serialization.MetaFile;
import de.cyface.utils.Validate;

/**
 * An object of this class represents a single measurement captured by the {@code DataCapturingService}. This usually
 * happens between complementary calls to
 * {@code DataCapturingService#startAsync(DataCapturingListener, Vehicle, StartUpFinishedHandler)} and
 * {@code DataCapturingService#stopAsync(ShutDownFinishedHandler)}.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.2.0
 * @since 1.0.0
 */
public final class Measurement {
    /**
     * The system wide unique identifier of this measurement.
     */
    private long id;
    /**
     * The {@link MeasurementStatus} of the measurement tells weather the measurement is still capturing data, finished
     * or similar.
     */
    private MeasurementStatus status;
    /**
     * The {@link Context} required to access the underlying persistence layer.
     */
    private Context context;
    /**
     * The file to persist meta information of a measurement.
     */
    private MetaFile metaFile;

    /**
     * Creates a new {@link Measurement}. Use {@link Persistence#newMeasurement(Vehicle)} instead to create a new
     * measurement it in the persistence layer, too.
     *
     * @param context The {@link Context} required to access the underlying persistence layer.
     * @param id The system wide unique identifier of this measurement.
     * @param status The {@link MeasurementStatus} of the measurement tells weather the measurement is still capturing
     *            data, finished or similar.
     */
    // TODO: we should check somewhere (when creating/deleting a m?) that there is only one folder per measurement id)
    public Measurement(@NonNull final Context context, final long id, @NonNull final MeasurementStatus status) {
        this.id = id;
        this.status = status;
        this.context = context;
    }

    /**
     * Returns a link to the folder containing the measurement data.
     *
     * @return The {@link File} link to the measurement's folder.
     */
    public File getMeasurementFolder() {
        return generateMeasurementFolderPath(context, status, id);
    }

    @Override
    public String toString() {
        return "Measurement{" + "id=" + id + ", status=" + status + ", context=" + context + ", metaFile=" + metaFile
                + '}';
    }

    // TODO how to handle when the MetaFile was not yet loaded? Maybe make sure that it's aways loaded?
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Measurement that = (Measurement)o;
        return id == that.id && status == that.status && context.equals(that.context)
                && ((metaFile == null && that.metaFile == null)
                        || (metaFile != null && that.metaFile != null && metaFile.equals(that.metaFile)));
    }

    @Override
    public int hashCode() {
        return (int)(id ^ (id >>> 32));
    }

    public long getIdentifier() {
        return id;
    }

    public MeasurementStatus getStatus() {
        return status;
    }

    /**
     *
     * Close the specified {@link Measurement}.
     * (!) Attention: This only moves the measurement to the finished folder. The
     * <code>DataCapturingBackgroundService</code>
     * must have stopped normally in advance in order for the point counts to be written into the {@link MetaFile}.
     * Else, the file is seen as corrupted.
     *
     * Supported MeasurementStatus life-cycle:
     * - {@link Persistence#newMeasurement(Vehicle)} -> {@link MeasurementStatus#OPEN}
     *
     * - {@link MeasurementStatus#OPEN} -> {@link MeasurementStatus#PAUSED}
     * - {@link MeasurementStatus#OPEN} -> {@link MeasurementStatus#FINISHED}
     *
     * - {@link MeasurementStatus#PAUSED} -> {@link MeasurementStatus#OPEN}
     * - {@link MeasurementStatus#PAUSED} -> {@link MeasurementStatus#FINISHED}
     *
     * - {@link MeasurementStatus#FINISHED} -> {@link MeasurementStatus#SYNCED}
     */
    public void setStatus(@NonNull final MeasurementStatus newStatus) {
        if (status == MeasurementStatus.SYNCED || status == MeasurementStatus.CORRUPTED) {
            throw new IllegalStateException("Unsupported MeasurementStatus life-cycle");
        }

        final boolean supportedOpenMeasurementLifeCycleFlow = status == MeasurementStatus.OPEN
                && (newStatus == MeasurementStatus.PAUSED || newStatus == MeasurementStatus.FINISHED
                        || newStatus == MeasurementStatus.CORRUPTED);
        final boolean supportedPausedMeasurementLifeCycleFlow = status == MeasurementStatus.PAUSED
                && (newStatus == MeasurementStatus.OPEN || newStatus == MeasurementStatus.FINISHED);
        final boolean supportedFinishedMeasurementLifeCycleFlow = status == MeasurementStatus.FINISHED
                && (newStatus == MeasurementStatus.SYNCED);

        if (supportedOpenMeasurementLifeCycleFlow || supportedPausedMeasurementLifeCycleFlow
                || supportedFinishedMeasurementLifeCycleFlow) {
            move(newStatus);
            status = newStatus;
            metaFile = MetaFile.loadFile(this); // Because the measurement was moved to another location
            return;
        }

        throw new IllegalStateException("Illegal MeasurementStatus life-cycle");

    }

    /**
     * Moves a {@link Measurement} from one {@link MeasurementStatus} folder to another status folder.
     *
     * @param newStatus the target status of the measurement to move.
     * @throws IllegalStateException when the current measurement folder does not exist or when the measurement could
     *             not be moved.
     */
    private void move(MeasurementStatus newStatus) {
        synchronized (this) {
            final File measurementFolder = getMeasurementFolder();
            if (!measurementFolder.exists()) {
                throw new IllegalStateException("Failed to access non existent measurement: " + measurementFolder);
            }

            final File targetMeasurementFolder = generateMeasurementFolderPath(context, newStatus, id);
            if (!measurementFolder.renameTo(targetMeasurementFolder)) {
                throw new IllegalStateException("Failed to move measurement: " + measurementFolder.getParent() + " to "
                        + targetMeasurementFolder.getPath());
            }
        }
    }

    /**
     * Creates the folder for this measurement. Its {@link MeasurementStatus} defined where the folder is created.
     */
    public void createDirectory() {
        final File measurementDir = getMeasurementFolder();
        if (measurementDir.exists()) {
            throw new IllegalStateException("Cannot create measurement folder as it already exists.");
        }
        if (!measurementDir.mkdirs()) {
            throw new IllegalStateException("Failed to create measurement folder: " + measurementDir.getAbsolutePath());
        }
    }

    /**
     * Creates a file for Cyface binary data for this measurement.
     *
     * @param fileName the name of the file
     * @param fileExtension the extension of the file
     * @return A {@link File} link to the created file.
     * @throws IllegalStateException when the measurement folder does not exist.
     */
    public File createFile(@NonNull final String fileName, @NonNull final String fileExtension) {
        final File file = generateMeasurementFilePath(this, fileName, fileExtension);
        Validate.isTrue(!file.exists(), "Failed to createFile as it already exists: " + file.getPath());
        try {
            if (!file.createNewFile()) {
                throw new IOException("Failed to createFile: " + file.getPath());
            }
            Validate.isTrue(file.exists());
            Log.d(Constants.TAG, "CreateFile successful: " + file.getAbsolutePath());
        } catch (final IOException e) {
            throw new IllegalStateException("Failed createFile: " + file.getPath());
        }
        return file;
    }

    public MetaFile getMetaFile() {
        return metaFile;
    }

    /**
     * Loads the {@link MetaFile} from the persistence layer and re-links it to this measurement internally.
     */
    public void loadMetaFile() {
        Validate.isTrue(metaFile == null, "Wrong method use: MetaFile already linked.");
        metaFile = MetaFile.loadFile(this);
        Validate.isTrue(metaFile.getFile().exists());
    }

    /**
     * Creates a new {@link MetaFile} for this measurement.
     *
     * @throws IllegalStateException when the {@link Measurement} is in a different state then
     *             {@link Measurement.MeasurementStatus#OPEN}
     * @param vehicle The {@link Vehicle} used in the measurement
     */
    public void createMetaFile(@NonNull final Vehicle vehicle) {
        Validate.isTrue(getStatus() == MeasurementStatus.OPEN, "Unsupported");
        this.metaFile = new MetaFile(this, vehicle);
        Validate.isTrue(metaFile.getFile().exists());
    }

    /**
     * Status which defines weather a measurement is still capturing data, paused, finished, synced or corrupted.
     *
     * @author Armin Schnabel
     * @version 1.0.0
     * @since 3.0.0
     */
    public enum MeasurementStatus {
        OPEN, PAUSED, FINISHED, SYNCED, CORRUPTED
    }
}
