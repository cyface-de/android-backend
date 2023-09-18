/*
 * Copyright 2017-2023 Cyface GmbH
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
package de.cyface.synchronization

/**
 * Final static constants used by multiple classes.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 2.0.0
 */
object Constants {
    /**
     * Tag used to identify Logcat messages issued by instances of this package.
     */
    const val TAG = "de.cyface.sync"
    // TODO [MOV-555]: Change these strings between two (STADTRADELN) campaigns.
    /**
     * The Cyface account type used to identify all Cyface system accounts.
     */
    const val ACCOUNT_TYPE = "de.cyface"

    @Suppress("unused") // Because this allows the sdk integrating app to add a sync account

    val AUTH_TOKEN_TYPE = "de.cyface.jwt"
}