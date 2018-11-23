package de.cyface.utils;

import static de.cyface.utils.Constants.TAG;

import java.util.ArrayList;
import java.util.Collection;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

/**
 * Maintains and informs {@link ErrorListener}. This class is responsible for Cyface Errors.
 * <p>
 * Best practices: When an unspecified error is reported by a user add an extras to the error which
 * the user can then report to us in addition or throw a more specific Exception. This helps to reduce
 * support time for all involved.
 *
 * @author Armin Schnabel
 * @version 1.1.0
 * @since 2.2.0
 */
public class ErrorHandler extends BroadcastReceiver {

    public final static String ERROR_INTENT = "de.cyface.error";
    public final static String ERROR_CODE_EXTRA = "de.cyface.error.error_code";
    public final static String HTTP_CODE_EXTRA = "de.cyface.error.http_code";
    private final Collection<ErrorListener> errorListeners;

    public ErrorHandler() {
        this.errorListeners = new ArrayList<>();
    }

    /**
     * Adds a party to the list to be informed when errors occur.
     *
     * @param errorListener the {@link ErrorListener}
     */
    public void addListener(final ErrorListener errorListener) {
        errorListeners.add(errorListener);
    }

    /**
     * Removes a party from the list of listeners.
     *
     * @param errorListener the {@link ErrorListener}
     */
    public void removeListener(final ErrorListener errorListener) {
        errorListeners.remove(errorListener);
    }

    /**
     * Informs listeners, e.g. a SDK implementing app, about errors.
     *
     * @param context the {@link Context}
     * @param errorCode the Cyface error code
     * @param httpCode the HTTP error returned by the server
     */
    public static void sendErrorIntent(final Context context, final int errorCode, final int httpCode,
            final String message) {
        final Intent intent = new Intent(ERROR_INTENT);
        intent.putExtra(HTTP_CODE_EXTRA, httpCode);
        intent.putExtra(ERROR_CODE_EXTRA, errorCode);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        Log.d(TAG, String.format("ErrorHandler (err %s, httpErr %s): %s", errorCode, httpCode, message));
    }

    /**
     * Informs listeners, e.g. a SDK implementing app, about errors.
     *
     * @param context the {@link Context}
     * @param errorCode the Cyface error code
     */
    public static void sendErrorIntent(final Context context, final int errorCode, @Nullable final String message) {
        final Intent intent = new Intent(ERROR_INTENT);
        intent.putExtra(ERROR_CODE_EXTRA, errorCode);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        Log.d(TAG, message);
        //Log.d(TAG, (message != null && message.length() > 0 ? message : "no error message provide"));
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        Validate.notNull(intent.getExtras());
        final int errorCodeInt = intent.getExtras().getInt(ERROR_CODE_EXTRA);
        final ErrorCode errorCode = ErrorCode.getValueForCode(errorCodeInt);
        Validate.notNull(errorCode);
        String errorMessage;
        switch (errorCode) {
            case UNAUTHORIZED:
                errorMessage = context.getString(R.string.error_message_credentials_incorrect);
                break;

            case MALFORMED_URL:
                errorMessage = context.getString(R.string.error_message_server_unavailable);
                break;

            case UNREADABLE_HTTP_RESPONSE:
                errorMessage = context.getString(R.string.error_message_http_response_unreadable);
                break;

            case SERVER_UNAVAILABLE:
                errorMessage = context.getString(R.string.error_message_server_unavailable);
                break;

            case NETWORK_ERROR:
                errorMessage = context.getString(R.string.error_message_unknown_network_error);
                break;

            case DATABASE_ERROR:
                errorMessage = context.getString(R.string.error_message_data_access_error);
                break;

            case AUTHENTICATION_ERROR:
                errorMessage = context.getString(R.string.error_message_unknown_authentication_error);
                break;

            case AUTHENTICATION_CANCELED:
                errorMessage = context.getString(R.string.error_message_authentication_canceled);
                break;

            case SYNCHRONIZATION_ERROR:
                errorMessage = context.getString(R.string.error_message_unknown_sync_error);
                break;

            case DATA_TRANSMISSION_ERROR:
                final int httpCode = intent.getExtras().getInt(HTTP_CODE_EXTRA);
                errorMessage = String
                        .format(context.getString(R.string.error_message_data_transmission_error_with_code), httpCode);
                break;

            case SSL_CERTIFICATE_UNKNOWN:
                errorMessage = context.getString(R.string.error_message_ssl_certificate);
                break;

            case BAD_REQUEST:
                errorMessage = context.getString(R.string.error_message_bad_request);
                break;

            default:
                errorMessage = context.getString(R.string.error_message_unknown_error);
        }

        for (final ErrorListener errorListener : errorListeners) {
            errorListener.onErrorReceive(errorCode, errorMessage);
        }
    }

    /**
     * A list of known Errors which are thrown by the Cyface SDK.
     *
     * @author Armin Schnabel
     * @version 1.1.2
     * @since 1.0.0
     */
    public enum ErrorCode {
        UNKNOWN(0), UNAUTHORIZED(1), MALFORMED_URL(2), UNREADABLE_HTTP_RESPONSE(3), SERVER_UNAVAILABLE(
                4), NETWORK_ERROR(5), DATABASE_ERROR(6), AUTHENTICATION_ERROR(7), AUTHENTICATION_CANCELED(
                        8), SYNCHRONIZATION_ERROR(
                                9), DATA_TRANSMISSION_ERROR(10), SSL_CERTIFICATE_UNKNOWN(11), BAD_REQUEST(12);
        // MEASUREMENT_ENTRY_IS_IRRETRIEVABLE(X),

        private final int code;

        ErrorCode(final int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }

        public static ErrorCode getValueForCode(final int code) {
            for (ErrorCode value : ErrorCode.values()) {
                if (value.getCode() == code) {
                    return value;
                }
            }
            return null;
        }
    }

    /**
     * Interface for listeners receiving errors.
     *
     * @author Armin Schnabel
     * @version 1.0.0
     * @since 1.0.0
     */
    public interface ErrorListener {
        // These enhanced error details will be (re)implemented with #CY-3709
        // @ param causeId Optional id of the cause object for the error, e.g. measurementId
        // @ param causeExtra Optional, additional information such as the date of a broken measurement
        void onErrorReceive(final ErrorCode errorCode, final String errorMessage);
    }

    // The following error handling will be (re)implemented with #CY-3709

    // if (resultExceptionType.equals(MeasurementEntryIsIrretrievableException.class.getSimpleName())) {
    // toastErrorMessage = context.getString(R.string.toast_error_message_unknown_sync_error)
    // + "(" + ErrorCode.MEASUREMENT_ENTRY_IS_IRRETRIEVABLE.ordinal() + ", " + causeId + ", " + causeExtra + ")";

    // if (resultExceptionType.equals(SSLHandshakeException.class.getSimpleName())) {
    // toastErrorMessage = context.getString(R.string.toast_error_message_ssl_certificate);
}
