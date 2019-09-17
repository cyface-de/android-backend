/*
 * Copyright 2017 Cyface GmbH
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
package de.cyface.datacapturing.backend;

import static de.cyface.datacapturing.TestUtils.AUTHORITY;
import static de.cyface.datacapturing.TestUtils.TIMEOUT_TIME;
import static de.cyface.synchronization.BundlesExtrasCodes.DISTANCE_CALCULATION_STRATEGY_ID;
import static de.cyface.synchronization.BundlesExtrasCodes.EVENT_HANDLING_STRATEGY_ID;
import static de.cyface.synchronization.BundlesExtrasCodes.LOCATION_CLEANING_STRATEGY_ID;
import static de.cyface.synchronization.BundlesExtrasCodes.SENSOR_FREQUENCY;
import static de.cyface.testutils.SharedTestUtils.clearPersistenceLayer;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.Context;
import android.content.Intent;
import android.os.Messenger;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.rule.ServiceTestRule;

import de.cyface.datacapturing.IgnoreEventsStrategy;
import de.cyface.datacapturing.IsRunningCallback;
import de.cyface.datacapturing.MessageCodes;
import de.cyface.datacapturing.PongReceiver;
import de.cyface.datacapturing.TestUtils;
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour;
import de.cyface.persistence.DefaultDistanceCalculationStrategy;
import de.cyface.persistence.DefaultLocationCleaningStrategy;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.Modality;
import de.cyface.synchronization.BundlesExtrasCodes;
import de.cyface.utils.CursorIsNullException;

/**
 * Tests whether the {@link DataCapturingBackgroundService} handling the data capturing works correctly.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.3.5
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class DataCapturingBackgroundServiceTest {

    /**
     * Junit rule handling the service connection.
     */
    @Rule
    public ServiceTestRule serviceTestRule = new ServiceTestRule();
    /**
     * Grants the <code>ACCESS_FINE_LOCATION</code> permission while running this test.
     */
    @Rule
    public GrantPermissionRule mRuntimePermissionRule = GrantPermissionRule
            .grant(android.Manifest.permission.ACCESS_FINE_LOCATION);
    /**
     * The messenger used to receive messages from the data capturing service.
     */
    private Messenger fromServiceMessenger;
    /**
     * The identifier for the test measurement created in the <code>setUp</code> method.
     */
    private Measurement testMeasurement;
    /**
     * Lock used to synchronize the test case with the background service.
     */
    private Lock lock;
    /**
     * Condition waiting for the background service to message this service, that it is running.
     */
    private Condition condition;
    /**
     * The {@link Context} required to send unique broadcasts, to start the capturing service and more.
     */
    private Context context;

    /**
     * Sets up all the instances required by all tests in this test class.
     *
     * @throws CursorIsNullException Then the content provider is not accessible
     */
    @Before
    public void setUp() throws CursorIsNullException {
        lock = new ReentrantLock();
        condition = lock.newCondition();
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        // This is normally called in the <code>DataCapturingService#Constructor</code>
        final PersistenceLayer<CapturingPersistenceBehaviour> persistenceLayer = new PersistenceLayer<>(context,
                context.getContentResolver(), AUTHORITY, new CapturingPersistenceBehaviour());
        persistenceLayer.restoreOrCreateDeviceId();

        testMeasurement = persistenceLayer.newMeasurement(Modality.BICYCLE);
    }

    @After
    public void tearDown() {
        clearPersistenceLayer(context, context.getContentResolver(), AUTHORITY);
        testMeasurement = null;
    }

    /**
     * This test case checks that starting the {@link DataCapturingBackgroundService} works and that the service
     * actually returns some data.
     *
     * @throws TimeoutException if timed out waiting for a successful connection with the service.
     */
    @Test
    public void testStartDataCapturing() throws TimeoutException {

        // Arrange (which are normally done by the DataCapturingService which is not part of this test)
        // Instantiate the Messenger for the service connection [ usually in DataCapturingService() ]
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                FromServiceMessageHandler fromServiceMessageHandler = new FromServiceMessageHandler();
                fromServiceMessenger = new Messenger(fromServiceMessageHandler);
            }
        });

        // Instantiate ToServiceConnection [ usually in DataCapturingService: BackgroundServiceConnection ]
        final TestCallback testCallback = new TestCallback("testStartDataCapturing", lock, condition);
        final ToServiceConnection toServiceConnection = new ToServiceConnection(fromServiceMessenger,
                context.getPackageName());
        toServiceConnection.context = context;
        toServiceConnection.callback = testCallback;

        // Start and bind DataCapturingBackgroundService [ usually in DCS.runService() ]
        final Intent startIntent = new Intent(context, DataCapturingBackgroundService.class);
        startIntent.putExtra(BundlesExtrasCodes.MEASUREMENT_ID, testMeasurement.getIdentifier());
        startIntent.putExtra(BundlesExtrasCodes.AUTHORITY_ID, AUTHORITY);
        startIntent.putExtra(EVENT_HANDLING_STRATEGY_ID, new IgnoreEventsStrategy());
        startIntent.putExtra(DISTANCE_CALCULATION_STRATEGY_ID, new DefaultDistanceCalculationStrategy());
        startIntent.putExtra(LOCATION_CLEANING_STRATEGY_ID, new DefaultLocationCleaningStrategy());
        startIntent.putExtra(SENSOR_FREQUENCY, 100);
        final Intent bindIntent = new Intent(context, DataCapturingBackgroundService.class);
        serviceTestRule.startService(startIntent);
        // bindService() waits for ServiceConnection.onServiceConnected() to be called before returning
        serviceTestRule.bindService(bindIntent, toServiceConnection, 0);

        // Act: Check if DataCapturingBackgroundService is running by sending a Ping
        checkDataCapturingBackgroundServiceRunning(testCallback);

        // Assert
        assertThat("It seems that service did not respond to a ping.", testCallback.wasRunning(), is(equalTo(true)));
        assertThat("It seems that the request to the service whether it was active timed out.",
                testCallback.didTimeOut(), is(equalTo(false)));

        // Cleanup: Unbind background service
        serviceTestRule.unbindService();
    }

    /**
     * This test case checks that starting the service works and that the service actually returns some data.
     *
     * @throws TimeoutException if timed out waiting for a successful connection with the service.
     */
    @Test
    public void testStartDataCapturingTwice() throws TimeoutException {

        // Arrange
        final TestCallback testCallback = new TestCallback("testStartDataCapturingTwice", lock, condition);

        // Start background service twice
        final Intent startIntent = new Intent(context, DataCapturingBackgroundService.class);
        startIntent.putExtra(BundlesExtrasCodes.MEASUREMENT_ID, testMeasurement.getIdentifier());
        startIntent.putExtra(BundlesExtrasCodes.AUTHORITY_ID, AUTHORITY);
        startIntent.putExtra(EVENT_HANDLING_STRATEGY_ID, new IgnoreEventsStrategy());
        startIntent.putExtra(DISTANCE_CALCULATION_STRATEGY_ID, new DefaultDistanceCalculationStrategy());
        startIntent.putExtra(LOCATION_CLEANING_STRATEGY_ID, new DefaultLocationCleaningStrategy());
        startIntent.putExtra(SENSOR_FREQUENCY, 100);
        final Intent bindIntent = new Intent(context, DataCapturingBackgroundService.class);
        serviceTestRule.startService(startIntent);
        serviceTestRule.bindService(bindIntent);
        serviceTestRule.startService(startIntent);
        serviceTestRule.bindService(bindIntent);

        // Act
        checkDataCapturingBackgroundServiceRunning(testCallback);

        // Assert
        assertThat("It seems that service did not respond to a ping.", testCallback.wasRunning(), is(equalTo(true)));
        assertThat("It seems that the request to the service whether it was active timed out.",
                testCallback.didTimeOut(), is(equalTo(false)));
    }

    /**
     * Sends a ping to the {@link DataCapturingBackgroundService} to check if it's running.
     * <p>
     * As {@link PongReceiver#checkIsRunningAsync(long, TimeUnit, IsRunningCallback)} is async this method waits
     * up to {@link TestUtils#TIMEOUT_TIME} to receive the pong response from the background service.
     *
     * @param testCallback The {@link TestCallback} used to check the async result.
     */
    private void checkDataCapturingBackgroundServiceRunning(@NonNull final TestCallback testCallback) {

        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                final String appId = context.getPackageName();
                final PongReceiver isRunningChecker = new PongReceiver(context, MessageCodes.getPingActionId(appId),
                        MessageCodes.getPongActionId(appId));
                isRunningChecker.checkIsRunningAsync(TIMEOUT_TIME, TimeUnit.SECONDS, testCallback);
            }
        });
        // This must not run on the main thread or it will produce an ANR.
        lock.lock();
        try {
            if (!testCallback.wasRunning()) {
                if (!condition.await(2 * TIMEOUT_TIME, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Waiting for pong or timeout timed out!");
                }
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }
    }
}
