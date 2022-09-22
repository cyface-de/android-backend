/*
 * Copyright 2018-2022 Cyface GmbH
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

import static de.cyface.datacapturing.TestUtils.ACCOUNT_TYPE;
import static de.cyface.datacapturing.TestUtils.AUTHORITY;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.accounts.AccountAuthenticatorActivity;
import android.content.ContentResolver;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import de.cyface.datacapturing.exception.CorruptedMeasurementException;
import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.exception.MissingPermissionException;
import de.cyface.datacapturing.exception.SetupException;
import de.cyface.datacapturing.ui.UIListener;
import de.cyface.persistence.model.Modality;
import de.cyface.synchronization.CyfaceAuthenticator;
import de.cyface.utils.CursorIsNullException;

/**
 * Checks if missing permissions are correctly detected before starting a service.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.1.8
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
@FlakyTest
@Ignore("Ignore this test until Android is capable of resetting permissions for every test")
public class DataCapturingServiceTestWithoutPermission {

    /**
     * An object of the class under test.
     */
    private DataCapturingService oocut;
    /**
     * Lock used to synchronize with the background service.
     */
    private Lock lock;
    /**
     * Condition waiting for the background service to wake up this test case.
     */
    private Condition condition;
    /**
     * The {@link Context} needed to access the persistence layer
     */
    private Context context;

    /**
     * Initializes the object of class under test.
     */
    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final ContentResolver contentResolver = context.getContentResolver();

        // The LOGIN_ACTIVITY is normally set to the LoginActivity of the SDK implementing app
        CyfaceAuthenticator.LOGIN_ACTIVITY = AccountAuthenticatorActivity.class;

        final String dataUploadServerAddress = "https://localhost:8080";
        final DataCapturingListener listener = new TestListener();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                oocut = new CyfaceDataCapturingService(context, contentResolver, AUTHORITY, ACCOUNT_TYPE,
                        dataUploadServerAddress, new IgnoreEventsStrategy(), listener, 100);
            } catch (SetupException | CursorIsNullException e) {
                throw new IllegalStateException(e);
            }
        });
        lock = new ReentrantLock();
        condition = lock.newCondition();
    }

    /**
     * Tests that the service correctly throws an <code>Exception</code> if no <code>ACCESS_FINE_LOCATION</code> was
     * granted.
     *
     * @throws MissingPermissionException The expected <code>Exception</code> thrown if the
     *             <code>ACCESS_FINE_LOCATION</code> is missing.
     * @throws DataCapturingException If the asynchronous background service did not start successfully.
     */
    @Test(expected = MissingPermissionException.class)
    public void testServiceDoesNotStartWithoutPermission() throws MissingPermissionException, DataCapturingException,
            CursorIsNullException, CorruptedMeasurementException {
        final TestStartUpFinishedHandler startUpFinishedHandler = new TestStartUpFinishedHandler(lock, condition,
                MessageCodes.GLOBAL_BROADCAST_SERVICE_STARTED);
        oocut.start(Modality.UNKNOWN, startUpFinishedHandler);
        // if the test fails we might need to wait a bit as we're async
    }

    /**
     * Tests whether a set {@link UIListener} is correctly informed about a missing permission.
     */
    @Test
    public void testUIListenerIsInformedOfMissingPermission()
            throws CursorIsNullException, CorruptedMeasurementException {
        TestUIListener uiListener = new TestUIListener();
        oocut.setUiListener(uiListener);

        boolean exceptionCaught = false;
        try {
            final TestStartUpFinishedHandler startUpFinishedHandler = new TestStartUpFinishedHandler(lock, condition,
                    MessageCodes.GLOBAL_BROADCAST_SERVICE_STARTED);
            oocut.start(Modality.UNKNOWN, startUpFinishedHandler);
        } catch (DataCapturingException | MissingPermissionException e) {
            assertThat(uiListener.requiredPermission, is(equalTo(true)));
            exceptionCaught = true;
        }
        assertThat(exceptionCaught, is(equalTo(true)));
    }
}
