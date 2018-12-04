package de.cyface.datacapturing;

import de.cyface.datacapturing.backend.DataCapturingBackgroundService;

/**
 * Final static constants used by multiple classes.
 *
 * @author Armin Schnabel
 * @version 1.0.1
 * @since 2.5.0
 */
public final class Constants {

    /**
     * Tag used to identify Logcat messages issued by instances of this package.
     */
    public final static String TAG = "de.cyface.capturing";
    /**
     * Tas used for logging from {@link DataCapturingBackgroundService}
     */
    public final static String BACKGROUND_TAG = "de.cyface.background";

    /**
     * The minimum space required for capturing. We don't want to use the space full up as this would
     * slow down the device and could get unusable.
     */
    public final static long MINIMUM_MEGABYTES_REQUIRED = 100L;

    private Constants() {
        // Nothing to do here.
    }
}
