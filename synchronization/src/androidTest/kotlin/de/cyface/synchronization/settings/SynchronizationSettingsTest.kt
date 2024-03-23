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
package de.cyface.synchronization.settings

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import de.cyface.persistence.SetupException
import org.json.JSONObject
import org.junit.Before
import org.junit.Test

/**
 * Test the inner workings of the [DefaultSynchronizationSettings].
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.9.0
 */
class SynchronizationSettingsTest {

    private var context: Context? = null

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    /**
     * Test that checks that the [DefaultSynchronizationSettings] constructor only accepts API URls with
     * "https://" as protocol.
     *
     * We had twice the problem that SDK implementors used no or a false protocol. This test ensures
     * that our code throws a hard exception if this happens again which should help to identify this
     * prior to release.
     */
    @Test(expected = SetupException::class)
    @Throws(SetupException::class)
    fun testConstructor_doesNotAcceptUrlWithoutProtocol() {
        DefaultSynchronizationSettings(
            context!!,
            "localhost:8080/api/v3",
            JSONObject()
        )
    }
}