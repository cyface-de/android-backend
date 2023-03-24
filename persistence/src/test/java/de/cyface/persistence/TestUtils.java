/*
 * Copyright 2018-2023 Cyface GmbH
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
 * @version 1.3.0
 * @since 4.0.0
 */
public final class TestUtils {

    /**
     * The content provider authority used for testing.
     */
    public final static String AUTHORITY = "de.cyface.persistence.provider.test";

    /**
     * Private constructor to avoid instantiation of utility class.
     */
    private TestUtils() {
        // Nothing to do here.
    }
}
