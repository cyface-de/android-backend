package de.cyface.synchronization;

import static de.cyface.synchronization.Constants.TAG;
import static de.cyface.synchronization.CyfaceAuthenticator.initSslContext;
import static de.cyface.utils.ErrorHandler.sendErrorIntent;
import static de.cyface.utils.ErrorHandler.ErrorCode.DATA_TRANSMISSION_ERROR;
import static de.cyface.utils.ErrorHandler.ErrorCode.MALFORMED_URL;
import static de.cyface.utils.ErrorHandler.ErrorCode.SERVER_UNAVAILABLE;
import static de.cyface.utils.ErrorHandler.ErrorCode.SYNCHRONIZATION_ERROR;
import static de.cyface.utils.ErrorHandler.ErrorCode.UNAUTHORIZED;
import static de.cyface.utils.ErrorHandler.ErrorCode.UNREADABLE_HTTP_RESPONSE;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

import javax.net.ssl.SSLContext;

import org.json.JSONObject;

import android.content.Context;
import android.content.SyncResult;
import android.support.annotation.NonNull;
import android.util.Log;

/**
 * Performs the actual synchronisation with a provided server, by uploading meta data and a file containing
 * measurements.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.0
 * @since 2.0.0
 */
class SyncPerformer {

    /**
     * Socket Factory required to communicate with the Cyface Server when using a self signed certificate issued by that
     * server. Further details are available in the
     * <a href="https://developer.android.com/training/articles/security-ssl.html#UnknownCa">Android documentation</a>
     * and for example <a href=
     * "https://stackoverflow.com/questions/24555890/using-a-custom-truststore-in-java-as-well-as-the-default-one">here</a>.
     */
    private SSLContext sslContext;
    private Context context;

    /**
     * Creates a new completely initialized <code>SyncPerformer</code> for a given Android <code>Context</code>.
     *
     * @param context The Android <code>Context</code> to use for setting the correct server certification information.
     */
    SyncPerformer(final @NonNull Context context) {
        this.context = context;

        // Load SSLContext
        try {
            sslContext = initSslContext(context);
        } catch (final IOException e) {
            throw new IllegalStateException("Trust store file failed while closing", e);
        } catch (final SynchronisationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Triggers the data transmission to a Cyface server API. The <code>measurementIdentifier</code> and
     * <code>deviceIdentifier</code> need to be globally unique. If they are not the server will probably reject the
     * request.
     * <p>
     * Sync errors are broadcasted to the {@link de.cyface.utils.ErrorHandler}.
     * <p>
     * Since this is a synchronous call it can take from seconds to minutes depending on the size of <code>data</code>.
     * Never call this on the UI thread. Your users are going to hate you.
     *
     * @param http The {@link Http} connection to use for transmission
     * @param syncResult The {@link SyncResult} used to store sync error information.
     * @param dataServerUrl The server URL to send the data to.
     * @param measurementIdentifier The measurement identifier of the transmitted measurement.
     * @param deviceIdentifier The device identifier of the device transmitting the measurement.
     * @param data The data to transmit as JSON measurement slice.
     * @param jwtAuthToken A valid JWT auth token to authenticate the transmission
     * @return True of the transmission was successful.
     *
     * @throws RequestParsingException When the post request could not be generated or when data could not be parsed
     *             from the measurement slice.
     */
    boolean sendData(final Http http, final SyncResult syncResult, final @NonNull String dataServerUrl, final long measurementIdentifier,
                 final @NonNull String deviceIdentifier, final @NonNull JSONObject data,
                 final @NonNull String jwtAuthToken) throws RequestParsingException {
        Log.i(TAG, String.format(Locale.US, "Uploading data from device %s with identifier %s to server %s",
                deviceIdentifier, measurementIdentifier, dataServerUrl));

        try {
            final URL postUrl = new URL(http.returnUrlWithTrailingSlash(dataServerUrl) + "/measurements/");
            HttpURLConnection con = null;
            try {
                con = http.openHttpConnection(postUrl, jwtAuthToken, sslContext);
                Log.d(TAG, "Posing measurement slice ...");
                http.post(con, data, true);
            } finally {
                if (con != null) {
                    con.disconnect();
                }
            }
        } catch (final ServerUnavailableException e) {
            // The SyncResults come from Android and help the SyncAdapter to re-schedule the sync
            syncResult.stats.numAuthExceptions++;
            sendErrorIntent(context, SERVER_UNAVAILABLE.getCode());
            return false;
        } catch (final MalformedURLException e) {
            syncResult.stats.numParseExceptions++;
            sendErrorIntent(context, MALFORMED_URL.getCode());
            return false;
        } catch (final ResponseParsingException e) {
            syncResult.stats.numParseExceptions++;
            sendErrorIntent(context, UNREADABLE_HTTP_RESPONSE.getCode());
            return false;
        } catch (final DataTransmissionException e) {
            syncResult.stats.numIoExceptions++;
            sendErrorIntent(context, DATA_TRANSMISSION_ERROR.getCode(), e.getHttpStatusCode());
            return false;
        } catch (final SynchronisationException e) {
            syncResult.stats.numParseExceptions++;
            sendErrorIntent(context, SYNCHRONIZATION_ERROR.getCode());
            return false;
        } catch (final UnauthorizedException e) {
            syncResult.stats.numAuthExceptions++;
            sendErrorIntent(context, UNAUTHORIZED.getCode());
            return false;
        }
        return true;
    }
}
