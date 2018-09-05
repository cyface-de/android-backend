package de.cyface.synchronization;

/**
 * Final static constants used by multiple classes.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.2.1
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

    // This may be used by all implementing apps, thus, public
    public final static String AUTH_TOKEN_TYPE = "de.cyface.auth_token_type";

    private Constants() {
        // Nothing to do here.
    }
}
