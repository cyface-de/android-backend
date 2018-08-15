package de.cyface.synchronization;

/**
 * Final static constants used by multiple classes.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.0
 * @since 2.0.0
 */
public final class Constants {

    final static int GEO_LOCATIONS_UPLOAD_BATCH_SIZE = 10;
    final static int ACCELERATIONS_UPLOAD_BATCH_SIZE = 2_000;
    final static int ROTATIONS_UPLOAD_BATCH_SIZE = 2_000;
    final static int DIRECTIONS_UPLOAD_BATCH_SIZE = 2_000;

    // This may be used by all implementing apps, thus, public
    public final static String AUTH_TOKEN_TYPE = "de.cyface.auth_token_type";

    private Constants() {
        // Nothing to do here.
    }
}
