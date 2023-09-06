package de.cyface.synchronization

/**
 * Final static constants used by multiple classes.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.3.0
 * @since 2.0.0
 */
object Constants {
    /**
     * Tag used to identify Logcat messages issued by instances of this package.
     */
    const val TAG = "de.cyface.sync"

    /**
     * This may be used by all implementing apps, thus, public
     */
    // Because this allows the sdk integrating app to add a sync account
    const val AUTH_TOKEN_TYPE = "de.cyface.auth_token_type"
}