package de.cyface.persistence.model;

import static de.cyface.persistence.FileUtils.generateMeasurementFolderPath;

import java.io.File;
import java.io.IOException;

import android.content.Context;
import androidx.annotation.NonNull;
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
 * @version 2.0.0
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
    public Measurement(@NonNull final Context context, final long id, @NonNull final MeasurementStatus status) {
        this.id = id;
        this.status = status;
        this.context = context;
        // FIXME: also link the data Files I think
    }

    /**
     * Returns a link to the folder containing the measurement data.
     *
     * @return The {@link File} link to the measurement's folder.
     */
    public File getMeasurementFolder() {
        return generateMeasurementFolderPath(context, status, id);
    }

    // FIXME: re-generate hashcode and equals methods + toString method

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
     * FIXME: automatically move the measurement to the new status-folder
     *
     * Status life-cycle:
     * - {@link Persistence#newMeasurement(Vehicle)} -> {@link MeasurementStatus#OPEN}
     *
     * - {@link MeasurementStatus#OPEN} -> {@link MeasurementStatus#PAUSED}
     * - {@link MeasurementStatus#OPEN} -> {@link MeasurementStatus#FINISHED}
     *
     * - {@link MeasurementStatus#PAUSED} -> {@link MeasurementStatus#OPEN}
     * - {@link MeasurementStatus#PAUSED} -> {@link MeasurementStatus#FINISHED}
     *
     * - {@link MeasurementStatus#FINISHED} -> {@link MeasurementStatus#SYNCED}
     *
     * - FIXME: -> CORRUPTED? - also in the code below
     */
    public void setStatus(@NonNull final MeasurementStatus newStatus) {
        switch (status) {
            case OPEN:
                if (newStatus == MeasurementStatus.PAUSED) {
                    move(newStatus);
                    break;
                }
                if (newStatus == MeasurementStatus.FINISHED) {
                    move(newStatus);
                    break;
                }
                throw new IllegalStateException("Illegal MeasurementStatus life-cycle");
            case PAUSED:
                if (newStatus == MeasurementStatus.OPEN) {
                    move(newStatus);
                    break;
                }
                if (newStatus == MeasurementStatus.FINISHED) {
                    move(newStatus);
                    break;
                }
                throw new IllegalStateException("Illegal MeasurementStatus life-cycle");
            case FINISHED:
                if (newStatus == MeasurementStatus.SYNCED) {
                    move(newStatus);
                    break;
                }
                throw new IllegalStateException("Illegal MeasurementStatus life-cycle");
            case SYNCED:
                throw new IllegalStateException("Unsupported MeasurementStatus life-cycle");
            case CORRUPTED:
                throw new IllegalStateException("Unsupported MeasurementStatus life-cycle");
            default:
                throw new IllegalStateException("Undefined MeasurementState");
        }
        this.status = newStatus;
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
     * @return A {@link File} link to the created file.
     * @throws IllegalStateException when the measurement folder does not exist.
     */
    private File createFile(final String fileName, final String fileExtension) {
        final File file = new File(getMeasurementFolder() + File.separator + fileName + "." + fileExtension);
        if (!file.exists()) {
            try {
                final boolean success = file.createNewFile();
                if (!success) {
                    throw new IOException("File not created");
                }
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Unable to create file for measurement data: " + file.getAbsolutePath());
            }
        }
        return file;
    }

    /**
     * Returns a {@link File} link pointing to the requested file.
     *
     * @param fileName The name of the file
     * @param fileExtension The file extension of the file
     * @return the {@link File}.
     */
    public File getFile(final long measurementId, final String fileName, final String fileExtension) {
        final File file = new File(getMeasurementFolder() + File.separator + fileName + "." + fileExtension);
        if (!file.exists()) {
            throw new IllegalStateException("Cannot load file because it does not exist");
        }
        // FIXME: we should check somewhere (e.g. when creating/deleting a measurement that there is only one folder per
        // measurement id)
        return file;
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
        final File file = createFile(MetaFile.FILE_NAME, MetaFile.FILE_EXTENSION);
        this.metaFile = new MetaFile(file, this, vehicle);
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
