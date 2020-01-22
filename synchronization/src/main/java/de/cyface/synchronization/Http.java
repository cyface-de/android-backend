/*
 * Copyright 2017 - 2020 Cyface GmbH
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
package de.cyface.synchronization;

import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.SSLContext;

import org.json.JSONObject;

import androidx.annotation.NonNull;

/**
 * An interface for http connections.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 9.0.0
 * @since 3.0.0
 */
interface Http {

    /**
     * The boundary to be used in the Multipart request to separate data.
     */
    String BOUNDARY = "---------------------------boundary";
    /**
     * The string which is used for a line feed.
     */
    String LINE_FEED = "\r\n";

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
    HttpURLConnection openHttpConnection(@NonNull URL url, @NonNull SSLContext sslContext, boolean hasBinaryContent,
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
    HttpURLConnection openHttpConnection(@NonNull URL url, @NonNull SSLContext sslContext, boolean hasBinaryContent)
            throws ServerUnavailableException;

    /**
     * The compressed post request which transmits a measurement batch through an existing http
     * connection
     *
     * @param connection The {@code HttpURLConnection} to be used for the request.
     * @param payload The measurement batch in json format
     * @param compress True if the {@param payload} should get compressed
     * @throws SynchronisationException If an IOException occurred while reading the response code.
     * @throws BadRequestException When server returns {@code HttpURLConnection#HTTP_BAD_REQUEST}
     * @throws UnauthorizedException When the server returns {@code HttpURLConnection#HTTP_UNAUTHORIZED}
     * @throws ForbiddenException When the server returns {@code HttpURLConnection#HTTP_FORBIDDEN}
     * @throws ConflictException When the server returns {@code HttpURLConnection#HTTP_CONFLICT}
     * @throws EntityNotParsableException When the server returns {@link HttpConnection#HTTP_ENTITY_NOT_PROCESSABLE}
     * @throws InternalServerErrorException When the server returns {@code HttpURLConnection#HTTP_INTERNAL_ERROR}
     * @throws NetworkUnavailableException When the network used for transmission becomes unavailable.
     * @throws TooManyRequestsException When the server returns {@link HttpConnection#HTTP_TOO_MANY_REQUESTS}
     */
    @NonNull
    HttpResponse post(@NonNull final HttpURLConnection connection, @NonNull final JSONObject payload,
            final boolean compress)
            throws SynchronisationException, UnauthorizedException, BadRequestException, InternalServerErrorException,
            ForbiddenException, EntityNotParsableException, ConflictException, NetworkUnavailableException,
            TooManyRequestsException;

    /**
     * The serialized post request which transmits a measurement through an existing http connection
     *
     * @param connection The {@code HttpURLConnection} to be used for the request.
     * @param metaData The {@link SyncAdapter.MetaData} required for the Multipart request.
     * @param progressListener The {@link UploadProgressListener} to be informed about the upload progress.
     * @param files The data files to upload via this post request. Currently these should be a sensor data file and an
     *            events data file.
     * @throws SynchronisationException If an IOException occurred during synchronization.
     * @throws BadRequestException When server returns {@code HttpURLConnection#HTTP_BAD_REQUEST}
     * @throws UnauthorizedException When the server returns {@code HttpURLConnection#HTTP_UNAUTHORIZED}
     * @throws ForbiddenException When the server returns {@code HttpURLConnection#HTTP_FORBIDDEN}
     * @throws ConflictException When the server returns {@code HttpURLConnection#HTTP_CONFLICT}
     * @throws EntityNotParsableException When the server returns {@link HttpConnection#HTTP_ENTITY_NOT_PROCESSABLE}
     * @throws InternalServerErrorException When the server returns {@code HttpURLConnection#HTTP_INTERNAL_ERROR}
     * @throws NetworkUnavailableException When the network used for transmission becomes unavailable.
     * @throws SynchronizationInterruptedException When the transmission stream ended too early, likely because the sync
     *             thread was interrupted (sync canceled)
     * @throws TooManyRequestsException When the server returns {@link HttpConnection#HTTP_TOO_MANY_REQUESTS}
     */
    @SuppressWarnings("UnusedReturnValue") // May be used in the future
    @NonNull
    HttpResponse post(@NonNull HttpURLConnection connection, @NonNull SyncAdapter.MetaData metaData,
            @NonNull final UploadProgressListener progressListener, @NonNull FilePart... files)
            throws SynchronisationException, BadRequestException, UnauthorizedException, InternalServerErrorException,
            ForbiddenException, EntityNotParsableException, ConflictException, NetworkUnavailableException,
            SynchronizationInterruptedException, TooManyRequestsException;
}
