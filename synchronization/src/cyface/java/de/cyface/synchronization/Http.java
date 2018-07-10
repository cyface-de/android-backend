package de.cyface.synchronization;

import android.support.annotation.NonNull;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

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
     * @param url       The URL of the cyface backend's REST API.
     * @param jwtBearer A String in the format "Bearer TOKEN".
     * @return the HTTPURLConnection
     * @throws DataTransmissionException when no server is at that URL
     */
    HttpURLConnection openHttpConnection(@NonNull URL url, @NonNull String jwtBearer)
            throws DataTransmissionException;

    /**
     * A HTTPConnection must be opened with the right header before you can communicate with the Cyface REST API
     *
     * @param url       The URL of the cyface backend's REST API.
     * @return the HTTPURLConnection
     * @throws DataTransmissionException when no server is at that URL
     */
    HttpURLConnection openHttpConnection(@NonNull URL url)
            throws DataTransmissionException;

    /**
     * The compressed post request which transmits a measurement batch through an existing http
     * connection
     *
     * @param payload The measurement batch in json format
     * @param <T>     Json string
     * @throws DataTransmissionException When the server is not reachable or the connection was
     *                                   interrupted.
     * @throws SynchronisationException  If the system is unable to handle the HTTP response.
     */
    <T> HttpResponse post(HttpURLConnection con, T payload, boolean compress)
            throws DataTransmissionException, SynchronisationException;
}
