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
package de.cyface.persistence.exception

import java.lang.Exception

/**
 * An `Exception` which occurs every time someone wants to load the device id from the
 * [de.cyface.persistence.DefaultPersistenceLayer] when there is no such entry in the database.
 *
 * @author Armin Schnabel
 * @version 1.0.1
 * @since 4.0.0
 */
class NoDeviceIdException
/**
 * Creates a new completely initialized [NoDeviceIdException], providing a detailed explanation
 * about the error to the caller.
 *
 * @param message The explanation of why this error occurred.
 */
    (message: String) : Exception(message)