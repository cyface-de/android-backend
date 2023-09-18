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
package de.cyface.persistence

/**
 * An `Exception` that is thrown each time setting up the `DataCapturingService` fails.
 *
 * @author Klemens Muthmann
 * @version 1.0.2
 * @since 2.0.0
 */
class SetupException : Exception {
    /**
     * Creates a new completely initialized `SetupException`, wrapping another `Exception` from
     * deeper within the system.
     *
     * @param e The wrapped `Exception`.
     */
    constructor(e: Exception) : super(e)

    /**
     * Creates a new completely initialized `SetupException`, providing a detailed explanation about the
     * error.
     *
     * @param message The message explaining the error condition.
     */
    constructor(message: String) : super(message)
}