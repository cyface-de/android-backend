package de.cyface.persistence.serialization;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import android.content.Context;

import de.cyface.persistence.FileUtils;
import de.cyface.persistence.model.Event;

/**
 * The file format to persist user interactions like pausing and resumung a measurement.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.0.0
 */
public class EventsFile implements FileSupport<Event> {

    /**
     * The {@link File} pointer to the actual file.
     */
    private final File file;
    /**
     * The name of the file containing the data
     */
    public final String FILE_NAME = "e";
    /**
     * The name of the file containing the data
     */
    public final String FILE_EXTENSION = "cyfe";

    public EventsFile(final Context context, final long measurementId) {
        this.file = new FileUtils(context).createFile(measurementId, FILE_NAME, FILE_EXTENSION);
    }

    @Override
    public void append(final Event event) {

        final byte[] data = serialize(event);

        try {
            final BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file, true));
            outputStream.write(data);
            outputStream.close();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to append data to file.");
        }
    }

    @Override
    public byte[] serialize(final Event event) {
        return MeasurementSerializer.serialize(event);
    }
}
