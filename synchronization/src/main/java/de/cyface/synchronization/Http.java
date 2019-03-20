/*
 * Copyright 2017 Cyface GmbH
 * This file is part of the Cyface SDK for Android.
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.synchronization;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

import org.json.JSONObject;

import android.accounts.NetworkErrorException;

import androidx.annotation.NonNull;

/**
 * An interface for http connections.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 4.0.0
 * @since 3.0.0
 */
interface Http {

    /**
     * Adds a trailing slash to the server URL or leaves an existing trailing slash untouched.
     *
     * @param url The url to format.
     * @return The server URL with a trailing slash.
     */
    @NonNull
    String returnUrlWithTrailingSlash(@NonNull String url);

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
    @NonNull
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
    @NonNull
    HttpsURLConnection openHttpConnection(@NonNull URL url, @NonNull SSLContext sslContext, boolean hasBinaryContent)
            throws ServerUnavailableException;

    /**
     * The compressed post request which transmits a measurement batch through an existing http
     * connection
     *
     * @param connection The {@code HttpURLConnection} to be used for the request.
     * @param payload The measurement batch in json format
     * @param compress True if the {@param payload} should get compressed
     * @throws RequestParsingException When the request could not be generated.
     * @throws DataTransmissionException When the server returned a non-successful status code.
     * @throws SynchronisationException When the new data output for the http connection failed to be created.
     * @throws ResponseParsingException When the http response could not be parsed.
     * @throws UnauthorizedException When the server returns {@code HttpURLConnection#HTTP_UNAUTHORIZED}
     * @throws BadRequestException When server returns {@code HttpURLConnection#HTTP_BAD_REQUEST}
     * @throws NetworkErrorException when the connection's input or error stream was null
     */
    @NonNull
    HttpResponse post(@NonNull final HttpURLConnection connection, @NonNull final JSONObject payload,
            final boolean compress) throws RequestParsingException, DataTransmissionException, SynchronisationException,
            ResponseParsingException, UnauthorizedException, BadRequestException, NetworkErrorException;

    /**
     * The serialized post request which transmits a measurement through an existing http connection
     *
     * @param connection The {@code HttpURLConnection} to be used for the request.
     * @param transferTempFile The data to transmit
     * @param metaData The {@link SyncAdapter.MetaData} required for the Multipart request.
     * @param fileName The name of the file to be uploaded
     * @param progressListener The {@link UploadProgressListener} to be informed about the upload progress.
     * @throws SynchronisationException When the new data output for the http connection failed to be created.
     * @throws ResponseParsingException When the http response could not be parsed.
     * @throws BadRequestException When server returns {@code HttpURLConnection#HTTP_BAD_REQUEST}
     * @throws UnauthorizedException When the server returns {@code HttpURLConnection#HTTP_UNAUTHORIZED}
     */
    @SuppressWarnings("UnusedReturnValue") // May be used in the future
    @NonNull
    HttpResponse post(@NonNull final HttpURLConnection connection, @NonNull final File transferTempFile,
            @NonNull final SyncAdapter.MetaData metaData, @NonNull final String fileName,
            @NonNull final UploadProgressListener progressListener)
            throws SynchronisationException, ResponseParsingException, BadRequestException, UnauthorizedException;
}
