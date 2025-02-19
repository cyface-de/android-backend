/*
 * Copyright 2018-2025 Cyface GmbH
 *
 * This file is part of the Cyface SDK for Android.
 *
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.synchronization

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import de.cyface.synchronization.Constants.TAG
import de.cyface.synchronization.ErrorHandler.ErrorListener
import de.cyface.utils.Validate.notNull
import java.util.Locale

/**
 * Maintains and informs [ErrorListener]. This class is responsible for Cyface Errors.
 *
 * Best practices: When an unspecified error is reported by a user add an extras to the error which
 * the user can then report to us in addition or throw a more specific Exception. This helps to reduce
 * support time for all involved.
 *
 * @author Armin Schnabel
 */
class ErrorHandler : BroadcastReceiver() {
    private val errorListeners: MutableCollection<ErrorListener> = ArrayList()

    /**
     * Adds a party to the list to be informed when errors occur.
     *
     * @param errorListener the [ErrorListener]
     */
    fun addListener(errorListener: ErrorListener) {
        errorListeners.add(errorListener)
    }

    /**
     * Removes a party from the list of listeners.
     *
     * @param errorListener the [ErrorListener]
     */
    fun removeListener(errorListener: ErrorListener) {
        errorListeners.remove(errorListener)
    }

    override fun onReceive(context: Context, intent: Intent) {
        notNull(intent.extras)
        val errorCodeInt = intent.extras!!.getInt(ERROR_CODE_EXTRA)
        val fromBackground = intent.extras!!.getBoolean(ERROR_BACKGROUND_EXTRA)
        val errorCode = ErrorCode.getValueForCode(errorCodeInt)
        notNull(errorCode)
        val errorMessage: String
        when (errorCode) {
            ErrorCode.UNAUTHORIZED -> errorMessage = context
                .getString(R.string.error_message_credentials_incorrect)

            ErrorCode.MALFORMED_URL -> errorMessage =
                context.getString(R.string.error_message_url_unreadable)

            ErrorCode.UNREADABLE_HTTP_RESPONSE -> errorMessage = context
                .getString(R.string.error_message_http_response_unreadable)

            ErrorCode.SERVER_UNAVAILABLE -> errorMessage =
                context.getString(R.string.error_message_server_unavailable)

            ErrorCode.NETWORK_ERROR -> errorMessage = context
                .getString(R.string.error_message_unknown_network_error)

            ErrorCode.DATABASE_ERROR -> errorMessage =
                context.getString(R.string.error_message_data_access_error)

            ErrorCode.AUTHENTICATION_ERROR -> errorMessage = context
                .getString(R.string.error_message_unknown_authentication_error)

            ErrorCode.AUTHENTICATION_CANCELED -> errorMessage = context
                .getString(R.string.error_message_authentication_canceled)

            ErrorCode.SYNCHRONIZATION_ERROR -> errorMessage =
                context.getString(R.string.error_message_unknown_sync_error)

            ErrorCode.DATA_TRANSMISSION_ERROR -> {
                val httpCode = intent.extras!!.getInt(HTTP_CODE_EXTRA)
                errorMessage = String.format(
                    context.getString(
                        R.string.error_message_data_transmission_error_with_code
                    ),
                    httpCode
                )
            }

            ErrorCode.SSL_CERTIFICATE_UNKNOWN -> errorMessage =
                context.getString(R.string.error_message_ssl_certificate)

            ErrorCode.BAD_REQUEST -> errorMessage =
                context.getString(R.string.error_message_bad_request)

            ErrorCode.FORBIDDEN -> errorMessage =
                context.getString(R.string.error_message_forbidden)

            ErrorCode.INTERNAL_SERVER_ERROR -> errorMessage = context
                .getString(R.string.error_message_internal_server_error)

            ErrorCode.ENTITY_NOT_PARSABLE -> errorMessage =
                context.getString(R.string.error_message_entity_not_parsable)

            ErrorCode.NETWORK_UNAVAILABLE -> errorMessage =
                context.getString(R.string.error_message_network_unavailable)

            ErrorCode.SYNCHRONIZATION_INTERRUPTED -> errorMessage = context
                .getString(R.string.error_message_synchronization_interrupted)

            ErrorCode.TOO_MANY_REQUESTS -> errorMessage =
                context.getString(R.string.error_message_too_many_requests)

            ErrorCode.HOST_UNRESOLVABLE -> errorMessage =
                context.getString(R.string.error_message_host_unresolvable)

            ErrorCode.UPLOAD_SESSION_EXPIRED -> errorMessage =
                context.getString(R.string.error_message_upload_session_expired)

            ErrorCode.UNEXPECTED_RESPONSE_CODE -> errorMessage =
                context.getString(R.string.error_message_unexpected_response_code)

            ErrorCode.ACCOUNT_NOT_ACTIVATED -> errorMessage =
                context.getString(R.string.error_message_account_not_activated)

            else -> errorMessage = context.getString(R.string.error_message_unknown_error)
        }
        for (errorListener in errorListeners) {
            errorListener.onErrorReceive(errorCode, errorMessage, fromBackground)
        }
    }

    /**
     * A list of known Errors which are thrown by the Cyface SDK.
     *
     * @author Armin Schnabel
     */
    enum class ErrorCode(val code: Int) {
        UNKNOWN(0), UNAUTHORIZED(1), MALFORMED_URL(2), UNREADABLE_HTTP_RESPONSE(3), SERVER_UNAVAILABLE(
            4
        ),
        NETWORK_ERROR(5), DATABASE_ERROR(6), AUTHENTICATION_ERROR(7), AUTHENTICATION_CANCELED(
            8
        ),
        SYNCHRONIZATION_ERROR(9), DATA_TRANSMISSION_ERROR(10), SSL_CERTIFICATE_UNKNOWN(
            11
        ),
        BAD_REQUEST(12), FORBIDDEN(13), INTERNAL_SERVER_ERROR(
            14
        ),
        ENTITY_NOT_PARSABLE(
            15
        ),
        NETWORK_UNAVAILABLE(
            16
        ),
        SYNCHRONIZATION_INTERRUPTED(
            17
        ),
        TOO_MANY_REQUESTS(18), HOST_UNRESOLVABLE(
            19
        ),
        UPLOAD_SESSION_EXPIRED(
            20
        ),
        UNEXPECTED_RESPONSE_CODE(21), ACCOUNT_NOT_ACTIVATED(22); // MEASUREMENT_ENTRY_IS_IRRETRIEVABLE(X),

        companion object {
            fun getValueForCode(code: Int): ErrorCode? {
                for (value in entries) {
                    if (value.code == code) {
                        return value
                    }
                }
                return null
            }
        }
    }

    /**
     * Interface for listeners receiving errors.
     *
     * @author Armin Schnabel
     */
    interface ErrorListener {
        /**
         * Handler called upon new errors.
         *
         * @param errorCode the Cyface error code
         * @param errorMessage A message which can be shown to the user, e.g. as toast.
         * @param fromBackground `true` if the error was caused without user interaction, e.g. to avoid
         * disturbing the user while he is not using the app.
         */
        fun onErrorReceive(errorCode: ErrorCode?, errorMessage: String?, fromBackground: Boolean)
    } // The following error handling will be (re)implemented with #CY-3709
    // if (resultExceptionType.equals(MeasurementEntryIsIrretrievableException.class.getSimpleName())) {
    // toastErrorMessage = context.getString(R.string.toast_error_message_unknown_sync_error)
    // + "(" + ErrorCode.MEASUREMENT_ENTRY_IS_IRRETRIEVABLE.ordinal() + ", " + causeId + ", " + causeExtra + ")";
    // if (resultExceptionType.equals(SSLHandshakeException.class.getSimpleName())) {
    // toastErrorMessage = context.getString(R.string.toast_error_message_ssl_certificate);

    companion object {
        const val ERROR_INTENT: String = "de.cyface.error"
        const val ERROR_CODE_EXTRA: String = "de.cyface.error.error_code"
        const val ERROR_BACKGROUND_EXTRA: String = "de.cyface.error.from_background"
        const val HTTP_CODE_EXTRA: String = "de.cyface.error.http_code"

        /**
         * Informs listeners, e.g. a SDK implementing app, about errors.
         *
         * @param context the [Context]
         * @param errorCode the Cyface error code
         * @param httpCode the HTTP error returned by the server
         */
        fun sendErrorIntent(
            context: Context?, errorCode: Int, httpCode: Int,
            message: String?
        ) {
            val intent = Intent(ERROR_INTENT)
            intent.putExtra(HTTP_CODE_EXTRA, httpCode)
            intent.putExtra(ERROR_CODE_EXTRA, errorCode)
            // Other than ShutdownFinishedHandler, this seem to work with local broadcast
            LocalBroadcastManager.getInstance(context!!).sendBroadcast(intent)
            Log.d(
                TAG,
                String.format(
                    Locale.getDefault(),
                    "ErrorHandler (err %s, httpErr %s): %s",
                    errorCode,
                    httpCode,
                    message
                )
            )
        }

        /**
         * Informs listeners, e.g. a SDK implementing app, about errors.
         *
         * @param context the [Context]
         * @param errorCode the Cyface error code
         * @param message A message which can be shown to the user, e.g. as toast.
         * @param fromBackground `true` if the error was caused without user interaction, e.g. to avoid
         * disturbing the user while he is not using the app.
         */
        fun sendErrorIntent(
            context: Context?,
            errorCode: Int,
            message: String?,
            fromBackground: Boolean
        ) {
            val intent = Intent(ERROR_INTENT)
            intent.putExtra(ERROR_CODE_EXTRA, errorCode)
            intent.putExtra(ERROR_BACKGROUND_EXTRA, fromBackground)
            // Other than ShutdownFinishedHandler, this seem to work with local broadcast
            LocalBroadcastManager.getInstance(context!!).sendBroadcast(intent)
            if (message != null) {
                Log.d(TAG, message)
            }
        }
    }
}
