package de.cyface.persistence.model;

/**
 * The vehicle context to choose from when starting a measurement.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 1.0.1
 * @since 1.0.0
 */
public enum Vehicle {
    BICYCLE("BICYCLE"), CAR("CAR"), MOTORBIKE("MOTORBIKE"), UNKNOWN("UNKNOWN");

    private String databaseIdentifier;

    Vehicle(final String databaseIdentifier) {
        this.databaseIdentifier = databaseIdentifier;
    }

    public String getDatabaseIdentifier() {
        return databaseIdentifier;
    }
}
