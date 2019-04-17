/*
 * Copyright 2018 Cyface GmbH
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
package de.cyface.persistence;

/**
 * Contains constants and utility methods required during testing.
 *
 * @author Armin Schnabel
 * @version 1.2.1
 * @since 4.0.0
 */
public final class TestUtils {

    /**
     * The content provider authority used for testing.
     */
    public final static String AUTHORITY = "de.cyface.persistence.provider.test";
    /**
     * The account type used during testing. This must be the same as in the authenticator configuration.
     */
    public final static String ACCOUNT_TYPE = "de.cyface.persistence.account.test";

    /**
     * Private constructor to avoid instantiation of utility class.
     */
    private TestUtils() {
        // Nothing to do here.
    }
}
