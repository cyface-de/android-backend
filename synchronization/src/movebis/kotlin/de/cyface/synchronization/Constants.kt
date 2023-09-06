package de.cyface.synchronization

/**
 * Final static constants used by multiple classes.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.1
 * @since 2.0.0
 */
object Constants {
    /**
     * Tag used to identify Logcat messages issued by instances of this package.
     */
    const val TAG = "de.cyface.sync"
    // TODO [MOV-555]: Change these strings between two (STADTRADELN) campaigns.
    /**
     * The Cyface account type used to identify all Cyface system accounts.
     */
    const val ACCOUNT_TYPE = "de.cyface"

    @Suppress("unused") // Because this allows the sdk integrating app to add a sync account

    val AUTH_TOKEN_TYPE = "de.cyface.jwt"
}