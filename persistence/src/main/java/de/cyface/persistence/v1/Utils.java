package de.cyface.persistence.v1;

import android.net.Uri;

import androidx.annotation.NonNull;

import de.cyface.persistence.v1.EventTable;
import de.cyface.persistence.v1.GeoLocationsTable;
import de.cyface.persistence.v1.IdentifierTable;
import de.cyface.persistence.v1.MeasurementTable;

/**
 * This utility class contains shared static methods used by multiple modules.
 *
 * @author Armin Schnabel
 * @version 1.1.0
 * @since 3.0.0
 */
public class Utils {

    /**
     * (!) It's important to provide the authority string as parameter because depending on from where you call this
     * you want to access your own authorities database.
     *
     * @param authority The authority to access the database
     */
    public static Uri getMeasurementUri(@NonNull final String authority) {
        return new Uri.Builder().scheme("content").authority(authority).appendPath(MeasurementTable.URI_PATH).build();
    }

    /**
     * (!) It's important to provide the authority string as parameter because depending on from where you call this
     * you want to access your own authorities database.
     *
     * @param authority The authority to access the database
     */
    public static Uri getGeoLocationsUri(@NonNull final String authority) {
        return new Uri.Builder().scheme("content").authority(authority).appendPath(GeoLocationsTable.URI_PATH).build();
    }

    /**
     * (!) It's important to provide the authority string as parameter because depending on from where you call this
     * you want to access your own authorities database.
     *
     * @param authority The authority to access the database
     */
    public static Uri getIdentifierUri(@NonNull final String authority) {
        return new Uri.Builder().scheme("content").authority(authority).appendPath(IdentifierTable.URI_PATH).build();
    }

    /**
     * (!) It's important to provide the authority string as parameter because depending on from where you call this
     * you want to access your own authorities database.
     *
     * @param authority The authority to access the database
     */
    public static Uri getEventUri(@NonNull final String authority) {
        return new Uri.Builder().scheme("content").authority(authority).appendPath(EventTable.URI_PATH).build();
    }
}
