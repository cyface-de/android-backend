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

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.Context
import android.content.SyncResult
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import de.cyface.persistence.DefaultPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.PersistenceBehaviour
import de.cyface.persistence.PersistenceLayer
import de.cyface.persistence.content.BaseColumns
import de.cyface.persistence.content.LocationTable
import de.cyface.persistence.content.MeasurementTable
import de.cyface.persistence.exception.NoSuchMeasurementException
import de.cyface.persistence.model.MeasurementStatus
import de.cyface.testutils.SharedTestUtils.cleanupOldAccounts
import de.cyface.testutils.SharedTestUtils.clearPersistenceLayer
import de.cyface.testutils.SharedTestUtils.insertSampleMeasurementWithData
import de.cyface.utils.CursorIsNullException
import de.cyface.utils.Validate
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.hamcrest.core.IsNull
import org.junit.After
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import de.cyface.synchronization.MockAuth
import de.cyface.synchronization.MockedUploader
import de.cyface.synchronization.TestUtils

/**
 * Tests the correct internal workings of the `CyfaceSyncAdapter` with the persistence layer.
 *
 * This test does not run against an actual API, but uses [MockedAuthenticator] and [MockedUploader].
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 2.4.6
 * @since 2.4.0
 */
@RunWith(AndroidJUnit4::class) // To notice errors with the short running testOnPerformSync before the large
// testOnPerformSyncWithLargeMeasurement which can save a lot of time
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SyncAdapterTest {

    private lateinit var persistence: PersistenceLayer<PersistenceBehaviour>
    private var context: Context? = null
    private var contentResolver: ContentResolver? = null
    private var account: Account? = null
    private var oocut: SyncAdapter? = null
    private var accountManager: AccountManager? = null

    @Before
    fun setUp() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        contentResolver = context!!.contentResolver
        persistence = DefaultPersistenceLayer(context!!, DefaultPersistenceBehaviour())
        clearPersistenceLayer(context!!, persistence)

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
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
                    accountManager!!.removeAccount(oldAccount, null, null)
                } else {
                    Validate.isTrue(accountManager!!.removeAccountExplicitly(oldAccount))
                }
            }
        }
        contentResolver = null
        context = null
    }

    /**
     * Tests whether measurements are correctly marked as synced.
     */
    @Test
    @Throws(
        NoSuchMeasurementException::class, CursorIsNullException::class
    )
    fun testOnPerformSync() {
        val point3DCount = 1 // 100 Hz * 8 h = 2_880_000
        val locationCount = 1 // 1 Hz * 8 h = 28_800

        // Arrange
        // Insert data to be synced
        val persistence = DefaultPersistenceLayer<DefaultPersistenceBehaviour?>(
            context!!, DefaultPersistenceBehaviour()
        )
        persistence.restoreOrCreateDeviceId() // is usually called by the DataCapturingService
        val (measurementIdentifier) = insertSampleMeasurementWithData(
            context!!,
            MeasurementStatus.FINISHED, persistence, point3DCount, locationCount
        )
        val loadedStatus = persistence.loadMeasurementStatus(measurementIdentifier)
        MatcherAssert.assertThat(
            loadedStatus,
            CoreMatchers.`is`(CoreMatchers.equalTo(MeasurementStatus.FINISHED))
        )
        contentResolver!!
            .acquireContentProviderClient(LocationTable.getUri(TestUtils.AUTHORITY)).use { client ->
                val result = SyncResult()
                Validate.notNull(client)
                val testBundle = Bundle()
                testBundle.putString(SyncAdapter.MOCK_IS_CONNECTED_TO_RETURN_TRUE, "")
                oocut!!.onPerformSync(account!!, testBundle, TestUtils.AUTHORITY, client!!, result)
            }

        // Assert: synced data is marked as synced
        val newStatus = persistence.loadMeasurementStatus(measurementIdentifier)
        MatcherAssert.assertThat(
            newStatus,
            CoreMatchers.`is`(CoreMatchers.equalTo(MeasurementStatus.SYNCED))
        )

        // GeoLocation
        val loadedMeasurement = persistence.loadMeasurement(measurementIdentifier)
        MatcherAssert.assertThat(loadedMeasurement, IsNull.notNullValue())
        val tracks = persistence.loadTracks(
            loadedMeasurement!!.id
        )
        MatcherAssert.assertThat(tracks[0].geoLocations.size, CoreMatchers.`is`(1))
    }

    /**
     * Test to reproduce problems occurring when large measurement uploads are not handled correctly,
     * e.g. OOM during serialization or compression.
     *
     * This test was used to reproduce:
     * - MOV-528: OOM on large uploads (depending on the device memory)
     * - MOV-515: 401 when upload takes longer than the token validation time (server-side).
     * (!) This bug is only triggered when you replace MockedHttpConnection with HttpConnection
     */
    @Test
    @LargeTest // ~ 8-10 minutes
    @Ignore("Because this is a very large test which does not need to be executed each time")
    @Throws(
        NoSuchMeasurementException::class, CursorIsNullException::class
    )
    fun testOnPerformSyncWithLargeMeasurement() {
        // 3_000_000 is the minimum which reproduced MOV-515 on N5X emulator
        val point3DCount = 2880000 // 100 Hz * 8 h
        val locationCount = 28800 // 1 Hz * 8 h

        // Arrange
        // Insert data to be synced
        val persistence = DefaultPersistenceLayer<DefaultPersistenceBehaviour?>(
            context!!, DefaultPersistenceBehaviour()
        )
        persistence.restoreOrCreateDeviceId() // is usually called by the DataCapturingService
        val (measurementIdentifier) = insertSampleMeasurementWithData(
            context!!,
            MeasurementStatus.FINISHED, persistence, point3DCount, locationCount
        )
        val loadedStatus = persistence.loadMeasurementStatus(measurementIdentifier)
        MatcherAssert.assertThat(
            loadedStatus,
            CoreMatchers.`is`(CoreMatchers.equalTo(MeasurementStatus.FINISHED))
        )

        // Mock - nothing to do

        // Act: sync
        var client: ContentProviderClient? = null
        try {
            client =
                contentResolver!!.acquireContentProviderClient(LocationTable.getUri(TestUtils.AUTHORITY))
            val result = SyncResult()
            Validate.notNull(client)
            val testBundle = Bundle()
            testBundle.putString(SyncAdapter.MOCK_IS_CONNECTED_TO_RETURN_TRUE, "")
            oocut!!.onPerformSync(account!!, testBundle, TestUtils.AUTHORITY, client!!, result)
        } finally {
            if (client != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    client.close()
                } else {
                    client.release()
                }
            }
        }

        // Assert: synced data is marked as synced
        val newStatus = persistence.loadMeasurementStatus(measurementIdentifier)
        MatcherAssert.assertThat(
            newStatus,
            CoreMatchers.`is`(CoreMatchers.equalTo(MeasurementStatus.SYNCED))
        )

        // GeoLocation
        val loadedMeasurement = persistence.loadMeasurement(measurementIdentifier)
        MatcherAssert.assertThat(loadedMeasurement, IsNull.notNullValue())
        val tracks = persistence.loadTracks(
            loadedMeasurement!!.id
        )
        MatcherAssert.assertThat(tracks[0].geoLocations.size, CoreMatchers.`is`(locationCount))
    }

    /**
     * Loads the track of geolocations objects for the provided measurement id.
     *
     * @param measurementId The measurement id of the data to load.
     * @return The cursor for the track of geolocation objects ordered by time ascending.
     */
    fun loadTrack(resolver: ContentResolver, measurementId: Long): Cursor? {
        return resolver.query(
            LocationTable.getUri(TestUtils.AUTHORITY),
            null,
            BaseColumns.MEASUREMENT_ID + "=?",
            arrayOf(measurementId.toString()),
            BaseColumns.TIMESTAMP + " ASC"
        )
    }

    /**
     * Loads the measurement for the provided measurement id.
     *
     * @param measurementId The measurement id of the measurement to load.
     * @return The cursor for the loaded measurement.
     */
    fun loadMeasurement(resolver: ContentResolver, measurementId: Long): Cursor? {
        return resolver.query(
            MeasurementTable.getUri(TestUtils.AUTHORITY),
            null,
            BaseColumns.ID + "=?",
            arrayOf(measurementId.toString()),
            null
        )
    }
}