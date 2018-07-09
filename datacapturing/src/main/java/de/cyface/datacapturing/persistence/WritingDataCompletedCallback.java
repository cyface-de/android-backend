package de.cyface.datacapturing.persistence;

/**
 * An interface for callbacks, called when writing sensor data for a measurement has completed.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.1
 */
public interface WritingDataCompletedCallback {
    /**
     * Called upon completion of writing sensor data to the <code>MeasuringPointContentProvider</code>.
     */
    void writingDataCompleted();
}
