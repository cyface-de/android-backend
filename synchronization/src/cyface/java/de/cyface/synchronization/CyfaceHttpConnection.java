package de.cyface.synchronization;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import org.json.JSONException;

import android.support.annotation.NonNull;
import android.util.Log;

import de.cyface.utils.Validate;
import de.cyface.utils.ValidationException;

import static de.cyface.synchronization.Constants.DEFAULT_CHARSET;

/**
 * Implements the {@link Http} connection interface for the Cyface apps.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.3
 * @since 2.0.0
 */
public class CyfaceHttpConnection implements Http {

    static final String TAG = "de.cyface.http";

    @Override
    public String returnUrlWithTrailingSlash(final String url) {
        if (url.endsWith("/")) {
            return url;
        } else {
            return url + "/";
        }
    }

    @Override
    public HttpURLConnection openHttpConnection(final @NonNull URL url, final @NonNull String jwtBearer)
            throws ServerUnavailableException {
        final HttpURLConnection con = openHttpConnection(url);
        con.setRequestProperty("Authorization", jwtBearer);
        return con;
    }

    @Override
    public HttpURLConnection openHttpConnection(final @NonNull URL url) throws ServerUnavailableException {
        try {
            final HttpURLConnection con = (HttpURLConnection)url.openConnection();
            con.setRequestProperty("Content-Type", "application/json; charset=" + DEFAULT_CHARSET);
            con.setConnectTimeout(5000);
            con.setRequestMethod("POST");
            con.setRequestProperty("User-Agent", System.getProperty("http.agent"));
            con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
            return con;
        } catch (final IOException e) {
            throw new ServerUnavailableException(
                    String.format("Error %s. There seems to be no server at %s.", e.getMessage(), url.toString()), e);
        }
    }

    @Override
    public <T> HttpResponse post(final HttpURLConnection con, final T payload, boolean compress)
            throws RequestParsingException, DataTransmissionException, SynchronisationException,
            ResponseParsingException, UnauthorizedException {

        final BufferedOutputStream os = initOutputStream(con, compress);
        try {
            Log.d(TAG, "Transmitting with compression " + compress + ".");
            if (compress) {
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
        return readResponse(con);
    }

    private byte[] gzip(byte[] input) {
        GZIPOutputStream gzipOutputStream = null;
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream);
            gzipOutputStream.write(input);
            gzipOutputStream.flush();
            gzipOutputStream.close();
            gzipOutputStream = null;
            return byteArrayOutputStream.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            if (gzipOutputStream != null) {
                try {
                    gzipOutputStream.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private BufferedOutputStream initOutputStream(final HttpURLConnection con, final boolean compress)
            throws SynchronisationException {
        if (compress) {
            con.setRequestProperty("Content-Encoding", "gzip");
        }
        con.setChunkedStreamingMode(0);
        con.setDoOutput(true);
        try {
            return new BufferedOutputStream(con.getOutputStream());
        } catch (final IOException e) {
            throw new SynchronisationException(String.format(
                    "OutputStream failed: Error %s. Unable to create new data output for the http connection.",
                    e.getMessage()), e);
        }
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
            DataTransmissionException, UnauthorizedException, SynchronisationException {

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
                        String.format("'%s'. Unable to read the http response.", e.getMessage()), e);
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
            throws ResponseParsingException, SynchronisationException, UnauthorizedException {
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
            return new HttpResponse(con.getResponseCode(), responseString);
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
