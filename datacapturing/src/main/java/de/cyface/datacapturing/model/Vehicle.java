package de.cyface.datacapturing.model;

/**
 * Created by muthmann on 02.02.18.
 */
public enum Vehicle {
    BICYCLE("BICYCLE"), CAR("CAR"), MOTORBIKE("MOTORBIKE"), UNKOWN("UNKOWN");

    private String databaseIdentifier;

    private Vehicle(final String databaseIdentifier) {
        this.databaseIdentifier = databaseIdentifier;
    }

    public String getDatabaseIdentifier() {
        return databaseIdentifier;
    }
}
