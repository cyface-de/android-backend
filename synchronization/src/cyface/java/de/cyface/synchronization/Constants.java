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

    // FIXME: Duplicate constants - move ErrorHandler to SDK
    static final String ERROR_INTENT = "de.cyface.error";
    static final String ERROR_CODE_EXTRA = "de.cyface.error.error_code";
    final static String HTTP_CODE_EXTRA = "de.cyface.error.http_code";
    public static final int UNKNOWN_EC = 0;
    public static final int UNAUTHORIZED_EC = 1;
    public static final int MALFORMED_URL_EC = 2;
    public static final int HTTP_RESPONSE_UNREADABLE_EC = 3;
    public static final int SERVER_UNAVAILABLE_EC = 4;
    public static final int NETWORK_ERROR_EC = 5;
    public static final int DATABASE_ERROR_EC = 6;
    public static final int AUTHENTICATION_ERROR_EC = 7;
    public static final int AUTHENTICATION_CANCELED_ERROR_EC = 8;
    public static final int SYNCHRONIZATION_ERROR_EC = 9;
    public static final int DATA_TRANSMISSION_ERROR_EC = 10;
    // public static final int MEASUREMENT_ENTRY_IS_IRRETRIEVABLE = X;

    private Constants() {
        // Nothing to do here.
    }
}
