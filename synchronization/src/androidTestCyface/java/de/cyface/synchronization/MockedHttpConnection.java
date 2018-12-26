package de.cyface.synchronization;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

import org.json.JSONObject;

import androidx.annotation.NonNull;

/**
 * An HTTP connection that does not actually connect to the server. This is useful for testing code requiring a
 * connection.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.0
 * @since 3.0.0
 */
final class MockedHttpConnection implements Http {

    @Override
    public String returnUrlWithTrailingSlash(String url) {
        return url;
    }

    @Override
    public HttpsURLConnection openHttpConnection(@NonNull URL url, SSLContext sslContext, boolean hasBinaryContent,
            @NonNull String jwtBearer) throws ServerUnavailableException {
        return openHttpConnection(url, sslContext, hasBinaryContent);
    }

    @Override
    public HttpsURLConnection openHttpConnection(@NonNull URL url, SSLContext sslContext, boolean hasBinaryContent)
            throws ServerUnavailableException {
        try {
            return (HttpsURLConnection)url.openConnection();
        } catch (IOException e) {
            throw new ServerUnavailableException("Mocked Err", e);
        }
    }

    @Override
    public HttpResponse post(HttpURLConnection connection, JSONObject payload, boolean compress)
            throws RequestParsingException, DataTransmissionException, SynchronisationException,
            ResponseParsingException, UnauthorizedException, BadRequestException {
        return new HttpResponse(201, "");
    }

    @Override
    public HttpResponse post(@NonNull HttpURLConnection connection, @NonNull InputStream data, @NonNull String deviceId,
            long measurementId, @NonNull String fileName, UploadProgressListener progressListener)
            throws RequestParsingException, DataTransmissionException, SynchronisationException,
            ResponseParsingException, UnauthorizedException, BadRequestException {
        progressListener.updatedProgress(1.0f); // 100%
        return new HttpResponse(201, "");
    }
}
