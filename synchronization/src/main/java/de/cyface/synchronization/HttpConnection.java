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

import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import de.cyface.utils.Validate;
import de.cyface.utils.ValidationException;

/**
 * Implements the {@link Http} connection interface for the Cyface apps.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 2.0.0
 */
public class HttpConnection implements Http {

    static final String TAG = "de.cyface.http";
    private final static String BOUNDARY = "---------------------------boundary";
    private final static String TAIL = "\r\n--" + BOUNDARY + "--\r\n";

    @Override
    public String returnUrlWithTrailingSlash(final String url) {
        if (url.endsWith("/")) {
            return url;
        } else {
            return url + "/";
        }
    }

    @Override
    public HttpsURLConnection openHttpConnection(@NonNull final URL url, @NonNull final SSLContext sslContext,
            final boolean hasBinaryContent, final @NonNull String jwtToken) throws ServerUnavailableException {
        final HttpsURLConnection connection = openHttpConnection(url, sslContext, hasBinaryContent);
        connection.setRequestProperty("Authorization", "Bearer " + jwtToken);
        return connection;
    }

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

    @Override
    public HttpResponse post(final HttpURLConnection connection, final JSONObject payload, final boolean compress)
            throws RequestParsingException, DataTransmissionException, SynchronisationException,
            ResponseParsingException, UnauthorizedException, BadRequestException {

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

    @Override
    public HttpResponse post(@NonNull final HttpURLConnection connection, final @NonNull File transferTempFile,
            @NonNull final String deviceId, final long measurementId, @NonNull final String fileName,
            UploadProgressListener progressListener)
            throws SynchronisationException, ResponseParsingException, BadRequestException, UnauthorizedException {

        // Use a buffered stream to upload the transfer file to avoid OOM and for performance
        FileInputStream fileInputStream;
        try {
            fileInputStream = new FileInputStream(transferTempFile);
        } catch (FileNotFoundException e) {
            throw new IllegalStateException(e);
        }
        final BufferedInputStream bufferedFileInputStream = new BufferedInputStream(fileInputStream);

        final long dataSize = transferTempFile.length() + TAIL.length();
        Validate.isTrue(dataSize > 0);

        final String header = setContentLength(connection, dataSize, deviceId, measurementId, fileName);
        final BufferedOutputStream outputStream = initOutputStream(connection);

        try {
            connection.connect();
            try {
                outputStream.write(header.getBytes());
                outputStream.flush();

                int progress = 0;
                int bytesRead;

                // noinspection PointlessArithmeticExpression - makes semantically more sense
                final int maxBufferSize = 1 * 1024 * 1024;
                int bytesAvailable, bufferSize;
                byte[] buffer;
                bytesAvailable = bufferedFileInputStream.available();
                bufferSize = Math.min(bytesAvailable, maxBufferSize);
                buffer = new byte[bufferSize];

                bytesRead = bufferedFileInputStream.read(buffer, 0, bufferSize);
                while (bytesRead > 0) {
                    outputStream.write(buffer, 0, bufferSize);
                    outputStream.flush();
                    progress += bytesRead; // Here progress is total uploaded bytes
                    progressListener.updatedProgress((progress * 100.0f) / dataSize);

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

    private BufferedOutputStream initOutputStream(final HttpURLConnection connection) throws SynchronisationException {
        connection.setDoOutput(true); // To upload data to the server
        try {
            // Wrapping this in a Buffered steam for performance reasons
            return new BufferedOutputStream(connection.getOutputStream());
        } catch (final IOException e) {
            throw new SynchronisationException(String.format("getOutputStream failed: %s", e.getMessage()), e);
        }
    }

    private String setContentLength(final HttpURLConnection connection, final long dataSize,
            final String deviceIdentifier, final long measurementIdentifier, final String fileName) {
        final String deviceIdPart = addPart("deviceId", deviceIdentifier, BOUNDARY);
        final String measurementIdPart = addPart("measurementId", Long.valueOf(measurementIdentifier).toString(),
                BOUNDARY);
        final String deviceTypePart = addPart("deviceType", android.os.Build.MODEL, BOUNDARY);
        final String androidVersion = addPart("osVersion", "Android " + Build.VERSION.RELEASE, BOUNDARY);

        final String fileHeader1 = "--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n" + "Content-Transfer-Encoding: binary\r\n";

        final String fileHeader2 = "Content-length: " + dataSize + "\r\n";
        final String fileHeader = fileHeader1 + fileHeader2 + "\r\n";
        final String stringData = deviceIdPart + measurementIdPart + deviceTypePart + androidVersion + fileHeader;

        final long requestLength = stringData.length() + dataSize;
        connection.setRequestProperty("Content-length", "" + requestLength);
        connection.setFixedLengthStreamingMode((int)requestLength);
        return stringData;
    }

    private String addPart(final @NonNull String key, final @NonNull String value, final @NonNull String boundary) {
        return String.format("--%s\r\nContent-Disposition: form-data; name=\"%s\"\r\n\r\n%s\r\n", boundary, key, value);
    }

    /**
     * Parses the JSON response from a connection and includes error handling for non 2XX status
     * codes.
     *
     * @param con The connection that received the response.
     * @return A parsed {@link HttpResponse} object.
     * @throws DataTransmissionException If the response was a non-successful HTTP response.
     * @throws ResponseParsingException If the system fails in handling the HTTP response.
     * @throws UnauthorizedException If the credentials for the cyface server are wrong.
     * @throws SynchronisationException If a connection error occurred while reading the response code.
     */
    private HttpResponse readResponse(final @NonNull HttpURLConnection con) throws ResponseParsingException,
            DataTransmissionException, UnauthorizedException, SynchronisationException, BadRequestException {

        final HttpResponse response = readResponseFromConnection(con);
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
     * @param con the {@link HttpURLConnection} to read the response from
     * @return the {@link HttpResponse}
     * @throws ResponseParsingException when the server response was unreadable
     * @throws SynchronisationException when a connection error occurred while reading the response code
     * @throws UnauthorizedException when the login was not successful and returned a 401 code.
     */
    private HttpResponse readResponseFromConnection(final HttpURLConnection con)
            throws SynchronisationException, ResponseParsingException, UnauthorizedException, BadRequestException {
        String responseString;
        try {
            responseString = readInputStream(con.getInputStream());
        } catch (final IOException e) {
            // This means that an error occurred, read the error from the ErrorStream
            // see https://developer.android.com/reference/java/net/HttpURLConnection
            try {
                responseString = readInputStream(con.getErrorStream());
            } catch (final IOException e1) {
                throw new IllegalStateException("Unable to read error body.", e1);
            } catch (final NullPointerException e1) {
                // Occurred on Xaomi Mi A1 after disabling WiFi instantly after sync start
                throw new SynchronisationException("Failed to read error. Connection interrupted?", e1);
            }
        }

        try {
            final HttpResponse response;
            response = new HttpResponse(con.getResponseCode(), responseString);
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
     * @throws ValidationException if the connect() method was not executed on {@link HttpURLConnection}
     * @return the {@link String} read from the InputStream
     */
    private String readInputStream(@NonNull final InputStream inputStream) throws IOException {
        BufferedReader bufferedReader = null;
        try {
            Validate.notNull(inputStream);
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
