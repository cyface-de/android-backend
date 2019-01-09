package de.cyface.synchronization;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

import androidx.annotation.NonNull;

import de.cyface.synchronization.exceptions.BadRequestException;
import de.cyface.synchronization.exceptions.DataTransmissionException;
import de.cyface.synchronization.exceptions.RequestParsingException;
import de.cyface.synchronization.exceptions.ResponseParsingException;
import de.cyface.synchronization.exceptions.ServerUnavailableException;
import de.cyface.synchronization.exceptions.SynchronisationException;
import de.cyface.synchronization.exceptions.UnauthorizedException;

/**
 * An interface for http connections.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.2.1
 * @since 3.0.0
 */
interface Http {
    /**
     * Adds a trailing slash to the server URL or leaves an existing trailing slash untouched.
     *
     * @param url The url to format.
     * @return The server URL with a trailing slash.
     */
    String returnUrlWithTrailingSlash(String url);

    /**
     * A HTTPConnection must be opened with the right header before you can communicate with the Cyface REST API
     *
     * @param url The URL of the cyface backend's REST API.
     * @param sslContext The {@link SSLContext} to open a secure connection to the server
     * @param hasBinaryContent True if binary content is to be transmitted
     * @param jwtBearer A String in the format "Bearer TOKEN".
     * @return the HTTPURLConnection
     * @throws ServerUnavailableException When there seems to be no server at the given URL.
     */
    HttpsURLConnection openHttpConnection(@NonNull URL url, @NonNull SSLContext sslContext, boolean hasBinaryContent,
            @NonNull String jwtBearer) throws ServerUnavailableException;

    /**
     * A HTTPConnection must be opened with the right header before you can communicate with the Cyface REST API
     *
     * @param url The URL of the cyface backend's REST API.
     * @param sslContext The {@link SSLContext} to open a secure connection to the server
     * @param hasBinaryContent True if binary content is to be transmitted
     * @return the HTTPURLConnection
     * @throws ServerUnavailableException When there seems to be no server at the given URL.
     */
    HttpsURLConnection openHttpConnection(@NonNull URL url, SSLContext sslContext, boolean hasBinaryContent)
            throws ServerUnavailableException;

    /**
     * The compressed post request which transmits a measurement batch through an existing http
     * connection
     *
     * @param payload The measurement batch in json format
     * @throws DataTransmissionException When the server returned a non-successful status code.
     * @throws RequestParsingException When the request could not be generated.
     * @throws ResponseParsingException When the http response could not be parsed.
     * @throws SynchronisationException When the new data output for the http connection failed to be created.
     * @throws UnauthorizedException If the credentials for the cyface server are wrong.
     */
    HttpResponse post(HttpURLConnection connection, JSONObject payload, boolean compress)
            throws RequestParsingException, DataTransmissionException, SynchronisationException,
            ResponseParsingException, UnauthorizedException, BadRequestException;

    /**
     * The serialized post request which transmits a measurement through an existing http connection
     *
     * @throws DataTransmissionException When the server returned a non-successful status code.
     * @throws RequestParsingException When the request could not be generated.
     * @throws ResponseParsingException When the http response could not be parsed.
     * @throws SynchronisationException When the new data output for the http connection failed to be created.
     * @throws UnauthorizedException If the credentials for the cyface server are wrong.
     */
    HttpResponse post(@NonNull HttpURLConnection connection, @NonNull InputStream data, @NonNull String deviceId,
            long measurementId, @NonNull String fileName, UploadProgressListener progressListener)
            throws RequestParsingException, DataTransmissionException, SynchronisationException,
            ResponseParsingException, UnauthorizedException, BadRequestException;
}
