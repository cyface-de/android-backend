package de.cyface.synchronization;

import java.io.File;

import android.os.Environment;

/**
 * Final static constants used by multiple classes.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.2.3
 * @since 2.0.0
 */
public final class Constants {

    /**
     * Increasing the _total batch size_ (i.e. sum of all data type batch sizes) leads to more data
     * loaded into the memory.
     *
     * Increasing _on of the batch sizes_ can lead to a "FAILED BINDER TRANSACTION". *
     * In #CY-3859 this occurred with a parcel size of ~1_380_000 (batch size 6_000).
     */
    final static int GEO_LOCATIONS_UPLOAD_BATCH_SIZE = 1_500;
    final static int ACCELERATIONS_UPLOAD_BATCH_SIZE = 2_000;
    final static int ROTATIONS_UPLOAD_BATCH_SIZE = 2_000;
    final static int DIRECTIONS_UPLOAD_BATCH_SIZE = 2_000;

    /**
     * This may be used by all implementing apps, thus, public
     */
    public final static String AUTH_TOKEN_TYPE = "de.cyface.auth_token_type";

    /**
     * The charset used to parse Strings (e.g. for JSON data)
     */
    public final static String DEFAULT_CHARSET = "UTF-8";

    /**
     * The disk where data can be stored by the SDK, e.g. image material.
     */
    public final static String BASE_PATH = Environment.getExternalStorageDirectory().getPath();

    /**
     * The path where files can be stored by the SDK, e.g. image material.
     */
    public final static String CYFACE_FOLDER_PATH = BASE_PATH + File.separator + "Cyface";

    /**
     * The minimum space required for capturing. We don't want to use the space full up as this would
     * slow down the device and could get unusable.
     */
    public final static long MINIMUM_MEGABYTES_REQUIRED = 100L;

    private Constants() {
        // Nothing to do here.
    }
}
