/*
 * Copyright 2017-2021 Cyface GmbH
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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;

import org.json.JSONObject;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;

import android.util.Log;

import androidx.annotation.NonNull;

import de.cyface.model.RequestMetaData;
import de.cyface.synchronization.exception.AccountNotActivated;
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
import de.cyface.synchronization.exception.UnexpectedResponseCode;
import de.cyface.synchronization.exception.UploadSessionExpired;

/**
 * Implements the {@link Http} connection interface for the Cyface apps.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 12.1.0
 * @since 2.0.0
 */
public class HttpConnection implements Http {

    /**
     * A String to filter log output from {@link HttpConnection} logs.
     */
    final static String TAG = "de.cyface.sync.http";
    /**
     * The file extension of the measurement file which is transmitted on synchronization.
     */
    @SuppressWarnings("SpellCheckingInspection")
    private static final String TRANSFER_FILE_EXTENSION = "ccyf";
    /**
     * The charset used to parse Strings (e.g. for JSON data)
     */
    private final static String DEFAULT_CHARSET = "UTF-8";
    /**
     * The status code returned when the MultiPart request is erroneous, e.g. when there is not exactly onf file or a
     * syntax error.
     */
    final static int HTTP_ENTITY_NOT_PROCESSABLE = 422;
    /**
     * The status code returned when the server responded that the user account is not activated.
     */
    final static int ACCOUNT_NOT_ACTIVATED = 428;
    /**
     * The status code returned when the server thinks that this client sent too many requests in to short time.
     * This helps to prevent DDoS attacks. The client should just retry a short time later.
     */
    final static int HTTP_TOO_MANY_REQUESTS = 429;
    private final static int MB_FROM_MEDIA_HTTP_UPLOADER = 0x100000;
    /**
     * With a sensor frequency of 100 Hz this supports Measurements up to ~ 44 hours.
     */
    private final static int MAX_CHUNK_SIZE = 100 * MB_FROM_MEDIA_HTTP_UPLOADER;
    /**
     * Http code which indicates that the upload intended by the client should be skipped.
     * <p>
     * The server is not interested in the data, e.g. or missing location data or data from a location of no interest.
     */
    private static final int SKIP_UPLOAD = 412;
    /**
     * Http code which indicates that one of the upload requests contained a too large payload.
     * <p>
     * This should not happen as the client checks the file size and skips too large measurement
     * and the pre-request only contains meta data and, thus, should be very small (< 1 KB).
     */
    private static final int PAYLOAD_TOO_LARGE = 413;

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
            NetworkUnavailableException, TooManyRequestsException, HostUnresolvable, ServerUnavailableException, UnexpectedResponseCode, AccountNotActivated {

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
            ServerUnavailableException, MeasurementTooLarge, UploadSessionExpired, UnexpectedResponseCode, AccountNotActivated {

        try {
            final String fileName = String.format(Locale.US, "%s_%s." + TRANSFER_FILE_EXTENSION,
                    metaData.getDeviceIdentifier(), metaData.getMeasurementIdentifier());
            Log.i(TAG, String.format(Locale.GERMAN, "Uploading %s to %s", fileName, url.toString()));
            final String jwtBearer = "Bearer " + jwtToken;

            // Uploader
            final InputStreamContent mediaContent = new InputStreamContent("application/octet-stream",
                    new BufferedInputStream(new FileInputStream(file)));
            mediaContent.setLength(file.length());
            Log.d(Constants.TAG, String.format("mediaContent.length: %s", mediaContent.getLength()));
            final NetHttpTransport transport = new NetHttpTransport(); // Use Builder to modify behaviour
            final RequestInitializeHandler httpRequestInitializer = new RequestInitializeHandler(metaData, jwtBearer);
            final MediaHttpUploader uploader = new MediaHttpUploader(mediaContent, transport, httpRequestInitializer);

            // We currently cannot merge multiple upload-chunk requests into one file on server side.
            // Thus, we prevent slicing the file into multiple files by increasing the chunk size.
            // If the file is larger sync would be successful but only the 1st chunk received DAT-730.
            // i.e. we throw an exception (which skips the upload) for too large measurements (44h+).
            uploader.setChunkSize(MAX_CHUNK_SIZE);
            if (file.length() > MAX_CHUNK_SIZE) {
                throw new MeasurementTooLarge(String.format("Transfer file is too large: %d", file.length()));
            }

            // Add meta data to PreRequest
            final GsonFactory jsonFactory = new GsonFactory();
            final Map<String, String> preRequestBody = HttpConnection.preRequestBody(metaData);
            uploader.setMetadata(new JsonHttpContent(jsonFactory, preRequestBody));

            // Vert.X currently only supports compressing "down-stream" out of the box
            uploader.setDisableGZipContent(true);

            // Progress
            uploader.setProgressListener(new ProgressHandler(progressListener));

            // Upload
            final GenericUrl requestUrl = new GenericUrl(url);
            final com.google.api.client.http.HttpResponse response = uploader.upload(requestUrl);
            try {
                return readResponse(response, jsonFactory);
            } finally {
                response.disconnect();
            }
        } catch (final SocketTimeoutException e) {
            // Happened on emulator when endpoint is local network instead of 10.0.2.2 [DAT-727]
            throw new ServerUnavailableException(e);
        } catch (final SSLException e) {
            Log.w(TAG, "Caught SSLException: " + e.getMessage());
            // Thrown by OkHttp when the network is no longer available [DAT-740]
            final String message = e.getMessage();
            if (message != null && message.contains("I/O error during system call, Broken pipe")) {
                throw new NetworkUnavailableException("Network became unavailable during upload.");
            }
            // SSLException with unknown cause [MOV-774]
            throw new SynchronisationException(e);
        } catch (final InterruptedIOException e) {
            Log.w(TAG, "Caught InterruptedIOException: " + e.getMessage());
            final String message = e.getMessage();
            if (message != null && message.contains("thread interrupted")) {
                // Request interrupted [DAT-741]
                throw new NetworkUnavailableException("Network interrupted during upload", e);
            }
            throw new SynchronisationException(e);
        } catch (final IOException e) {
            Log.w(TAG, "Caught IOException: " + e.getMessage());
            final String message = e.getMessage();
            if (message != null && message.contains("unexpected end of stream")) {
                // Unstable WiFi connection [DAT-742]
                throw new SynchronizationInterruptedException("Upload interrupted", e);
            }
            // IOException with unknown cause [MOV-778]
            throw new SynchronisationException(e);
        }
    }

    static class ProgressHandler implements MediaHttpUploaderProgressListener {

        private final UploadProgressListener progressListener;

        public ProgressHandler(final UploadProgressListener progressListener) {
            this.progressListener = progressListener;
        }

        @Override
        public void progressChanged(MediaHttpUploader uploader) throws IOException {
            Log.d(Constants.TAG, String.format("progress: %s, uploaded: %s Bytes", uploader.getProgress(),
                    uploader.getNumBytesUploaded()));
            progressListener.updatedProgress((float)uploader.getProgress());
        }
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
     * Assembles a {@code HttpContent} object which contains the metadata.
     *
     * @param metaData The metadata to convert.
     * @return The meta data as {@code HttpContent}.
     */
    static Map<String, String> preRequestBody(final RequestMetaData metaData) {
        final Map<String, String> attributes = new HashMap<>();

        // Location meta data
        if (metaData.getStartLocation() != null) {
            attributes.put("startLocLat", String.valueOf(metaData.getStartLocation().getLatitude()));
            attributes.put("startLocLon", String.valueOf(metaData.getStartLocation().getLongitude()));
            attributes.put("startLocTS", String.valueOf(metaData.getStartLocation().getTimestamp()));
        }
        if (metaData.getEndLocation() != null) {
            attributes.put("endLocLat", String.valueOf(metaData.getEndLocation().getLatitude()));
            attributes.put("endLocLon", String.valueOf(metaData.getEndLocation().getLongitude()));
            attributes.put("endLocTS", String.valueOf(metaData.getEndLocation().getTimestamp()));
        }
        attributes.put("locationCount", String.valueOf(metaData.getLocationCount()));

        // Remaining meta data
        attributes.put("deviceId", metaData.getDeviceIdentifier());
        attributes.put("measurementId", metaData.getMeasurementIdentifier());
        attributes.put("deviceType", metaData.getDeviceType());
        attributes.put("osVersion", metaData.getOperatingSystemVersion());
        attributes.put("appVersion", metaData.getApplicationVersion());
        attributes.put("length", String.valueOf(metaData.getLength()));
        // To support the API specification we may not change the "vehicle" key name of the modality
        attributes.put("modality", String.valueOf(metaData.getModality()));
        attributes.put("formatVersion", String.valueOf(metaData.getFormatVersion()));

        return attributes;
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
                return handleSuccess(new HttpResponse(responseCode, responseBody, responseMessage));
            }
            return handleError(new HttpResponse(responseCode, responseBody, responseMessage));
        } catch (final IOException e) {
            throw new SynchronisationException(e);
        }
    }

    @NonNull
    private Result readResponse(com.google.api.client.http.HttpResponse response, JsonFactory jsonFactory)
            throws BadRequestException, UnauthorizedException, ForbiddenException,
            ConflictException, EntityNotParsableException, InternalServerErrorException, TooManyRequestsException,
            SynchronisationException, UploadSessionExpired, UnexpectedResponseCode, AccountNotActivated {

        // Read response from connection
        final int responseCode = response.getStatusCode();
        final String responseMessage = response.getStatusMessage();
        final String responseBody;
        try {
            responseBody = readResponseBody(response, jsonFactory);
            return handleSuccess(new HttpResponse(responseCode, responseBody, responseMessage));
        } catch (GoogleJsonResponseException e) {
            final GoogleJsonError details = e.getDetails();
            if (details != null) {
                return handleError(new HttpResponse(responseCode, details.getMessage(), responseMessage));
            }
            // TODO: Our server should only add JSON bodies to error responses or else there is no
            // way to read the error body with the Google API client library
            return handleError(new HttpResponse(responseCode, e.toString(), responseMessage));
        }
    }

    private Result handleSuccess(HttpResponse response) {

        // Handle known success responses
        final int responseCode = response.getResponseCode();
        switch (responseCode) {
            // Login Requests
            case HttpURLConnection.HTTP_OK:
                Log.d(TAG, "200: Login successful");
                return Result.LOGIN_SUCCESSFUL;
            case HttpURLConnection.HTTP_CREATED:
                Log.d(TAG, "201: Upload successful");
                return Result.UPLOAD_SUCCESSFUL;
        }

        // Known response
        throw new IllegalStateException("Unknown success code: " + responseCode);
    }

    private Result handleError(HttpResponse response)
            throws BadRequestException, UnauthorizedException, ForbiddenException, ConflictException,
            EntityNotParsableException, InternalServerErrorException, TooManyRequestsException, UploadSessionExpired,
            UnexpectedResponseCode, AccountNotActivated {

        // Handle known error responses
        final int responseCode = response.getResponseCode();
        switch (responseCode) {
            case HttpURLConnection.HTTP_BAD_REQUEST:
                Log.w(TAG, "400: Unknown error");
                throw new BadRequestException(response.getBody());
            case HttpURLConnection.HTTP_UNAUTHORIZED:
                Log.w(TAG, "401: Bad credentials or missing authorization information");
                throw new UnauthorizedException(response.getBody());
            case HttpURLConnection.HTTP_FORBIDDEN:
                Log.w(TAG, "403: The authorized user has no permissions to post measurements");
                throw new ForbiddenException(response.getBody());
            case HttpURLConnection.HTTP_NOT_FOUND:
                // This code is thrown if the upload is expired. Client should restart upload.
                Log.w(TAG, "404: Did the upload session expire? Try again.");
                throw new UploadSessionExpired(response.getBody());
            case HttpURLConnection.HTTP_CONFLICT:
                Log.w(TAG, "409: The measurement already exists on the server.");
                throw new ConflictException(response.getBody());
            case SKIP_UPLOAD:
                Log.i(TAG, "412: Skip upload");
                return Result.UPLOAD_SKIPPED;
            case PAYLOAD_TOO_LARGE:
                Log.w(TAG, "413: Payload too large");
                // We currently don't officially support this error code.
                // - server returns 412 (not interested) if pre-request `content-length` too large
                // - server returns 413 (payload too large) if pre-request meta-data body too large
                // - server returns 413 (payload too large) if upload-request `content-length` or body
                // is too large, which should not happen when the client respects the `412`
                // (not interested) returned by the pre-request when `content-length` is set correctly.
                // If we do support this error, the server needs to add a JSON body to the error response
                // which lets us differentiate which of the cases in of `413` happened.
                throw new IllegalStateException(response.getBody());
            case HTTP_ENTITY_NOT_PROCESSABLE:
                Log.w(TAG, "422: Multipart request is erroneous.");
                throw new EntityNotParsableException(response.getBody());
            case ACCOUNT_NOT_ACTIVATED:
                Log.w(TAG, "428: User account not activated.");
                throw new AccountNotActivated(response.getBody());
            case HTTP_TOO_MANY_REQUESTS:
                Log.w(TAG, "429: Server reported too many requests received from this client.");
                throw new TooManyRequestsException(response.getBody());
            case HttpURLConnection.HTTP_INTERNAL_ERROR:
                Log.w(TAG, "500: Server reported internal error.");
                throw new InternalServerErrorException(response.getBody());
            default:
                Log.e(TAG, String.format("%d: Server reported with an unexpected error code.", responseCode));
                throw new UnexpectedResponseCode(response.getBody());
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
            return readInputStream(connection.getInputStream());
        } catch (final IOException e) {

            // When reading the InputStream fails, we check if there is an ErrorStream to read from
            // (For details see https://developer.android.com/reference/java/net/HttpURLConnection)
            final InputStream errorStream = connection.getErrorStream();

            // Return empty string if there were no errors, connection is not connected or server sent no useful data.
            // This occurred e.g. on Xiaomi Mi A1 after disabling WiFi instantly after sync start
            if (errorStream == null) {
                return "";
            }

            return readInputStream(errorStream);
        }
    }

    /**
     * Reads the body from the {@code com.google.api.client.http.HttpResponse}.
     * <p>
     * This contains either the error or the success message.
     *
     * @param response the {@code HttpResponse} to read the body from
     * @param jsonFactory the {@code Factory} to be used to parse the response if it's JSON
     * @return the {@link HttpResponse} body
     * @throws GoogleJsonResponseException if the response does not contain a success status code
     * @throws SynchronisationException if an {@code IOException} occurred while reading the response
     */
    private String readResponseBody(final com.google.api.client.http.HttpResponse response,
            final JsonFactory jsonFactory) throws GoogleJsonResponseException, SynchronisationException {

        // See `uploader.upload`: Handle error parsing correctly
        if (!response.isSuccessStatusCode()) {
            throw GoogleJsonResponseException.from(jsonFactory, response);
        }

        // Read success response body
        try (final InputStream inputStream = response.getContent()) {
            return readInputStream(inputStream);
        } catch (final IOException e) {
            // No errors, connection is not connected or server sent no useful data.
            // Unsure if this happens with the new 2021 protocol, thus, throwing an exception.
            throw new SynchronisationException(e);
        }
    }

    /**
     * Extracts the String from the provided {@code InputStream}.
     *
     * @param inputStream the {@code InputStream} to read from
     * @return the {@link String} read from the InputStream. If an I/O error occurs while reading fro the stream, the
     *         already read string is returned which might my empty or cut short.
     */
    @NonNull
    private String readInputStream(@NonNull final InputStream inputStream) {

        try {
            try (final BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(inputStream, DEFAULT_CHARSET))) {
                final StringBuilder responseString = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    responseString.append(line);
                }
                return responseString.toString();
            } catch (final UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Result returned by this class' public methods, to inform callers how to proceed.
     *
     * @author Armin Schnabel
     * @version 1.0.0
     * @since 7.0.0
     */
    enum Result {
        UPLOAD_SUCCESSFUL, UPLOAD_SKIPPED, UPLOAD_FAILED, LOGIN_SUCCESSFUL
    }
}