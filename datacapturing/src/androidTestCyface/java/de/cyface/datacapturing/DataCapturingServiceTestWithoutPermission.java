package de.cyface.datacapturing;

import static de.cyface.datacapturing.ServiceTestUtils.ACCOUNT_TYPE;
import static de.cyface.datacapturing.ServiceTestUtils.AUTHORITY;
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

import android.content.ContentResolver;
import android.content.Context;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.MediumTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.exception.MissingPermissionException;
import de.cyface.datacapturing.exception.SetupException;
import de.cyface.datacapturing.ui.UIListener;
import de.cyface.persistence.model.Vehicle;

/**
 * Checks if missing permissions are correctly detected before starting a service.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
@FlakyTest
@Ignore // Ignore this test until Android is capable of resetting permissions for every test
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
     * Initializes the object of class under test.
     */
    @Before
    public void setUp() {
        final Context context = InstrumentationRegistry.getTargetContext();
        final ContentResolver contentResolver = context.getContentResolver();
        final String dataUploadServerAddress = "https://localhost:8080";
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                try {
                    oocut = new CyfaceDataCapturingService(context, contentResolver, AUTHORITY, ACCOUNT_TYPE,
                            dataUploadServerAddress, new IgnoreEventsStrategy());
                } catch (SetupException e) {
                    throw new IllegalStateException(e);
                }
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
    public void testServiceDoesNotStartWithoutPermission() throws MissingPermissionException, DataCapturingException {
        final TestStartUpFinishedHandler startUpFinishedHandler = new TestStartUpFinishedHandler(lock, condition);
        oocut.startAsync(new TestListener(lock, condition), Vehicle.UNKNOWN, startUpFinishedHandler);
        // FIXME: if the test fails we might need to wait a bit as we're not async
    }

    /**
     * Tests whether a set {@link UIListener} is correctly informed about a missing permission.
     */
    @Test
    public void testUIListenerIsInformedOfMissingPermission() {
        TestUIListener uiListener = new TestUIListener();
        oocut.setUiListener(uiListener);

        boolean exceptionCaught = false;
        try {
            final TestStartUpFinishedHandler startUpFinishedHandler = new TestStartUpFinishedHandler(lock, condition);
            oocut.startAsync(new TestListener(lock, condition), Vehicle.UNKNOWN, startUpFinishedHandler);
        } catch (DataCapturingException | MissingPermissionException e) {
            assertThat(uiListener.requiredPermission, is(equalTo(true)));
            exceptionCaught = true;
        }
        assertThat(exceptionCaught, is(equalTo(true)));
    }

}
