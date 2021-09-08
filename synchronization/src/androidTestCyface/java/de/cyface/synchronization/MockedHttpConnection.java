/*
 * Copyright 2018 - 2021 Cyface GmbH
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

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONObject;

import androidx.annotation.NonNull;

import de.cyface.synchronization.exception.SynchronisationException;

/**
 * An HTTP connection that does not actually connect to the server. This is useful for testing code requiring a
 * connection.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 4.0.0
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
    public HttpURLConnection open(@NonNull URL url, boolean hasBinaryContent, @NonNull String jwtBearer)
            throws SynchronisationException {
        return open(url, hasBinaryContent);
    }

    @NonNull
    @Override
    public HttpURLConnection open(@NonNull URL url, boolean hasBinaryContent) throws SynchronisationException {
        try {
            return (HttpURLConnection)url.openConnection();
        } catch (IOException e) {
            throw new SynchronisationException("Mocked Err", e);
        }
    }

    @NonNull
    @Override
    public HttpConnection.Result login(@NonNull HttpURLConnection connection, @NonNull JSONObject payload,
            boolean compress) {
        return HttpConnection.Result.LOGIN_SUCCESSFUL;
    }

    @Override
    public HttpConnection.Result upload(URL url, String jwtBearer, SyncAdapter.MetaData metaData, File file,
            UploadProgressListener progressListener) {
        progressListener.updatedProgress(1.0f); // 100%
        return HttpConnection.Result.UPLOAD_SUCCESSFUL;
    }
}