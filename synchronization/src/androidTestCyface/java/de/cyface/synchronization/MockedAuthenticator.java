/*
 * Copyright 2023 Cyface GmbH
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

import androidx.annotation.NonNull;

import java.net.MalformedURLException;
import java.net.URL;

import de.cyface.model.Activation;
import de.cyface.uploader.Authenticator;
import de.cyface.uploader.Result;
import de.cyface.uploader.exception.RegistrationFailed;

/**
 * An [Authenticator] that does not actually connect to the server, for testing.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.7.0
 */
final class MockedAuthenticator implements Authenticator {

    @NonNull
    @Override
    public String authenticate(@NonNull String s, @NonNull String s1) {
        return "testAuthToken";
    }

    @NonNull
    @Override
    public URL loginEndpoint() throws MalformedURLException {
        return new URL("https://mocked.cyface.de/api/v123/login");
    }

    @NonNull
    @Override
    public Result register(@NonNull String s, @NonNull String s1, @NonNull String s2, @NonNull Activation activation) throws RegistrationFailed {
        return Result.UPLOAD_SUCCESSFUL;
    }

    @NonNull
    @Override
    public URL registrationEndpoint() throws MalformedURLException {
        return new URL("https://mocked.cyface.de/api/v123/login");
    }
}