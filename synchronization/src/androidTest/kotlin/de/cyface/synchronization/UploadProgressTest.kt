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
package de.cyface.synchronization

import android.accounts.Account
import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SyncResult
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import de.cyface.persistence.DefaultPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.PersistenceBehaviour
import de.cyface.persistence.PersistenceLayer
import de.cyface.persistence.content.LocationTable.Companion.getUri
import de.cyface.persistence.exception.NoSuchMeasurementException
import de.cyface.persistence.model.MeasurementStatus
import de.cyface.persistence.model.Modality
import de.cyface.persistence.serialization.Point3DFile
import de.cyface.serializer.model.Point3DType
import de.cyface.testutils.SharedTestUtils.cleanupOldAccounts
import de.cyface.testutils.SharedTestUtils.clearPersistenceLayer
import de.cyface.testutils.SharedTestUtils.insertGeoLocation
import de.cyface.testutils.SharedTestUtils.insertMeasurementEntry
import de.cyface.testutils.SharedTestUtils.insertPoint3D
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.LinkedList

/**
 * Tests if the upload progress is broadcast as expected.
 *
 * This test does not run against an actual API, but uses [MockedUploader].
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.4.8
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class UploadProgressTest {

    private lateinit var persistence: PersistenceLayer<PersistenceBehaviour>
    private var context: Context? = null
    private var contentResolver: ContentResolver? = null
    private var persistenceLayer: DefaultPersistenceLayer<DefaultPersistenceBehaviour?>? = null
    private var accountManager: AccountManager? = null
    private var oocut: SyncAdapter? = null
    private var account: Account? = null

    @Before
    fun setUp() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        contentResolver = context!!.contentResolver
        persistence = DefaultPersistenceLayer(context!!, DefaultPersistenceBehaviour())
        clearPersistenceLayer(context!!, persistence)
        persistenceLayer = DefaultPersistenceLayer(context!!, DefaultPersistenceBehaviour())
        persistenceLayer!!.restoreOrCreateDeviceId()

        // Ensure reproducibility
        accountManager = AccountManager.get(context)
        cleanupOldAccounts(accountManager!!, TestUtils.ACCOUNT_TYPE, TestUtils.AUTHORITY)

        // Add new sync account (usually done by DataCapturingService and WifiSurveyor)
        account = Account(TestUtils.DEFAULT_USERNAME, TestUtils.ACCOUNT_TYPE)
        accountManager!!.addAccountExplicitly(account, TestUtils.DEFAULT_PASSWORD, null)
        oocut = SyncAdapter(context!!, false, MockAuth(), MockedUploader())
    }

    @After
    fun tearDown() {
        runBlocking { clearPersistenceLayer(context!!, persistence) }
        val oldAccounts = accountManager!!.getAccountsByType(TestUtils.ACCOUNT_TYPE)
        if (oldAccounts.isNotEmpty()) {
            for (oldAccount in oldAccounts) {
                ContentResolver.removePeriodicSync(oldAccount, TestUtils.AUTHORITY, Bundle.EMPTY)
                require(accountManager!!.removeAccountExplicitly(oldAccount))
            }
        }
        contentResolver = null
        context = null
    }

    @Test
    @Throws(
        NoSuchMeasurementException::class
    )
    fun testUploadProgressHappyPath() = runBlocking {
        val receiver = TestReceiver()
        val filter = IntentFilter()
        filter.addAction(CyfaceConnectionStatusListener.SYNC_FINISHED)
        filter.addAction(CyfaceConnectionStatusListener.SYNC_PROGRESS)
        filter.addAction(CyfaceConnectionStatusListener.SYNC_STARTED)

        @SuppressLint("UnspecifiedRegisterReceiverFlag")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context!!.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context!!.registerReceiver(receiver, filter)
        }

        var client: ContentProviderClient? = null
        try {
            val (measurementIdentifier) = insertMeasurementEntry(
                persistenceLayer!!,
                Modality.UNKNOWN
            )
            insertGeoLocation(
                persistenceLayer!!.locationDao!!, measurementIdentifier, 1503055141000L,
                49.9304133333333,
                8.82831833333333, 400.0, 0.0, 9.4, 20.0
            )
            insertGeoLocation(
                persistenceLayer!!.locationDao!!, measurementIdentifier, 1503055142000L,
                49.9305066666667,
                8.82814, 400.0, 8.78270530700684, 8.4, 20.0
            )

            // Insert file base data
            val accelerationsFile =
                Point3DFile(context!!, measurementIdentifier, Point3DType.ACCELERATION)
            val rotationsFile = Point3DFile(context!!, measurementIdentifier, Point3DType.ROTATION)
            val directionsFile =
                Point3DFile(context!!, measurementIdentifier, Point3DType.DIRECTION)
            insertPoint3D(accelerationsFile, 1501662635973L, 10.1189575, -0.15088624, 0.2921924)
            insertPoint3D(accelerationsFile, 1501662635981L, 10.116563, -0.16765137, 0.3544629)
            insertPoint3D(accelerationsFile, 1501662635983L, 10.171648, -0.2921924, 0.3784131)
            insertPoint3D(rotationsFile, 1501662635981L, 0.001524045, 0.0025423833, -0.0010279021)
            insertPoint3D(rotationsFile, 1501662635990L, 0.001524045, 0.0025423833, -0.016474236)
            insertPoint3D(rotationsFile, 1501662635993L, -0.0064654383, -0.0219587, -0.014343708)
            insertPoint3D(directionsFile, 1501662636010L, 7.65, -32.4, -71.4)
            insertPoint3D(directionsFile, 1501662636030L, 7.65, -32.550003, -71.700005)
            insertPoint3D(directionsFile, 1501662636050L, 7.65, -33.15, -71.700005)

            // Mark measurement as finished
            persistenceLayer!!.setStatus(measurementIdentifier, MeasurementStatus.FINISHED, false)
            client = contentResolver!!.acquireContentProviderClient(getUri(TestUtils.AUTHORITY))
            val result = SyncResult()
            requireNotNull(client)
            val testBundle = Bundle()
            testBundle.putString(SyncAdapter.MOCK_IS_CONNECTED_TO_RETURN_TRUE, "")
            oocut!!.onPerformSync(account!!, testBundle, TestUtils.AUTHORITY, client, result)
        } finally {
            client?.close()
            context!!.unregisterReceiver(receiver)
        }
        MatcherAssert.assertThat(
            receiver.getCollectedPercentages().size, CoreMatchers.`is`(
                CoreMatchers.equalTo(1)
            )
        )
        MatcherAssert.assertThat(
            receiver.getCollectedPercentages()[0], CoreMatchers.`is`(
                CoreMatchers.equalTo(100.0f)
            )
        )
    }
}

internal class TestReceiver : BroadcastReceiver() {
    private val collectedPercentages: MutableList<Float> = LinkedList()
    override fun onReceive(context: Context, intent: Intent) {
        requireNotNull(intent.action)
        when (intent.action) {
            CyfaceConnectionStatusListener.SYNC_FINISHED -> {
                Log.d(TestUtils.TAG, "SYNC FINISHED")
            }
            CyfaceConnectionStatusListener.SYNC_PROGRESS -> {
                val percentage = intent.getFloatExtra(BundlesExtrasCodes.SYNC_PERCENTAGE_ID, -1.0f)
                collectedPercentages.add(percentage)
                Log.d(TestUtils.TAG, "SYNC PROGRESS: $percentage % ")
            }
            CyfaceConnectionStatusListener.SYNC_STARTED -> {
                Log.d(TestUtils.TAG, "SYNC STARTED")
            }
            else -> {
                error("Invalid message ${intent.action}")
            }
        }
    }

    fun getCollectedPercentages(): List<Float> {
        return collectedPercentages
    }
}
