package de.cyface.persistence.model;

/**
 * Events are user interactions which we need to log, e.g.:
 * - to split a GPS track when the track is paused into sub-tracks without the pauses
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.0.0
 */
public enum Event {
    CAPTURING_STARTED, CAPTURING_PAUSED, CAPTURING_RESUMED, CAPTURING_STOPPED
}
