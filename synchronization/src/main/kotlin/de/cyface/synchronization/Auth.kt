/*
 * Copyright 2023-2024 Cyface GmbH
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

import androidx.fragment.app.FragmentActivity
import net.openid.appauth.IdToken

/**
 * The interface to authenticate the user and get the user id.
 *
 * @author Armin Schnabel
 */
interface Auth {

    /**
     * Makes sure that a valid access token is available before executing the provided action.
     *
     * @param action The method which will be executed.
     */
    fun performActionWithFreshTokens(
        action: (
            accessToken: String?,
            idToken: String?,
            ex: Exception?
        ) -> Unit
    )

    /**
     * @return The `userId` or `null` if not available.
     */
    fun userId(): String?

    /**
     * Sends the end session request to the auth service to sign out the user.
     */
    fun endSession(activity: FragmentActivity)
}