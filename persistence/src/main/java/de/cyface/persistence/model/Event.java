package de.cyface.persistence.model;

public enum Event {
    CAPTURING_STARTED("CAPTURING_STARTED"), CAPTURING_PAUSED("CAPTURING_PAUSED"), CAPTURING_RESUMED(
            "CAPTURING_RESUMED"), CAPTURING_STOPPED("CAPTURING_STOPPED"), UNKNOWN("UNKNOWN");

    private String databaseIdentifier;

    // FIXME: what's this useful for? (from vehicle)
    Event(final String databaseIdentifier) {
        this.databaseIdentifier = databaseIdentifier;
    }

    public String getDatabaseIdentifier() {
        return databaseIdentifier;
    }
}
