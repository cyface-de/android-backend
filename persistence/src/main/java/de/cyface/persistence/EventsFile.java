package de.cyface.persistence;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import de.cyface.persistence.model.Event;

public class EventsFile implements FileSupport<Event> {

    private final File file;
    public final String fileName = "e";
    public final String fileExtension = "cyfe";

    public EventsFile(final long measurementId) {
        this.file = Constants.createFile(measurementId, fileName, fileExtension);
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
