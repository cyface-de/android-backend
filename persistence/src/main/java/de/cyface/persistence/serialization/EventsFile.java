package de.cyface.persistence.serialization;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import androidx.annotation.NonNull;
import de.cyface.persistence.FileUtils;
import de.cyface.persistence.model.Event;
import de.cyface.persistence.model.Measurement;
import de.cyface.utils.Validate;

/**
 * The file format to persist user interactions like pausing and resumung a measurement.
 *
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 3.0.0
 */
public class EventsFile implements FileSupport<Event> {

    /**
     * The {@link File} pointer to the actual file.
     */
    private final File file;
    /**
     * The {@link Measurement} to which this file is part of.
     */
    private final Measurement measurement;
    /**
     * The name of the file containing the data
     */
    private static final String FILE_NAME = "e";
    /**
     * The name of the file containing the data
     */
    private static final String FILE_EXTENSION = "cyfe";

    /**
     * Constructor which actually creates a new {@link EventsFile} in the persistence layer.
     *
     * @param measurement The {@link Measurement} to which this file is part of.
     */
    public EventsFile(@NonNull final Measurement measurement) {
        this.file = measurement.createFile(FILE_NAME, FILE_EXTENSION);
        this.measurement = measurement;
    }

    /**
     * Constructor to reference existing {@link EventsFile}.
     *
     * @param measurement The {@link Measurement} to which this file is part of.
     * @param file The already existing file which represents the {@link EventsFile}
     */
    private EventsFile(@NonNull final Measurement measurement, @NonNull final File file) {
        this.file = file;
        this.measurement = measurement;
    }

    public File getFile() {
        return file;
    }

    public Measurement getMeasurement() {
        return measurement;
    }

    @Override
    public void append(final Event event) {

        final byte[] data = serialize(event);

        try {
            final BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file, true));
            outputStream.write(data);
            outputStream.close();
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to append data to file.");
        }
    }

    @Override
    public byte[] serialize(final Event event) {
        return MeasurementSerializer.serialize(event);
    }

    /**
     * Loads an existing {@link EventsFile} for a specified {@link Measurement}.
     *
     * @return the {@link EventsFile} link to the file
     * @throws IllegalStateException if there is no such file
     */
    public static EventsFile loadFile(@NonNull final Measurement measurement) {
        final File file = FileUtils.generateMeasurementFilePath(measurement, FILE_NAME, FILE_EXTENSION);
        Validate.isTrue(file.exists());
        return new EventsFile(measurement, file);
    }
}
