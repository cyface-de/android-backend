package de.cyface.persistence.model;

/**
 * The {@link Modality} types to choose from when starting a {@link Measurement}.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 2.0.0
 * @since 1.0.0
 */
public enum Modality {
    BICYCLE("BICYCLE"), CAR("CAR"), MOTORBIKE("MOTORBIKE"), BUS("BUS"), TRAIN("TRAIN"), WALKING("WALKING"), UNKNOWN(
            "UNKNOWN");

    private String databaseIdentifier;

    Modality(final String databaseIdentifier) {
        this.databaseIdentifier = databaseIdentifier;
    }

    public String getDatabaseIdentifier() {
        return databaseIdentifier;
    }
}
