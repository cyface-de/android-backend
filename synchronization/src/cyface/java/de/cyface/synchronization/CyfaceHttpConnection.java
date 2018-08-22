package de.cyface.synchronization;

import android.support.annotation.NonNull;
import android.util.Log;

import org.json.JSONException;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.GZIPOutputStream;

import org.json.JSONException;

import android.support.annotation.NonNull;
import android.util.Log;

/**
 * Implements the {@link Http} connection interface for the Cyface apps.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 2.0.0
 */
public class CyfaceHttpConnection implements Http {

    private static final String TAG = "de.cyface.http";

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
            con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
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

        BufferedOutputStream os = initOutputStream(con, compress);
        try {
            Log.d(TAG, "Transmitting with compression " + compress + ".");
            if (compress) {
                os.write(gzip(payload.toString().getBytes("UTF-8")));
            } else {
                os.write(payload.toString().getBytes("UTF-8"));
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
     */
    private HttpResponse readResponse(final @NonNull HttpURLConnection con)
            throws DataTransmissionException, ResponseParsingException, UnauthorizedException {

        StringBuilder responseString = new StringBuilder();
        HttpResponse response;
        try {
            // We need to read the status code first, as a response with an error might not contain
            // a response body but an error response body. This caused "response not readable" on Xpedia Z5 6.0.1
            // int status = con.getResponseCode();
            try { // if (status >= 400 && status <= 600) {
                BufferedReader er = new BufferedReader(new InputStreamReader(con.getErrorStream()));
                String errorLine;
                while ((errorLine = er.readLine()) != null) {
                    responseString.append(errorLine);
                }
                er.close();
            } catch (NullPointerException e) { // } else {
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    responseString.append(inputLine);
                }
                in.close();
            }
            response = new HttpResponse(con.getResponseCode(), responseString.toString());
            if (response.is2xxSuccessful()) {
                return response;
            } else {
                if (response.getResponseCode() == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    throw new UnauthorizedException("Server returned 401: UNAUTHORIZED.");
                }
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
            }
        } catch (final IOException | JSONException e) {
            throw new ResponseParsingException(
                    String.format("Error: '%s'. Unable to read the http response.", e.getMessage()), e);
        }
    }
}
