package de.cyface.persistence.model;

/**
 * Status which defines whether a {@link Measurement} is still capturing data({@link #OPEN}), {@link #PAUSED},
 * {@link #FINISHED} or {@link #SYNCED}.
 *
 * Usually only one {@code Measurement} should be {@link #OPEN} or {@link #PAUSED}; else there has been some error.
 *
 * @author Armin Schnabel
 * @version 2.0.1
 * @since 3.0.0
 */
public enum MeasurementStatus {
    /**
     * This state defines that a {@link Measurement} is currently active.
     */
    OPEN("OPEN"),
    /**
     * This state defines that an active {@link Measurement} was paused and not yet {@link #FINISHED} or resumed.
     */
    PAUSED("PAUSED"),
    /**
     * This state defines that a {@link Measurement} has been completed and was not yet {@link #SYNCED}.
     */
    FINISHED("FINISHED"),
    /**
     * This state defines that a {@link Measurement} has been synchronized.
     */
    SYNCED("SYNCED");

    private String databaseIdentifier;

    MeasurementStatus(final String databaseIdentifier) {
        this.databaseIdentifier = databaseIdentifier;
    }

    public String getDatabaseIdentifier() {
        return databaseIdentifier;
    }
}