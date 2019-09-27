/*
 * Copyright 2017 Cyface GmbH
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

import static de.cyface.persistence.Constants.DEFAULT_CHARSET;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

import org.json.JSONObject;

import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import de.cyface.utils.Validate;

/**
 * Implements the {@link Http} connection interface for the Cyface apps.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 11.0.0
 * @since 2.0.0
 */
public class HttpConnection implements Http {

    /**
     * A String to filter log output from {@link HttpConnection} logs.
     */
    final static String TAG = "de.cyface.sync.http";
    /**
     * The boundary to be used in the Multipart request to separate data.
     */
    final static String BOUNDARY = "---------------------------boundary";
    /**
     * The string which is used for a line feed.
     */
    final static String LINE_FEED = "\r\n";
    /**
     * The tail to be used in the Multipart request to indicate that the request end.
     */
    final static String TAIL = "--" + BOUNDARY + "--" + LINE_FEED;
    /**
     * The status code returned when the MultiPart request is erroneous, e.g. when there is not exactly onf file or a
     * syntax error.
     */
    final static int HTTP_ENTITY_NOT_PROCESSABLE = 422;
    /**
     * The status code returned when the server thinks that this client sent too many requests in to short time.
     * This helps to prevent DDoS attacks. The client should just retry a short time later.
     */
    final static int HTTP_TOO_MANY_REQUESTS = 429;

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
    public HttpURLConnection openHttpConnection(@NonNull final URL url, @NonNull final SSLContext sslContext,
            final boolean hasBinaryContent, final @NonNull String jwtToken) throws ServerUnavailableException {
        final HttpURLConnection connection = openHttpConnection(url, sslContext, hasBinaryContent);
        connection.setRequestProperty("Authorization", "Bearer " + jwtToken);
        return connection;
    }

    @NonNull
    @Override
    public HttpURLConnection openHttpConnection(@NonNull final URL url, @NonNull final SSLContext sslContext,
            final boolean hasBinaryContent) throws ServerUnavailableException {
        HttpURLConnection connection;
        try {
            connection = (HttpURLConnection)url.openConnection();
        } catch (final IOException e) {
            throw new ServerUnavailableException(
                    String.format("Error %s. There seems to be no server at %s.", e.getMessage(), url.toString()), e);
        }

        if (url.getPath().startsWith("https://")) {
            final HttpsURLConnection httpsURLConnection = (HttpsURLConnection)connection;
            // Without verifying the hostname we receive the "Trust Anchor..." Error
            httpsURLConnection.setSSLSocketFactory(sslContext.getSocketFactory());
            httpsURLConnection.setHostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(final String hostname, final SSLSession session) {
                    HostnameVerifier hv = HttpsURLConnection.getDefaultHostnameVerifier();
                    return hv.verify(url.getHost(), session);
                }
            });
        }

        if (hasBinaryContent) {
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
        } else {
            connection.setRequestProperty("Content-Type", "application/json; charset=" + DEFAULT_CHARSET);
        }
        // connection.setConnectTimeout(5000);
        try {
            connection.setRequestMethod("POST");
        } catch (final ProtocolException e) {
            throw new IllegalStateException(e);
        }
        connection.setRequestProperty("User-Agent", System.getProperty("http.agent"));
        // connection.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
        return connection;
    }

    @NonNull
    @Override
    public HttpResponse post(@NonNull final HttpURLConnection connection, @NonNull final JSONObject payload,
            final boolean compress)
            throws SynchronisationException, UnauthorizedException, BadRequestException,
            InternalServerErrorException, ForbiddenException, EntityNotParsableException, ConflictException,
            NetworkUnavailableException, TooManyRequestsException {

        // For performance reasons (documentation) set ether fixedLength (known length) or chunked streaming mode
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
            if (e.getMessage().contains("I/O error during system call, Broken pipe")) {
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

        return readResponse(connection);
    }

    @NonNull
    @Override
    public HttpResponse post(@NonNull final HttpURLConnection connection, @NonNull final File transferTempFile,
            @NonNull final File eventsTransferTempFile, @NonNull final SyncAdapter.MetaData metaData,
            @NonNull final String fileName, @NonNull final String eventsFileName,
            @NonNull final UploadProgressListener progressListener)
            throws SynchronisationException, BadRequestException, UnauthorizedException, InternalServerErrorException,
            ForbiddenException, EntityNotParsableException, ConflictException, NetworkUnavailableException,
            SynchronizationInterruptedException, TooManyRequestsException {

        // Generate header
        // Attention: Parts of the header (Content-Type, boundary, request method, user agent) are already set
        final String remainingHeader = generateHeader(metaData);
        final byte[] remainingHeaderBytes = remainingHeader.getBytes();

        // File parts
        // Measurement file meta data
        final String fileHeaderPart = generateFileHeaderPart("fileToUpload", fileName);
        // Events file meta data
        final String eventsFileHeaderPart = generateFileHeaderPart("eventsFile", eventsFileName);
        // There should be no need to set a content length of the fileHeaderPart here

        // Set the fixed number of bytes which will be written to the OutputStream
        final long binarySize = transferTempFile.length();
        final long eventsBinarySize = eventsTransferTempFile.length();
        Validate.isTrue(binarySize != 0L); // We don't post empty files, ensure files still exists
        Validate.isTrue(eventsBinarySize != 0L); // We don't post empty files, ensure files still exists
        final long fixedStreamLength = calculateBytesWrittenToOutputStream(remainingHeaderBytes,
                fileHeaderPart.getBytes().length, eventsFileHeaderPart.getBytes().length, binarySize,
                eventsBinarySize);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            connection.setFixedLengthStreamingMode(fixedStreamLength);
        } else {
            connection.setFixedLengthStreamingMode((int)fixedStreamLength);
        }
        // connection.setRequestProperty("Content-length" should be obsolete with setFixedLengthStreamingMode

        // Use a buffered stream to upload the transfer file to avoid OOM and for performance
        final FileInputStream fileInputStream;
        final FileInputStream eventFileInputStream;
        try {
            fileInputStream = new FileInputStream(transferTempFile);
            eventFileInputStream = new FileInputStream(eventsTransferTempFile);
        } catch (final FileNotFoundException e) {
            throw new IllegalStateException(e);
        }
        final BufferedInputStream bufferedFileInputStream = new BufferedInputStream(fileInputStream);
        final BufferedInputStream bufferedEventsFileInputStream = new BufferedInputStream(eventFileInputStream);
        final BufferedOutputStream outputStream = initOutputStream(connection);

        try {
            connection.connect();
            try {
                long bytesWrittenToOutputStream = 0;

                // Write MultiPart header (including the filePartHeader
                bytesWrittenToOutputStream += remainingHeaderBytes.length;
                outputStream.write(remainingHeaderBytes);

                // Write Measurement binary
                outputStream.write(fileHeaderPart.getBytes());
                bytesWrittenToOutputStream += fileHeaderPart.getBytes().length;
                bytesWrittenToOutputStream += writeToOutputStream(outputStream, bufferedFileInputStream, binarySize,
                        progressListener);
                outputStream.write(LINE_FEED.getBytes());
                bytesWrittenToOutputStream += LINE_FEED.getBytes().length;

                // Write Events binary
                outputStream.write(eventsFileHeaderPart.getBytes());
                bytesWrittenToOutputStream += eventsFileHeaderPart.getBytes().length;
                bytesWrittenToOutputStream += writeToOutputStream(outputStream, bufferedEventsFileInputStream,
                        eventsBinarySize, progressListener);
                outputStream.write(LINE_FEED.getBytes());
                bytesWrittenToOutputStream += LINE_FEED.getBytes().length;

                // Write MultiPart Tail boundary
                outputStream.write(TAIL.getBytes());
                bytesWrittenToOutputStream += TAIL.getBytes().length;

                // Ensure we only write the "registered" number of bytes to the stream MOV-693
                outputStream.flush(); // This way we can identify exceptions thrown by flush easier
                Validate.isTrue(bytesWrittenToOutputStream == fixedStreamLength, "bytesWrittenToOutputStream "
                        + bytesWrittenToOutputStream + " != " + fixedStreamLength + " fixedStreamLength");
                Log.d(TAG, "Total bytes written to output stream: " + bytesWrittenToOutputStream);
            } finally {
                outputStream.close();
            }
        } catch (final SSLException e) {
            Log.w(TAG, "Caught SSLException: " + e.getMessage());
            // This exception is thrown by OkHttp when the network is no longer available
            if (e.getMessage().contains("I/O error during system call, Broken pipe")) {
                throw new NetworkUnavailableException("Network became unavailable during transmission.");
            }
            throw new SynchronisationException(e); // SSLException with unknown cause MOV-774
        } catch (final InterruptedIOException e) {
            // This exception is thrown when the login request is interrupted
            throw new NetworkUnavailableException("Network interrupted during post", e);
        } catch (final IOException e) {
            Log.w(TAG, "Caught IOException: " + e.getMessage());
            // Logging out interrupts the sync thread. This must not throw a RuntimeException, thus:
            if (e.getMessage().contains("unexpected end of stream")) {
                throw new SynchronizationInterruptedException("Sync was probably interrupted via cancelSynchronization",
                        e);
            }
            throw new SynchronisationException(e); // IOException with unknown cause MOV-778
        }

        return readResponse(connection);
    }

    /**
     * Writes the content of {@param bufferedFileInputStream} to the {@param outputStream} and informs the
     * {@param progressListener} about the progress.
     * 
     * @param outputStream the {@code BufferedOutputStream} to write the data to
     * @param bufferedFileInputStream the {@code BufferedInputStream} to read the data from
     * @param binarySize the {code Byte} size of the data to read
     * @param progressListener the {@code UploadProgressListener} to inform about the progress
     * @return the number of {@code Byte}s written to the {@param outputStream}
     * @throws IOException when an I/O operation fails
     */
    private int writeToOutputStream(@NonNull final BufferedOutputStream outputStream,
            @NonNull final BufferedInputStream bufferedFileInputStream, final long binarySize,
            @NonNull final UploadProgressListener progressListener) throws IOException {
        // Create file upload buffer
        // noinspection PointlessArithmeticExpression - makes semantically more sense
        final int maxBufferSize = 1 * 1024 * 1024;
        int bytesAvailable, bufferSize;
        byte[] buffer;
        bytesAvailable = bufferedFileInputStream.available();
        bufferSize = Math.min(bytesAvailable, maxBufferSize);
        buffer = new byte[bufferSize];

        // Write the binaries to the OutputStream
        int bytesWritten = 0;
        int bytesRead;
        bytesRead = bufferedFileInputStream.read(buffer, 0, bufferSize);
        while (bytesRead > 0) {
            outputStream.write(buffer, 0, bufferSize);
            bytesWritten += bytesRead; // Here progress is total uploaded bytes
            progressListener.updatedProgress((bytesWritten * 100.0f) / binarySize);

            bytesAvailable = bufferedFileInputStream.available();
            bufferSize = Math.min(bytesAvailable, maxBufferSize);
            bytesRead = bufferedFileInputStream.read(buffer, 0, bufferSize);
        }
        Log.d(TAG, "writeToOutputStream() file -> " + bytesWritten);
        return bytesWritten;
    }

    /**
     * Setting the number of bytes which will be written to the {@code OutputStream} up front (via
     * {@code HttpConnection#setFixedLengthStreamingMode()}) allows to flush the {@code OutputStream} frequently to
     * reduce the amount of bytes kept in memory.
     * 
     * @param headerBytes The MultiPart header as string which will be written to the {@code OutputStream}
     * @param fileHeaderPartSize The {@code Byte} size of the header part of the multipart file part
     * @param eventsFileHeaderPartSize The {@code Byte} size of the header part of the multipart file part
     * @param binarySize The number of bytes of the binary which will be written to the {@code OutputStream}
     * @param eventsBinarySize The number of bytes of the events binary which will be written to the
     *            {@code OutputStream}
     */
    long calculateBytesWrittenToOutputStream(final byte[] headerBytes, final int fileHeaderPartSize,
            final int eventsFileHeaderPartSize, final long binarySize,
            final long eventsBinarySize) {

        // This should be obsolete with setFixedLengthStreamingMode:
        // connection.setRequestProperty("Content-length", String.valueOf(requestLength));

        // Set count of Bytes not chars in the header!
        return headerBytes.length + fileHeaderPartSize + binarySize + LINE_FEED.getBytes().length
                + eventsFileHeaderPartSize + eventsBinarySize + LINE_FEED.getBytes().length
                + TAIL.getBytes().length;
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
     * @throws SynchronisationException when initializing the stream failed. This happened e.g. when Wifi was manually
     *             disabled just after synchronization started (Pixel 2 XL).
     */
    private BufferedOutputStream initOutputStream(final HttpURLConnection connection) throws SynchronisationException {
        connection.setDoOutput(true); // To upload data to the server
        try {
            // Wrapping this in a Buffered steam for performance reasons
            return new BufferedOutputStream(connection.getOutputStream());
        } catch (final IOException e) {
            throw new SynchronisationException(String.format("getOutputStream failed: %s", e.getMessage()), e);
        }
    }

    /**
     * Assembles the header of the Multipart request.
     *
     * @param metaData The {@link SyncAdapter.MetaData} required for the Multipart request.
     * @return The Multipart header
     */
    @NonNull
    String generateHeader(@NonNull final SyncAdapter.MetaData metaData) {

        // Location meta data
        String startLocationPart = ""; // We only transfer this part if there are > 0 locations
        if (metaData.startLocation != null) {
            final String startLocLat = generatePart("startLocLat", String.valueOf(metaData.startLocation.getLat()));
            final String startLocLon = generatePart("startLocLon", String.valueOf(metaData.startLocation.getLon()));
            final String startLocTS = generatePart("startLocTS", String.valueOf(metaData.startLocation.getTimestamp()));
            startLocationPart = startLocLat + startLocLon + startLocTS;
        }
        String endLocationPart = ""; // We only transfer this part if there are > 0 locations
        if (metaData.endLocation != null) {
            final String endLocLat = generatePart("endLocLat", String.valueOf(metaData.endLocation.getLat()));
            final String endLocLon = generatePart("endLocLon", String.valueOf(metaData.endLocation.getLon()));
            final String endLocTS = generatePart("endLocTS", String.valueOf(metaData.endLocation.getTimestamp()));
            endLocationPart = endLocLat + endLocLon + endLocTS;
        }
        final String locationCountPart = generatePart("locationCount", String.valueOf(metaData.locationCount));

        // Remaining meta data
        final String deviceIdPart = generatePart("deviceId", metaData.deviceId);
        final String measurementIdPart = generatePart("measurementId", Long.valueOf(metaData.measurementId).toString());
        final String deviceTypePart = generatePart("deviceType", metaData.deviceType);
        final String osVersionPart = generatePart("osVersion", metaData.osVersion);
        final String appVersionPart = generatePart("appVersion", metaData.appVersion);
        final String lengthPart = generatePart("length", String.valueOf(metaData.length));
        // To support the API v2 specification we may not change the "vehicle" key name of the modality
        final String modalityPart = generatePart("vehicle", String.valueOf(metaData.modality.getDatabaseIdentifier()));

        return startLocationPart + endLocationPart + deviceIdPart + measurementIdPart + deviceTypePart + osVersionPart
                + appVersionPart + lengthPart + locationCountPart + modalityPart;
    }

    /**
     * Generates a valid Multipart header entry for a file part.
     *
     * @param partName The name of the file part
     * @param fileName The name of the file attached
     * @return The generated part entry.
     */
    public static String generateFileHeaderPart(@NonNull final String partName, @NonNull final String fileName) {
        return "--" + BOUNDARY + LINE_FEED
                + "Content-Disposition: form-data; name=\"" + partName + "\"; filename=\"" + fileName + "\"" + LINE_FEED
                + "Content-Type: application/octet-stream" + LINE_FEED + "Content-Transfer-Encoding: binary" + LINE_FEED
                + LINE_FEED;
    }

    /**
     * Generates a valid Multipart entry.
     *
     * @param key The name of the part.
     * @param value The value of the part entry.
     * @return The generated part entry.
     */
    @NonNull
    private String generatePart(final @NonNull String key, final @NonNull String value) {
        return String.format(
                "--%s" + LINE_FEED + "Content-Disposition: form-data; name=\"%s\"" + LINE_FEED
                        + LINE_FEED + "%s" + LINE_FEED,
                HttpConnection.BOUNDARY, key, value);
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
     */
    @NonNull
    private HttpResponse readResponse(@NonNull final HttpURLConnection connection)
            throws SynchronisationException, BadRequestException, UnauthorizedException, ForbiddenException,
            ConflictException, EntityNotParsableException, InternalServerErrorException, TooManyRequestsException {

        // Read response from connection
        final int responseCode;
        try {
            responseCode = connection.getResponseCode();
        } catch (final IOException e) {
            throw new SynchronisationException(e);
        }
        final String responseBody = readResponseBody(connection);
        final HttpResponse response = new HttpResponse(responseCode, responseBody);

        // Handle known success responses
        switch (responseCode) {
            // Login Requests
            case HttpURLConnection.HTTP_OK:
                Log.d(TAG, "200: Login successful");
                return response;
            case HttpURLConnection.HTTP_CREATED:
                Log.d(TAG, "201: Upload successful");
                return response;
        }

        // Handle known error responses
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
            case HttpURLConnection.HTTP_CONFLICT:
                Log.w(TAG, "409: The measurement already exists on the server.");
                throw new ConflictException(response.getBody());
            case HTTP_ENTITY_NOT_PROCESSABLE:
                Log.w(TAG, "422: Multipart request is erroneous.");
                throw new EntityNotParsableException(response.getBody());
            case HttpURLConnection.HTTP_INTERNAL_ERROR:
                Log.w(TAG, "500: Server reported internal error.");
                throw new InternalServerErrorException(response.getBody());
            case HTTP_TOO_MANY_REQUESTS:
                Log.w(TAG, "429: Server reported too many requests received from this client.");
                throw new TooManyRequestsException(response.getBody());
        }

        // Known response
        throw new IllegalStateException("Unknown error code: " + responseCode);
    }

    /**
     * Reads the body from the {@link HttpURLConnection}. This contains ether the error or the success message.
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
            // This occurred e.g. on Xaomi Mi A1 after disabling WiFi instantly after sync start
            if (errorStream == null) {
                return "";
            }

            return readInputStream(errorStream);
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
            BufferedReader bufferedReader = null;
            try {
                bufferedReader = new BufferedReader(new InputStreamReader(inputStream, DEFAULT_CHARSET));
                final StringBuilder responseString = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    responseString.append(line);
                }
                return responseString.toString();
            } catch (final UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            } finally {
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
            }
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
