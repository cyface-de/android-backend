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
package de.cyface.datacapturing;

import static de.cyface.datacapturing.TestUtils.AUTHORITY;
import static de.cyface.datacapturing.TestUtils.TIMEOUT_TIME;
import static de.cyface.testutils.SharedTestUtils.clearPersistenceLayer;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.accounts.AccountAuthenticatorActivity;
import android.content.ContentProvider;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import de.cyface.datacapturing.backend.DataCapturingBackgroundService;
import de.cyface.datacapturing.backend.TestCallback;
import de.cyface.datacapturing.exception.CorruptedMeasurementException;
import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.exception.MissingPermissionException;
import de.cyface.datacapturing.exception.SetupException;
import de.cyface.persistence.exception.NoSuchMeasurementException;
import de.cyface.persistence.model.Modality;
import de.cyface.synchronization.CyfaceAuthenticator;
import de.cyface.utils.CursorIsNullException;

/**
 * This test checks that the ping pong mechanism works as expected. This mechanism ist used to check if a service, in
 * this case the {@link DataCapturingBackgroundService}, is running or not.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.2.9
 * @since 2.3.2
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class PingPongTest {

    /**
     * Grants the permission required by the {@link DataCapturingService}.
     */
    @Rule
    public GrantPermissionRule mRuntimePermissionRule = GrantPermissionRule
            .grant(android.Manifest.permission.ACCESS_FINE_LOCATION);
    /**
     * An instance of the class under test (object of class under test).
     */
    private PongReceiver oocut;
    /**
     * Lock used to synchronize the asynchronous calls to the {@link DataCapturingService} with the test thread.
     */
    private Lock lock;
    /**
     * Condition used to synchronize the asynchronous calls to the {@link DataCapturingService} with the test thread.
     */
    private Condition condition;
    /**
     * The {@link DataCapturingService} instance used by the test to check whether a pong can be received.
     */
    private DataCapturingService dcs;
    /**
     * The {@link Context} required to send unique broadcasts and to start the capturing service.
     */
    private Context context;

    /**
     * Sets up all the instances required by all tests in this test class.
     *
     */
    @Before
    public void setUp() {
        lock = new ReentrantLock();
        condition = lock.newCondition();
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final String appId = context.getPackageName();
        oocut = new PongReceiver(context, MessageCodes.getPingActionId(appId), MessageCodes.getPongActionId(appId));
    }

    @After
    public void tearDown() {
        clearPersistenceLayer(context, context.getContentResolver(), AUTHORITY);
    }

    /**
     * Tests the ping pong with a running service. In that case it should successfully finish one round of ping/pong
     * with that service.
     *
     * @throws MissingPermissionException Should not happen, since there is a JUnit rule to prevent it.
     * @throws DataCapturingException If data capturing was not possible after starting the service.
     * @throws NoSuchMeasurementException If the service lost track of the measurement.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test
    public void testWithRunningService() throws MissingPermissionException, DataCapturingException,
            NoSuchMeasurementException, CursorIsNullException, CorruptedMeasurementException {

        // Arrange
        // Instantiate DataCapturingService
        final DataCapturingListener testListener = new TestListener();
        // The LOGIN_ACTIVITY is normally set to the LoginActivity of the SDK implementing app
        CyfaceAuthenticator.LOGIN_ACTIVITY = AccountAuthenticatorActivity.class;
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                dcs = new CyfaceDataCapturingService(context, context.getContentResolver(), TestUtils.AUTHORITY,
                        TestUtils.ACCOUNT_TYPE, "https://fake.fake/", new IgnoreEventsStrategy(), testListener,
                        100);
            } catch (SetupException | CursorIsNullException e) {
                throw new IllegalStateException(e);
            }
        });

        // Start Capturing
        StartUpFinishedHandler finishedHandler = new TestStartUpFinishedHandler(lock, condition,
                MessageCodes.getServiceStartedActionId(context.getPackageName()));
        dcs.start(Modality.UNKNOWN, finishedHandler);

        // Give the async start some time to start the DataCapturingBackgroundService
        lock.lock();
        try {
            condition.await(TIMEOUT_TIME, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }

        // Act
        // Check if DataCapturingBackgroundService is running
        TestCallback testCallback = new TestCallback("testWithRunningService", lock, condition);
        oocut.checkIsRunningAsync(TIMEOUT_TIME, TimeUnit.SECONDS, testCallback);

        // Give the async call some time
        lock.lock();
        try {
            condition.await(2 * TIMEOUT_TIME, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }

        // Assert
        // Ensure DataCapturingBackgroundService was running during the async check
        assertThat(testCallback.wasRunning(), is(equalTo(true)));
        assertThat(testCallback.didTimeOut(), is(equalTo(false)));

        // Cleanup
        // Stop Capturing
        TestShutdownFinishedHandler shutdownHandler = new TestShutdownFinishedHandler(lock, condition,
                MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED);
        dcs.stop(shutdownHandler);

        // Give the async stop some time to stop gracefully
        lock.lock();
        try {
            condition.await(TIMEOUT_TIME, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Tests that the {@link PongReceiver} works without crashing as expected even when no service runs.
     */
    @Test
    public void testWithNonRunningService() {

        // Act
        // Check if DataCapturingBackgroundService is running
        TestCallback testCallback = new TestCallback("testWithNonRunningService", lock, condition);
        oocut.checkIsRunningAsync(TIMEOUT_TIME, TimeUnit.SECONDS, testCallback);

        // Give the async call some time
        lock.lock();
        try {
            condition.await(2 * TIMEOUT_TIME, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }

        // Assert
        // Ensure DataCapturingBackgroundService was running during the async check
        assertThat(testCallback.didTimeOut(), is(equalTo(true)));
        assertThat(testCallback.wasRunning(), is(equalTo(false)));
    }
}
