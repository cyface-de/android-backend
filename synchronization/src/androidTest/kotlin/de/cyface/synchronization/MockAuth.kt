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

/**
 * The mock implementation of the [Auth] interface.
 *
 * @author Armin Schnabel
 */
class MockAuth: Auth {
    override fun performActionWithFreshTokens(action: (accessToken: String?, idToken: String?, ex: Exception?) -> Unit) {
        action("eyTestToken", null, null)
    }

    override fun userId(): String {
        return "testUserId"
    }

    override fun endSession(activity: FragmentActivity) {
        // Nothing to do
    }
}