package de.cyface.synchronization;

public final class Constants {

    final static int GEO_LOCATIONS_UPLOAD_BATCH_SIZE = 10;
    final static int ACCELERATIONS_UPLOAD_BATCH_SIZE = 2_000;
    final static int ROTATIONS_UPLOAD_BATCH_SIZE = 2_000;
    final static int DIRECTIONS_UPLOAD_BATCH_SIZE = 2_000;

    private Constants() {
        // Nothing to do here.
    }
}
