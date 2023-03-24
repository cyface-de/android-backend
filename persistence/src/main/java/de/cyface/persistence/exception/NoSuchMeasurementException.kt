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

/**
 * An `Exception` which occurs every time someone wants to execute an action on a
 * [de.cyface.persistence.model.Measurement], that does not exist on this device.
 *
 * @author Klemens Muthmann
 * @since 2.0.1
 * @version 1.0.1
 * @property message The explanation of why this error occurred.
 */
class NoSuchMeasurementException(message: String) : Exception(message)