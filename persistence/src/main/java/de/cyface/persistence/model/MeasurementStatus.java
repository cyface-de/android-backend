package de.cyface.persistence.model;

/**
 * Status which defines weather a measurement is still capturing data, (not yet supported) paused, finished, synced or
 * corrupted. This type is used to allow generalisation of status based methods such as hasMeasurement(OPEN, etc.).
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.0.0
 */
public enum MeasurementStatus {
    OPEN, /* PAUSED, currently the measurement table sees paused measurements as open */FINISHED, SYNCED, CORRUPTED
}