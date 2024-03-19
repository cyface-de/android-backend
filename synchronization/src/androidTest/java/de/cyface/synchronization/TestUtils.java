/*
 * Copyright 2019 Cyface GmbH
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

/**
 * Contains utility methods and constants required by the tests within the synchronization project.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.4.0
 * @since 2.1.0
 */
public final class TestUtils {
    /**
     * The tag used to identify Logcat messages from this module.
     */
    final static String TAG = Constants.TAG + ".test";
    /**
     * The content provider authority used during tests. This must be the same as in the manifest and the authenticator
     * configuration.
     */
    final static String AUTHORITY = "de.cyface.synchronization.test.provider";
    /**
     * The account type used during testing. This must be the same as in the authenticator configuration.
     */
    final static String ACCOUNT_TYPE = "de.cyface.synchronization.test";
    /**
     * For manual testing this can be replaced with an username available at {@link #TEST_API_URL}.
     * <p>
     * Never use actual APIs credentials in automated tests.
     */
    final static String DEFAULT_USERNAME = "REPLACE_WITH_USERNAME";
    /**
     * For manual testing this can be replaced with a password available at {@link #TEST_API_URL}.
     * <p>
     * Never use actual APIs credentials in automated tests.
     */
    final static String DEFAULT_PASSWORD = "REPLACE_WITH_PASSWORD";
    /**
     * For manual testing this can be replaced with a path to an API available for testing.
     * <p>
     * Never use actual APIs in automated tests.
     */
    @SuppressWarnings("unused") // because this is used in the cyface flavour
    final static String TEST_API_URL = "https://replace.with/url"; // never use a non-numeric port here!
}
