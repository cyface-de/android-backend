package de.cyface.synchronization;

import java.io.File;
import java.io.IOException;
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
 * @version 2.0.0
 * @since 3.0.0
 */
final class MockedHttpConnection implements Http {

    @NonNull
    @Override
    public String returnUrlWithTrailingSlash(@NonNull String url) {
        return url;
    }

    @NonNull
    @Override
    public HttpsURLConnection openHttpConnection(@NonNull URL url, @NonNull SSLContext sslContext,
            boolean hasBinaryContent, @NonNull String jwtBearer) throws ServerUnavailableException {
        return openHttpConnection(url, sslContext, hasBinaryContent);
    }

    @NonNull
    @Override
    public HttpsURLConnection openHttpConnection(@NonNull URL url, @NonNull SSLContext sslContext,
            boolean hasBinaryContent) throws ServerUnavailableException {
        try {
            return (HttpsURLConnection)url.openConnection();
        } catch (IOException e) {
            throw new ServerUnavailableException("Mocked Err", e);
        }
    }

    @NonNull
    @Override
    public HttpResponse post(@NonNull HttpURLConnection connection, @NonNull JSONObject payload, boolean compress)
            throws ResponseParsingException, UnauthorizedException, BadRequestException {
        return new HttpResponse(201, "");
    }

    @NonNull
    @Override
    public HttpResponse post(@NonNull HttpURLConnection connection, @NonNull File transferTempFile, @NonNull String deviceId,
                             long measurementId, @NonNull String fileName, @NonNull UploadProgressListener progressListener)
            throws ResponseParsingException, UnauthorizedException, BadRequestException {
        progressListener.updatedProgress(1.0f); // 100%
        return new HttpResponse(201, "");
    }
}