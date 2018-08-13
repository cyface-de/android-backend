package de.cyface.synchronization;

import android.net.Uri;

import de.cyface.persistence.GpsPointsTable;
import de.cyface.persistence.MagneticValuePointTable;
import de.cyface.persistence.MeasurementTable;
import de.cyface.persistence.RotationPointTable;
import de.cyface.persistence.SamplePointTable;

final class TestUtils {
    /**
     * The tag used to identify Logcat messages from this module.
     */
    final static String TAG = "de.cyface.synchronization.test";
    final static String AUTHORITY = "de.cyface.synchronization.provider.test";
    final static String ACCOUNT_TYPE = "de.cyface.synchronization.account.test";

    static Uri getMeasurementUri() {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(MeasurementTable.URI_PATH).build();
    }

    static Uri getGeoLocationsUri() {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(GpsPointsTable.URI_PATH).build();
    }

    static Uri getAccelerationsUri() {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(SamplePointTable.URI_PATH).build();
    }

    static Uri getRotationsUri() {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(RotationPointTable.URI_PATH).build();
    }

    static Uri getDirectionsUri() {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(MagneticValuePointTable.URI_PATH).build();
    }
}
