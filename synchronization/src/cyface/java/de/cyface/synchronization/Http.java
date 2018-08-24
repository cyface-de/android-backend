package de.cyface.synchronization;

import java.net.HttpURLConnection;
import java.net.URL;

import android.support.annotation.NonNull;

/**
 * An interface for http connections.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.0
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
     * @param jwtBearer A String in the format "Bearer TOKEN".
     * @return the HTTPURLConnection
     * @throws ServerUnavailableException When there seems to be no server at the given URL.
     */
    HttpURLConnection openHttpConnection(@NonNull URL url, @NonNull String jwtBearer) throws ServerUnavailableException;

    /**
     * A HTTPConnection must be opened with the right header before you can communicate with the Cyface REST API
     *
     * @param url The URL of the cyface backend's REST API.
     * @return the HTTPURLConnection
     * @throws ServerUnavailableException When there seems to be no server at the given URL.
     */
    HttpURLConnection openHttpConnection(@NonNull URL url) throws ServerUnavailableException;

    /**
     * The compressed post request which transmits a measurement batch through an existing http
     * connection
     *
     * @param payload The measurement batch in json format
     * @param <T> Json string
     * @throws DataTransmissionException When the server returned a non-successful status code.
     * @throws RequestParsingException When the request could not be generated.
     * @throws ServerUnavailableException When there seems to be no server at the given URL.
     * @throws ResponseParsingException When the http response could not be parsed.
     * @throws SynchronisationException When the new data output for the http connection failed to be created.
     * @throws UnauthorizedException If the credentials for the cyface server are wrong.
     */
    <T> HttpResponse post(HttpURLConnection con, T payload, boolean compress)
            throws DataTransmissionException, RequestParsingException, ServerUnavailableException,
            ResponseParsingException, SynchronisationException, UnauthorizedException;
}
