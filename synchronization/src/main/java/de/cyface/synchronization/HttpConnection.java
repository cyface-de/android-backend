/*
 * Copyright 2017-2023 Cyface GmbH
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

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;

import org.json.JSONObject;

import android.util.Log;

import androidx.annotation.NonNull;

import de.cyface.model.RequestMetaData;
import de.cyface.synchronization.exception.HostUnresolvable;
import de.cyface.uploader.HttpResponse;
import de.cyface.uploader.Result;
import de.cyface.uploader.UploadProgressListener;
import de.cyface.uploader.Uploader;
import de.cyface.uploader.exception.AccountNotActivated;
import de.cyface.uploader.exception.BadRequestException;
import de.cyface.uploader.exception.ConflictException;
import de.cyface.uploader.exception.EntityNotParsableException;
import de.cyface.uploader.exception.ForbiddenException;
import de.cyface.uploader.exception.InternalServerErrorException;
import de.cyface.uploader.exception.MeasurementTooLarge;
import de.cyface.uploader.exception.NetworkUnavailableException;
import de.cyface.uploader.exception.ServerUnavailableException;
import de.cyface.uploader.exception.SynchronisationException;
import de.cyface.uploader.exception.SynchronizationInterruptedException;
import de.cyface.uploader.exception.TooManyRequestsException;
import de.cyface.uploader.exception.UnauthorizedException;
import de.cyface.uploader.exception.UnexpectedResponseCode;
import de.cyface.uploader.exception.UploadSessionExpired;

/**
 * Implements the {@link Http} connection interface for the Cyface apps.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 13.0.0
 * @since 2.0.0
 */
public class HttpConnection implements Http {

    /**
     * A String to filter log output from {@link HttpConnection} logs.
     */
    final static String TAG = "de.cyface.sync.http";

    @NonNull
    @Override
    public String returnUrlWithTrailingSlash(@NonNull final String url) {

        if (url.endsWith("/")) {
            return url;
        } else {
            return url + "/";
        }
    }

    @NonNull
    @Override
    public HttpURLConnection open(@NonNull final URL url, final boolean hasBinaryContent,
            final @NonNull String jwtToken) throws SynchronisationException {

        final HttpURLConnection connection = open(url, hasBinaryContent);
        connection.setRequestProperty("Authorization", "Bearer " + jwtToken);
        return connection;
    }

    @NonNull
    @Override
    public HttpURLConnection open(@NonNull final URL url, final boolean hasBinaryContent)
            throws SynchronisationException {

        HttpURLConnection connection;
        try {
            connection = (HttpURLConnection)url.openConnection();
        } catch (final IOException e) {
            // openConnection() only prepares, but does not establish an actual network connection
            throw new SynchronisationException(String.format("Error %s. Unable to prepare connection for URL  %s.",
                    e.getMessage(), url), e);
        }

        if (url.getPath().startsWith("https://")) {
            final HttpsURLConnection httpsURLConnection = (HttpsURLConnection)connection;
            // Without verifying the hostname we receive the "Trust Anchor..." Error
            httpsURLConnection.setHostnameVerifier((hostname, session) -> {
                HostnameVerifier hv = HttpsURLConnection.getDefaultHostnameVerifier();
                return hv.verify(url.getHost(), session);
            });
        }

        connection.setRequestProperty("Content-Type", "application/json; charset=" + DEFAULT_CHARSET);
        try {
            connection.setRequestMethod("POST");
        } catch (final ProtocolException e) {
            throw new IllegalStateException(e);
        }
        connection.setRequestProperty("User-Agent", System.getProperty("http.agent"));
        return connection;
    }

    @NonNull
    @Override
    public Result login(@NonNull final HttpURLConnection connection, @NonNull final JSONObject payload,
            final boolean compress)
            throws SynchronisationException, UnauthorizedException, BadRequestException,
            InternalServerErrorException, ForbiddenException, EntityNotParsableException, ConflictException,
            NetworkUnavailableException, TooManyRequestsException, HostUnresolvable, ServerUnavailableException,
            UnexpectedResponseCode, AccountNotActivated {

        // For performance reasons (documentation) set either fixedLength (known length) or chunked streaming mode
        // we currently don't use fixedLengthStreamingMode as we only use this request for small login requests
        connection.setChunkedStreamingMode(0);
        final BufferedOutputStream outputStream = initOutputStream(connection);

        try {
            Log.d(TAG, "Transmitting with compression " + compress + ".");
            if (compress) {
                connection.setRequestProperty("Content-Encoding", "gzip");
                outputStream.write(gzip(payload.toString().getBytes(DEFAULT_CHARSET)));
            } else {
                outputStream.write(payload.toString().getBytes(DEFAULT_CHARSET));
            }
            outputStream.flush();
            outputStream.close();
        } catch (final SSLException e) {
            // This exception is thrown by OkHttp when the network is no longer available
            final String message = e.getMessage();
            if (message != null && message.contains("I/O error during system call, Broken pipe")) {
                Log.w(TAG, "Caught SSLException: " + e.getMessage());
                throw new NetworkUnavailableException("Network became unavailable during transmission.", e);
            } else {
                throw new IllegalStateException(e); // SSLException with unknown cause
            }
        } catch (final InterruptedIOException e) {
            // This exception is thrown when the login request is interrupted, e.g. see MOV-761
            throw new NetworkUnavailableException("Network interrupted during login", e);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }

        try {
            return readResponse(connection);
        } catch (UploadSessionExpired e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Result upload(final URL url, final String jwtToken, final RequestMetaData metaData,
            final File file, final UploadProgressListener progressListener)
            throws SynchronisationException, BadRequestException, UnauthorizedException, InternalServerErrorException,
            ForbiddenException, EntityNotParsableException, ConflictException, NetworkUnavailableException,
            SynchronizationInterruptedException, TooManyRequestsException, HostUnresolvable,
            ServerUnavailableException, MeasurementTooLarge, UploadSessionExpired, UnexpectedResponseCode,
            AccountNotActivated {

        final var cyfaceUploader = new Uploader(url);
        return cyfaceUploader.upload(jwtToken, metaData, file, progressListener);
    }

    private byte[] gzip(byte[] input) {

        try {
            GZIPOutputStream gzipOutputStream = null;
            try {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream);
                try {
                    gzipOutputStream.write(input);
                    gzipOutputStream.flush();
                } finally {
                    gzipOutputStream.close();
                }
                gzipOutputStream = null;
                return byteArrayOutputStream.toByteArray();
            } finally {
                if (gzipOutputStream != null) {
                    gzipOutputStream.close();
                }
            }
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to gzip.");
        }
    }

    /**
     * Initializes a {@code BufferedOutputStream} for the provided connection.
     *
     * @param connection the {@code HttpURLConnection} to create the stream for.
     * @return the {@code BufferedOutputStream} created.
     * @throws ServerUnavailableException When no connection could be established with the server
     * @throws HostUnresolvable e.g. when the phone is connected to a network which is not connected to the internet.
     */
    private BufferedOutputStream initOutputStream(final HttpURLConnection connection)
            throws ServerUnavailableException, HostUnresolvable {

        connection.setDoOutput(true); // To upload data to the server
        try {
            // Wrapping this in a Buffered steam for performance reasons
            return new BufferedOutputStream(connection.getOutputStream());
        } catch (final IOException e) {
            final String message = e.getMessage();
            if (message != null && message.contains("Unable to resolve host")) {
                throw new HostUnresolvable(e);
            }
            // Happened e.g. when Wifi was manually disabled just after synchronization started (Pixel 2 XL).
            // Or when the hostname is not verified (e.g. typo in sub-domain part)
            throw new ServerUnavailableException(e);
        }
    }

    /**
     * Reads the {@link HttpResponse} from the {@link HttpURLConnection} and identifies known errors.
     *
     * @param connection The connection that received the response.
     * @return The {@link HttpResponse}.
     * @throws SynchronisationException If an IOException occurred while reading the response code.
     * @throws BadRequestException When server returns {@code HttpURLConnection#HTTP_BAD_REQUEST}
     * @throws UnauthorizedException When the server returns {@code HttpURLConnection#HTTP_UNAUTHORIZED}
     * @throws ForbiddenException When the server returns {@code HttpURLConnection#HTTP_FORBIDDEN}
     * @throws ConflictException When the server returns {@code HttpURLConnection#HTTP_CONFLICT}
     * @throws EntityNotParsableException When the server returns {@link #HTTP_ENTITY_NOT_PROCESSABLE}
     * @throws InternalServerErrorException When the server returns {@code HttpURLConnection#HTTP_INTERNAL_ERROR}
     * @throws TooManyRequestsException When the server returns {@link #HTTP_TOO_MANY_REQUESTS}
     * @throws UnexpectedResponseCode When the server returns an unexpected response code
     * @throws AccountNotActivated When the user account is not activated
     */
    @NonNull
    private Result readResponse(@NonNull final HttpURLConnection connection)
            throws SynchronisationException, BadRequestException, UnauthorizedException, ForbiddenException,
            ConflictException, EntityNotParsableException, InternalServerErrorException, TooManyRequestsException,
            UploadSessionExpired, UnexpectedResponseCode, AccountNotActivated {

        final int responseCode;
        final String responseMessage;
        try {
            responseCode = connection.getResponseCode();
            responseMessage = connection.getResponseMessage();
            final String responseBody = readResponseBody(connection);

            if (responseCode >= 200 && responseCode < 300) {
                return Uploader.handleSuccess(new HttpResponse(responseCode, responseBody, responseMessage));
            }
            return Uploader.handleError(new HttpResponse(responseCode, responseBody, responseMessage));
        } catch (final IOException e) {
            throw new SynchronisationException(e);
        }
    }

    /**
     * Reads the body from the {@link HttpURLConnection}. This contains either the error or the success message.
     *
     * @param connection the {@link HttpURLConnection} to read the response from
     * @return the {@link HttpResponse} body
     */
    @NonNull
    private String readResponseBody(@NonNull final HttpURLConnection connection) {

        // First try to read and return a success response body
        try {
            return Uploader.readInputStream(connection.getInputStream());
        } catch (final IOException e) {

            // When reading the InputStream fails, we check if there is an ErrorStream to read from
            // (For details see https://developer.android.com/reference/java/net/HttpURLConnection)
            final InputStream errorStream = connection.getErrorStream();

            // Return empty string if there were no errors, connection is not connected or server sent no useful data.
            // This occurred e.g. on Xiaomi Mi A1 after disabling WiFi instantly after sync start
            if (errorStream == null) {
                return "";
            }

            return Uploader.readInputStream(errorStream);
        }
    }
}