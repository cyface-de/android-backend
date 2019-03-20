// FIXME
package de.cyface.synchronization;

import static de.cyface.synchronization.TestUtils.ACCOUNT_TYPE;
import static de.cyface.synchronization.TestUtils.AUTHORITY;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.accounts.Account;
import android.content.Context;
import android.net.ConnectivityManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import de.cyface.utils.Validate;

/**
 * Tests the correct functionality of the {@link WiFiSurveyor} class.
 * <p>
 * The tests in this class require an emulator or a real device.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 4.0.0
 */
@RunWith(AndroidJUnit4.class)
public class WifiSurveyorTest {

    /**
     * An object of the class under test.
     */
    private WiFiSurveyor testedObject;

    /**
     * Initializes the properties for each test case individually.
     */
    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ConnectivityManager connectivityManager = (ConnectivityManager)context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        Validate.notNull(connectivityManager);
        testedObject = new WiFiSurveyor(context, connectivityManager, AUTHORITY, ACCOUNT_TYPE);
    }

    /**
     * Tests that marking the connection as syncable using the periodicSync flag on the account works.
     * <p>
     * This test reproduced MOV-635 where the periodic sync flag did not change.
     * This bug was only reproducible in integration environment (device and emulator) but not as roboelectric test.
     *
     * @throws SynchronisationException This should not happen in the test environment. Occurs if no Android
     *             <code>Context</code> is available.
     */
    @Test
    public void testSetPeriodicSyncEnabled() throws SynchronisationException {

        // Arrange
        Account account = testedObject.createAccount("test", null);
        testedObject.startSurveillance(account);
        Validate.isTrue(!testedObject.isPeriodicSyncEnabled()); // Default state

        // Act & Assert 1
        testedObject.setPeriodicSyncEnabled(true);
        assertThat(testedObject.isPeriodicSyncEnabled(), is(equalTo(true)));

        // Act & Assert 2
        testedObject.setPeriodicSyncEnabled(false);
        assertThat(testedObject.isPeriodicSyncEnabled(), is(equalTo(false)));
    }
}
