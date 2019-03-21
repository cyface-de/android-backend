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
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;

import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.NetworkErrorException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import de.cyface.utils.Validate;

/**
 * Implements the {@link Http} connection interface for the Cyface apps.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 4.0.0
 * @since 2.0.0
 */
public class HttpConnection implements Http {

    /**
     * A String to filter log output from {@link HttpConnection} logs.
     */
    static final String TAG = "de.cyface.http";
    /**
     * The boundary to be used in the Multipart request to separate data.
     */
    private final static String BOUNDARY = "---------------------------boundary";
    /**
     * The tail to be used in the Multipart request to indicate that the request end.
     */
    private final static String TAIL = "\r\n--" + BOUNDARY + "--\r\n";

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
    public HttpsURLConnection openHttpConnection(@NonNull final URL url, @NonNull final SSLContext sslContext,
            final boolean hasBinaryContent, final @NonNull String jwtToken) throws ServerUnavailableException {
        final HttpsURLConnection connection = openHttpConnection(url, sslContext, hasBinaryContent);
        connection.setRequestProperty("Authorization", "Bearer " + jwtToken);
        return connection;
    }

    @NonNull
    @Override
    public HttpsURLConnection openHttpConnection(@NonNull final URL url, @NonNull final SSLContext sslContext,
            final boolean hasBinaryContent) throws ServerUnavailableException {
        HttpsURLConnection connection;
        try {
            connection = (HttpsURLConnection)url.openConnection();
        } catch (final IOException e) {
            throw new ServerUnavailableException(
                    String.format("Error %s. There seems to be no server at %s.", e.getMessage(), url.toString()), e);
        }

        // Without verifying the hostname we receive the "Trust Anchor..." Error
        connection.setSSLSocketFactory(sslContext.getSocketFactory());
        connection.setHostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(final String hostname, final SSLSession session) {
                HostnameVerifier hv = HttpsURLConnection.getDefaultHostnameVerifier();
                return hv.verify(url.getHost(), session);
            }
        });

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
            final boolean compress) throws RequestParsingException, DataTransmissionException, SynchronisationException,
            ResponseParsingException, UnauthorizedException, BadRequestException, NetworkErrorException {

        // For performance reasons (documentation) set ether fixedLength (known length) or chunked streaming mode
        connection.setChunkedStreamingMode(0); // we could also calculate the length here
        final BufferedOutputStream os = initOutputStream(connection);

        try {
            Log.d(TAG, "Transmitting with compression " + compress + ".");
            if (compress) {
                connection.setRequestProperty("Content-Encoding", "gzip");
                os.write(gzip(payload.toString().getBytes(DEFAULT_CHARSET)));
            } else {
                os.write(payload.toString().getBytes(DEFAULT_CHARSET));
            }
            os.flush();
            os.close();
        } catch (final IOException e) {
            throw new RequestParsingException(String.format("Error %s. Unable to parse http request.", e.getMessage()),
                    e);
        }
        return readResponse(connection);
    }

    @NonNull
    @Override
    public HttpResponse post(@NonNull final HttpURLConnection connection, final @NonNull File transferTempFile,
            @NonNull final SyncAdapter.MetaData metaData, @NonNull final String fileName,
            @NonNull UploadProgressListener progressListener)
            throws SynchronisationException, ResponseParsingException, BadRequestException, UnauthorizedException {

        // Generate header
        final long filePartSize = transferTempFile.length() + TAIL.length();
        Validate.isTrue(filePartSize > 0);
        final String header = generateHeader(filePartSize, metaData, fileName);

        // Set content length
        final long requestLength = header.length() + filePartSize;
        connection.setRequestProperty("Content-length", "" + requestLength);
        connection.setFixedLengthStreamingMode((int)requestLength);

        // Use a buffered stream to upload the transfer file to avoid OOM and for performance
        final FileInputStream fileInputStream;
        try {
            fileInputStream = new FileInputStream(transferTempFile);
        } catch (final FileNotFoundException e) {
            throw new IllegalStateException(e);
        }
        final BufferedInputStream bufferedFileInputStream = new BufferedInputStream(fileInputStream);
        final BufferedOutputStream outputStream = initOutputStream(connection);

        try {
            connection.connect();
            try {
                // Send header
                outputStream.write(header.getBytes());
                outputStream.flush();

                // Create file upload buffer
                // noinspection PointlessArithmeticExpression - makes semantically more sense
                final int maxBufferSize = 1 * 1024 * 1024;
                int bytesAvailable, bufferSize;
                byte[] buffer;
                bytesAvailable = bufferedFileInputStream.available();
                bufferSize = Math.min(bytesAvailable, maxBufferSize);
                buffer = new byte[bufferSize];

                // Send file
                int progress = 0;
                int bytesRead;
                bytesRead = bufferedFileInputStream.read(buffer, 0, bufferSize);
                while (bytesRead > 0) {
                    outputStream.write(buffer, 0, bufferSize);
                    outputStream.flush();
                    progress += bytesRead; // Here progress is total uploaded bytes
                    progressListener.updatedProgress((progress * 100.0f) / filePartSize);

                    bytesAvailable = bufferedFileInputStream.available();
                    bufferSize = Math.min(bytesAvailable, maxBufferSize);
                    bytesRead = bufferedFileInputStream.read(buffer, 0, bufferSize);
                }

                // Write closing boundary and close stream
                outputStream.write(TAIL.getBytes());
                outputStream.flush();
            } finally {
                outputStream.close();
            }
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }

        // Get server response
        try {
            return new HttpResponse(connection.getResponseCode(), connection.getResponseMessage());
        } catch (final IOException e) {
            Log.w(TAG, "Server closed stream. Request was not successful!");
            throw new ResponseParsingException("Server closed stream?", e);
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
     * @param filePartSize The Bytes of the file to be transferred including the {@link #TAIL} length.
     * @param metaData The {@link SyncAdapter.MetaData} required for the Multipart request.
     * @param fileName The name of the file to be uploaded
     * @return The Multipart header
     */
    @NonNull
    private String generateHeader(final long filePartSize, @NonNull final SyncAdapter.MetaData metaData,
            @NonNull final String fileName) {

        // File meta data
        final String filePart = "--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n" + "Content-Transfer-Encoding: binary\r\n";
        final String contentLengthPart = "Content-length: " + filePartSize + "\r\n";
        final String fileHeaderPart = filePart + contentLengthPart + "\r\n";

        // Location meta data
        String startLocationPart = ""; // We only transfer this part if there are > 0 locations
        if (metaData.startLocation != null) {
            startLocationPart = generatePart("startLocation", metaData.startLocation.getLat() + ", "
                    + metaData.startLocation.getLon() + ", " + metaData.startLocation.getTimestamp());
        }
        String endLocationPart = ""; // We only transfer this part if there are > 0 locations
        if (metaData.endLocation != null) {
            endLocationPart = generatePart("endLocation", metaData.endLocation.getLat() + ", "
                    + metaData.endLocation.getLon() + ", " + metaData.endLocation.getTimestamp());
        }
        final String locationCountPart = generatePart("locationCount", String.valueOf(metaData.locationCount));

        // Remaining meta data
        final String deviceIdPart = generatePart("deviceId", metaData.deviceId);
        final String measurementIdPart = generatePart("measurementId", Long.valueOf(metaData.measurementId).toString());
        final String deviceTypePart = generatePart("deviceType", metaData.deviceType);
        final String osVersionPart = generatePart("osVersion", metaData.osVersion);
        final String appVersionPart = generatePart("appVersion", metaData.appVersion);
        final String lengthPart = generatePart("length", String.valueOf(metaData.length));

        // This was reordered to be in the same order as in the iOS code
        return fileHeaderPart + startLocationPart + endLocationPart + deviceIdPart + measurementIdPart + deviceTypePart
                + osVersionPart + appVersionPart + lengthPart + locationCountPart;
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
        return String.format("--%s\r\nContent-Disposition: form-data; name=\"%s\"\r\n\r\n%s\r\n",
                HttpConnection.BOUNDARY, key, value);
    }

    /**
     * Parses the JSON response from a connection and includes error handling for non 2XX status
     * codes.
     *
     * @param connection The connection that received the response.
     * @return A parsed {@link HttpResponse} object.
     * @throws ResponseParsingException If the system fails in handling the HTTP response.
     * @throws DataTransmissionException If the response was a non-successful HTTP response.
     * @throws UnauthorizedException When the server returns {@code HttpURLConnection#HTTP_UNAUTHORIZED}
     * @throws SynchronisationException If a connection error occurred while reading the response code.
     * @throws BadRequestException When server returns {@code HttpURLConnection#HTTP_BAD_REQUEST}
     * @throws NetworkErrorException when the connection's input or error stream was null
     */
    private HttpResponse readResponse(@NonNull final HttpURLConnection connection)
            throws ResponseParsingException, DataTransmissionException, UnauthorizedException, SynchronisationException,
            BadRequestException, NetworkErrorException {

        final HttpResponse response = readResponseFromConnection(connection);
        if (response.is2xxSuccessful()) {
            return response;
        } else {
            if (response.getResponseCode() == HttpURLConnection.HTTP_UNAUTHORIZED) {
                throw new UnauthorizedException("Server returned 401: UNAUTHORIZED.");
            }
            try {
                if (response.getBody().has("errorName")) {
                    throw new DataTransmissionException(response.getResponseCode(),
                            response.getBody().getString("errorName"), response.getBody().getString("errorMessage"));
                } else if (response.getBody().has("exception") && response.getBody().has("error")
                        && response.getBody().has("message")) {
                    throw new DataTransmissionException(response.getResponseCode(),
                            response.getBody().getString("exception"),
                            response.getBody().getString("error") + ": " + response.getBody().getString("message"));
                } else {
                    throw new DataTransmissionException(response.getResponseCode(), "unknown response attributes",
                            response.getBody().toString());
                }
            } catch (final JSONException e) {
                throw new ResponseParsingException(
                        String.format("readResponse() failed: '%s'. Unable to read the http response.", e.getMessage()),
                        e);
            }
        }
    }

    /**
     * Extracts the {@link HttpResponse} from the {@link HttpURLConnection}.
     *
     * @param connection the {@link HttpURLConnection} to read the response from
     * @return the {@link HttpResponse}
     * @throws SynchronisationException when a connection error occurred while reading the response code
     * @throws ResponseParsingException when the server response was unreadable
     * @throws UnauthorizedException When the server returns {@code HttpURLConnection#HTTP_UNAUTHORIZED}
     * @throws BadRequestException When server returns {@code HttpURLConnection#HTTP_BAD_REQUEST}
     * @throws NetworkErrorException when the connection's input or error stream was null
     */
    private HttpResponse readResponseFromConnection(@NonNull final HttpURLConnection connection)
            throws SynchronisationException, ResponseParsingException, UnauthorizedException, BadRequestException,
            NetworkErrorException {
        String responseString;
        try {
            responseString = readInputStream(connection.getInputStream());
        } catch (final IOException e) {
            // This means that an error occurred, read the error from the ErrorStream
            // see https://developer.android.com/reference/java/net/HttpURLConnection
            try {
                responseString = readInputStream(connection.getErrorStream());
            } catch (final IOException e1) {
                throw new IllegalStateException("Unable to read error body.", e1);
            } catch (final NullPointerException e1) {
                // Occurred on Xaomi Mi A1 after disabling WiFi instantly after sync start
                throw new SynchronisationException("Failed to read error. Connection interrupted?", e1);
            }
        }

        try {
            final HttpResponse response;
            response = new HttpResponse(connection.getResponseCode(), responseString);
            return response;
        } catch (final IOException e) {
            throw new SynchronisationException("A connection error occurred while reading the response code.", e);
        }
    }

    /**
     * Reads a String from an InputStream.
     *
     * @param inputStream the {@link InputStream} to read from
     * @throws IOException if an IO error occurred
     * @throws NetworkErrorException if the provided {@code InputStream} was null
     * @return the {@link String} read from the InputStream
     */
    private String readInputStream(@Nullable final InputStream inputStream) throws IOException, NetworkErrorException {
        // This happened on Pixel 2 XL when wifi was manually disabled just after sync started
        if (inputStream == null) {
            throw new NetworkErrorException("readInputStream with null inputStream, returning empty String");
        }

        BufferedReader bufferedReader = null;
        try {
            bufferedReader = new BufferedReader(new InputStreamReader(inputStream, DEFAULT_CHARSET));
            StringBuilder responseString = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                responseString.append(line);
            }
            return responseString.toString();
        } finally {
            if (bufferedReader != null) {
                bufferedReader.close();
            }
        }
    }
}
