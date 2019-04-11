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

import androidx.annotation.NonNull;

/**
 * Internal value object class for the attributes of an HTTP response. It wrappers the HTTP
 * status code as well as the String body.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 3.0.0
 * @since 1.0.0
 */
class HttpResponse {

    /**
     * The {@code HttpURLConnection} status code returned by the server's {@link HttpResponse}.
     */
    private final int responseCode;
    /**
     * The server's {@link HttpResponse} body.
     */
    @NonNull
    private final String body;

    /**
     * @param responseCode the HTTP status code returned by the server
     * @param responseBody the HTTP response body returned by the server. Can be empty when the server has nothing to
     *            say.
     */
    HttpResponse(final int responseCode, @NonNull final String responseBody) {
        this.responseCode = responseCode;
        this.body = responseBody;
    }

    @NonNull
    String getBody() {
        return body;
    }

    int getResponseCode() {
        return responseCode;
    }
}
