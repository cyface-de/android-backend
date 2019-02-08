package de.cyface.datacapturing;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;
import de.cyface.persistence.model.GeoLocation;

/**
 * A class containing static utility functions, encapsulating often used calls.
 *
 * @author Armin Schnabel
 * @version 1.1.7
 * @since 2.0.0
 */
public class TestUtils {

    /**
     * The tag used to identify log messages send to logcat.
     */
    public static final String TAG = Constants.TAG + ".test";
    /**
     * The authority used to identify the content provider for testing.
     */
    public static final String AUTHORITY = "de.cyface.datacapturing.test.provider";
    /**
     * Account type used by all accounts created during testing.
     */
    static final String ACCOUNT_TYPE = "de.cyface.datacapturing.test";
    static final String DEFAULT_USERNAME = "admin";
    static final String DEFAULT_PASSWORD = "secret";
    /**
     * The following constants were selected so that adding each base+constant results in coordinates with approximately
     * 1 meter distance between base coordinates and base+1*constant coordinates
     */
    private final static double BASE_LAT = 51.100;
    /**
     * see {@link #BASE_LAT}
     */
    private final static double BASE_LON = 13.100;
    /**
     * see {@link #BASE_LAT}
     */
    private final static double LAT_CONSTANT = 0.000008993199995;
    /**
     * see {@link #BASE_LAT}
     */
    private final static double LON_CONSTANT = 0.0000000270697;

    /**
     * Private constructor so no one tries to instantiate the utility class.
     */
    private TestUtils() {
        // Nothing to do here.
    }

    /**
     * Locks the test and waits until the timeout is reached or a signal to continue execution is received. NEVER do
     * call this on the main thread (e.g. <code>Instrumentation#runOnMainSync(Runnable)</code>) or you will receive an
     * Application Not Responding (ANR) error.
     * 
     * @param time The time to wait until the test lock is released and the test continues even if no signal was issued.
     * @param unit The unit of <code>time</code>. For example seconds or milliseconds.
     * @param lock Used to lock the calling process.
     * @param condition Used to wait for a signal to continue execution.
     */
    static void lockAndWait(final long time, final @NonNull TimeUnit unit, final @NonNull Lock lock,
            final @NonNull Condition condition) {
        lock.lock();
        try {
            condition.await(time, unit);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks for the current isRunning status of the object of class under test and runs that on the main thread.
     *
     * @param oocut The current <code>DataCapturingService</code> as object of class under test.
     * @param runningStatusCallback The callback to tell about the received service status.
     */
    static void callCheckForRunning(final @NonNull DataCapturingService oocut,
            final @NonNull IsRunningCallback runningStatusCallback) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                oocut.isRunning(1, TimeUnit.SECONDS, runningStatusCallback);
            }
        });
    }

    /**
     * Generates {@link GeoLocation}s with coordinates for testing.
     * <p>
     * See {@param relativeDistance} if you need locations with a specific distance from each other.
     *
     * @param relativeDistance an integer which defines how much the generated {@code GeoLocation}s are away from each
     *            other. E.g.: If you generate {@code GeoLocation}s using {@param relativeDistance} 1, 3, 5 the
     *            generated locations will be approximately 1 meter per {@param relativeDistance}-difference away from
     *            each other. In this case (1, 5) = 4m distance and (1, 3) or (3, 5) = 2m distance.
     * @return the generated {@code GeoLocation}
     */
    static GeoLocation generateGeoLocation(final int relativeDistance) {
        final double salt = Math.random();
        return new GeoLocation(BASE_LAT + relativeDistance * LAT_CONSTANT, BASE_LON + relativeDistance * LON_CONSTANT,
                1000000000L + relativeDistance * 1000L, salt * 15.0, (float)salt * 30f);
    }
}
