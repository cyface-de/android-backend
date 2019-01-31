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
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

import android.content.Context;
import android.content.SyncResult;
import android.util.Log;
import androidx.annotation.NonNull;

/**
 * Performs the actual synchronisation with a provided server, by uploading meta data and a file containing
 * measurements.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.6
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
     * @param data The data to transmit
     * @param jwtAuthToken A valid JWT auth token to authenticate the transmission
     * @return True of the transmission was successful.
     *
     * @throws RequestParsingException When the post request could not be generated or when data could not be parsed
     *             from the measurement slice.
     */
    boolean sendData(final Http http, final SyncResult syncResult, final @NonNull String dataServerUrl,
            final long measurementIdentifier, final @NonNull String deviceIdentifier, final @NonNull InputStream data,
            final @NonNull UploadProgressListener progressListener, final @NonNull String jwtAuthToken)
            throws RequestParsingException, BadRequestException {
        HttpsURLConnection.setFollowRedirects(false);
        HttpsURLConnection connection = null;
        final String fileName = String.format(Locale.US, "%s_%d.cyf", deviceIdentifier, measurementIdentifier);

        try {
            final URL url = new URL(String.format("%s/measurements", dataServerUrl));
            Log.i(TAG, String.format(Locale.GERMAN, "Uploading %s to %s", fileName, url.toString()));
            try {
                connection = http.openHttpConnection(url, sslContext, true, jwtAuthToken);
                http.post(connection, data, deviceIdentifier, measurementIdentifier, fileName, progressListener);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        } catch (final ServerUnavailableException e) {
            // The SyncResults come from Android and help the SyncAdapter to re-schedule the sync
            syncResult.stats.numAuthExceptions++;
            sendErrorIntent(context, SERVER_UNAVAILABLE.getCode(), e.getMessage());
            return false;
        } catch (final MalformedURLException e) {
            syncResult.stats.numParseExceptions++;
            sendErrorIntent(context, MALFORMED_URL.getCode(), e.getMessage());
            return false;
        } catch (final ResponseParsingException e) {
            syncResult.stats.numParseExceptions++;
            sendErrorIntent(context, UNREADABLE_HTTP_RESPONSE.getCode(), e.getMessage());
            return false;
        } catch (final DataTransmissionException e) {
            syncResult.stats.numIoExceptions++;
            sendErrorIntent(context, DATA_TRANSMISSION_ERROR.getCode(), e.getHttpStatusCode(), e.getMessage());
            return false;
        } catch (final SynchronisationException e) {
            syncResult.stats.numParseExceptions++;
            sendErrorIntent(context, SYNCHRONIZATION_ERROR.getCode(), e.getMessage());
            return false;
        } catch (final UnauthorizedException e) {
            syncResult.stats.numAuthExceptions++;
            sendErrorIntent(context, UNAUTHORIZED.getCode(), e.getMessage());
            return false;
        }
        return true;
    }
}
