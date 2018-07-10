package de.cyface.synchronization;

public final class Constants {

    final static int GEO_LOCATIONS_UPLOAD_BATCH_SIZE = 10;
    final static int ACCELERATIONS_UPLOAD_BATCH_SIZE = 2_000;
    final static int ROTATIONS_UPLOAD_BATCH_SIZE = 2_000;
    final static int DIRECTIONS_UPLOAD_BATCH_SIZE = 2_000;

    public final static String ACCOUNT_TYPE = "de.cyface.account";
    public final static String AUTH_TOKEN_TYPE = "de.cyface.auth_token_type";

    public final static String DEFAULT_FREE_USERNAME = "playStoreBeta";
    public final static String DEFAULT_FREE_PASSWORD = "playStoreBeta@Cy";

    private Constants() {
        // Nothing to do here.
    }
}
