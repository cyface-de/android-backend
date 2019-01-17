package de.cyface.persistence.serialization;

import java.io.File;

import android.content.Context;
import androidx.annotation.NonNull;
import de.cyface.persistence.FileUtils;
import de.cyface.persistence.model.Event;
import de.cyface.persistence.model.Point3d;
import de.cyface.utils.Validate;

/**
 * The file format to persist user interactions like pausing and resumung a measurement.
 *
 * @author Armin Schnabel
 * @version 3.0.0
 * @since 3.0.0
 */
public class EventsFile implements FileSupport<Event> {

    /**
     * The {@link File} pointer to the actual file.
     */
    private final File file;
    /**
     * The name of the folder containing event data. Separating the files of each data type should improve performance
     * when searching for files.
     */
    public static final String EVENTS_FOLDER_NAME = "events";
    /**
     * The file extension of files containing direction data. This makes sure no system-generated files in the
     * {@link #EVENTS_FOLDER_NAME} are identified as {@link Point3dFile}s.
     */
    public final static String EVENT_FILE_EXTENSION = "cyfe";

    /**
     * Constructor which actually creates a new {@link EventsFile} in the persistence layer.
     *
     * @param context The {@link Context} required to access the underlying persistence layer.
     * @param measurementId the identifier of the measurement for which the file is to be found
     * @param folderName The folder name defining the {@link Point3d} type of the file
     * @param fileExtension the extension of the file type
     */
    public EventsFile(@NonNull final Context context, final long measurementId, @NonNull final String folderName,
            @NonNull final String fileExtension) {
        this.file = FileUtils.createFile(context, measurementId, folderName, fileExtension);
    }

    /**
     * Constructor to reference existing {@link EventsFile}.
     *
     * @param file The already existing file which represents the {@link EventsFile}
     */
    private EventsFile(@NonNull final File file) {
        this.file = file;
    }

    public File getFile() {
        return file;
    }

    @Override
    public void append(final Event event) {
        final byte[] data = serialize(event);
        FileUtils.write(file, data, true);
    }

    @Override
    public byte[] serialize(final Event event) {
        return MeasurementSerializer.serialize(event);
    }

    /**
     * Loads an existing {@link EventsFile} for a specified measurement. The {@link File} must already exist.
     * If you want to create a new {@code EventsFile} use the Constructor.
     *
     * @param context The {@link Context} required to access the underlying persistence layer.
     * @param measurementId the identifier of the measurement for which the file is to be found
     * @param folderName The folder name defining the {@link Point3d} type of the file
     * @param fileExtension the extension of the file type
     * @return the {@link EventsFile} link to the file
     * @throws IllegalStateException if there is no such file
     */
    public static EventsFile loadFile(@NonNull final Context context, final long measurementId,
            @NonNull final String folderName, @NonNull final String fileExtension) {
        final File file = FileUtils.getFilePath(context, measurementId, folderName, fileExtension);
        Validate.isTrue(file.exists());
        return new EventsFile(file);
    }
}