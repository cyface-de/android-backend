package de.cyface.synchronization;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import android.support.annotation.NonNull;

/**
 * An HTTP connection that does not actually connect to the server. This is useful for testing code requiring a
 * connection.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.0.1
 * @since 3.0.0
 */
final class MockedHttpConnection implements Http {

    @Override
    public String returnUrlWithTrailingSlash(String url) {
        return url;
    }

    @Override
    public HttpURLConnection openHttpConnection(@NonNull URL url, @NonNull String jwtBearer)
            throws ServerUnavailableException {
        return openHttpConnection(url);
    }

    @Override
    public HttpURLConnection openHttpConnection(@NonNull URL url) throws ServerUnavailableException {
        try {
            return (HttpURLConnection)url.openConnection();
        } catch (IOException e) {
            throw new ServerUnavailableException("Mocked Err", e);
        }
    }

    @Override
    public <T> HttpResponse post(HttpURLConnection con, T payload, boolean compress) throws ResponseParsingException {
        return new HttpResponse(201, "");
    }
}
