package de.cyface.synchronization;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import android.support.annotation.NonNull;

import javax.net.ssl.SSLContext;

/**
 * An HTTP connection that does not actually connect to the server. This is useful for testing code requiring a
 * connection.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.0.2
 * @since 3.0.0
 */
final class MockedHttpConnection implements Http {

    @Override
    public String returnUrlWithTrailingSlash(String url) {
        return url;
    }

    @Override
    public HttpURLConnection openHttpConnection(@NonNull URL url, @NonNull String jwtBearer, SSLContext sslContext, boolean hasBinaryContent)
            throws ServerUnavailableException {
        return openHttpConnection(url, hasBinaryContent);
    }

    @Override
    public HttpURLConnection openHttpConnection(@NonNull URL url, boolean hasBinaryContent) throws ServerUnavailableException {
        try {
            return (HttpURLConnection)url.openConnection();
        } catch (IOException e) {
            throw new ServerUnavailableException("Mocked Err", e);
        }
    }

    @Override
    public <T> HttpResponse post(HttpURLConnection con, T payload, boolean compress)
            throws ResponseParsingException, UnauthorizedException {
        return new HttpResponse(201, "");
    }

    @Override
    public HttpResponse post(@NonNull HttpURLConnection connection, @NonNull InputStream data, @NonNull String deviceId, long measurementId, @NonNull String fileName, UploadProgressListener progressListener) throws RequestParsingException, DataTransmissionException, SynchronisationException, ResponseParsingException, UnauthorizedException {
        return new HttpResponse(201, "");
    }
}
