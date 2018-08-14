package de.cyface.synchronization;

import android.support.annotation.NonNull;

import org.json.JSONException;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * An HTTP connection that does not actually connect to the server. This is useful for testing code requiring a
 * connection.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 3.0.0
 */
final class MockedHttpConnection implements Http {

    @Override
    public String returnUrlWithTrailingSlash(String url) {
        return url;
    }

    @Override
    public HttpURLConnection openHttpConnection(@NonNull URL url, @NonNull String jwtBearer)
            throws DataTransmissionException {
        return openHttpConnection(url);
    }

    @Override
    public HttpURLConnection openHttpConnection(@NonNull URL url) throws DataTransmissionException {
        try {
            return (HttpURLConnection)url.openConnection();
        } catch (IOException e) {
            throw new DataTransmissionException(404, "MockedErr", "Mocked Err");
        }
    }

    @Override
    public <T> HttpResponse post(HttpURLConnection con, T payload, boolean compress)
            throws DataTransmissionException, SynchronisationException {
        try {
            return new HttpResponse(201, "");
        } catch (JSONException e) {
            throw new DataTransmissionException(404, "MockedErr", "Mocked Err");
        }
    }
}
