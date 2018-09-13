package de.cyface.synchronization;

import android.os.Environment;

/**
 * Final static constants used by multiple classes.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.0.1
 * @since 2.0.0
 */
public final class Constants {

    // TODO: Change these strings between two STADTRADELN Campaigns.
    /**
     * The Cyface account type used to identify all Cyface system accounts.
     */
    public final static String ACCOUNT_TYPE = "de.cyface";
    public final static String AUTH_TOKEN_TYPE = "de.cyface.jwt";

    /**
     * The disk where data can be stored by the SDK, e.g. image material.
     */
    public final static String BASE_PATH = Environment.getExternalStorageDirectory().getPath();

    /**
     * The minimum space required for capturing. We don't want to use the space full up as this would
     * slow down the device and could get unusable.
     */
    public final static long MINIMUM_MEGABYTES_REQUIRED = 100L;

    private Constants() {
        // Nothing to do here.
    }
}
