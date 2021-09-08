/*
 * Copyright 2017 - 2021 Cyface GmbH
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

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONObject;

import androidx.annotation.NonNull;

import de.cyface.synchronization.exception.BadRequestException;
import de.cyface.synchronization.exception.ConflictException;
import de.cyface.synchronization.exception.EntityNotParsableException;
import de.cyface.synchronization.exception.ForbiddenException;
import de.cyface.synchronization.exception.HostUnresolvable;
import de.cyface.synchronization.exception.InternalServerErrorException;
import de.cyface.synchronization.exception.MeasurementTooLarge;
import de.cyface.synchronization.exception.NetworkUnavailableException;
import de.cyface.synchronization.exception.ServerUnavailableException;
import de.cyface.synchronization.exception.SynchronisationException;
import de.cyface.synchronization.exception.SynchronizationInterruptedException;
import de.cyface.synchronization.exception.TooManyRequestsException;
import de.cyface.synchronization.exception.UnauthorizedException;

/**
 * An interface for http connections.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 10.0.0
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
     * @param hasBinaryContent True if binary content is to be transmitted
     * @param jwtBearer A String in the format "Bearer TOKEN".
     * @return the HTTPURLConnection
     * @throws SynchronisationException When the connection object could not be prepared
     */
    @NonNull
    HttpURLConnection open(@NonNull URL url, boolean hasBinaryContent, @NonNull String jwtBearer)
            throws SynchronisationException;

    /**
     * A HTTPConnection must be opened with the right header before you can communicate with the Cyface REST API
     *
     * @param url The URL of the cyface backend's REST API.
     * @param hasBinaryContent True if binary content is to be transmitted
     * @return the HTTPURLConnection
     * @throws SynchronisationException When the connection object could not be prepared
     */
    @NonNull
    HttpURLConnection open(@NonNull URL url, boolean hasBinaryContent) throws SynchronisationException;

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
     * @throws HostUnresolvable e.g. when the phone is connected to a network which is not connected to the internet
     * @throws ServerUnavailableException When no connection could be established with the server
     * @return {@code HttpConnection.Result.LOGIN_SUCCESSFUL} if successful or else an {@code Exception}.
     */
    @NonNull
    HttpConnection.Result login(@NonNull final HttpURLConnection connection, @NonNull final JSONObject payload,
            final boolean compress)
            throws SynchronisationException, UnauthorizedException, BadRequestException, InternalServerErrorException,
            ForbiddenException, EntityNotParsableException, ConflictException, NetworkUnavailableException,
            TooManyRequestsException, HostUnresolvable, ServerUnavailableException;

    /**
     * The serialized post request which transmits a measurement through an existing http connection
     *
     * @param url the resource to upload the data to
     * @param jwtBearer A String in the format "Bearer TOKEN".
     * @param metaData The {@link SyncAdapter.MetaData} required for the Multipart request.
     * @param progressListener The {@link UploadProgressListener} to be informed about the upload progress.
     * @param file The data file to upload via this post request.
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
     * @throws HostUnresolvable e.g. when the phone is connected to a network which is not connected to the internet
     * @throws ServerUnavailableException When no connection could be established with the server
     * @throws MeasurementTooLarge When the transfer file is too large to be uploaded.
     * @return {@code HttpConnection.Result.UPLOAD_SUCCESSFUL} on success, {@code HttpConnection.Result.UPLOAD_FAILED}
     *         when the upload attempt should be repeated or {@code HttpConnection.Result.UPLOAD_SKIPPED} if the server
     *         is not interested in the data.
     */
    HttpConnection.Result upload(final URL url, final String jwtBearer, final SyncAdapter.MetaData metaData,
            final File file, final UploadProgressListener progressListener)
            throws SynchronisationException, BadRequestException, UnauthorizedException, InternalServerErrorException,
            ForbiddenException, EntityNotParsableException, ConflictException, NetworkUnavailableException,
            SynchronizationInterruptedException, TooManyRequestsException, HostUnresolvable, ServerUnavailableException,
            MeasurementTooLarge;
}