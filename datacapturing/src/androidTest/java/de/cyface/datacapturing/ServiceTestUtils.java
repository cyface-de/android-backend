package de.cyface.datacapturing;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;

import de.cyface.persistence.AccelerationPointTable;
import de.cyface.persistence.DirectionPointTable;
import de.cyface.persistence.GpsPointsTable;
import de.cyface.persistence.MeasurementTable;
import de.cyface.persistence.RotationPointTable;

/**
 * A class containing static utility functions, encapsulating often used calls.
 *
 * @author Klemens Muthmann
 * @version 1.1.2
 * @since 2.0.0
 */
public class ServiceTestUtils {
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
    public static final String ACCOUNT_TYPE = "de.cyface.datacapturing.test";
    public final static String DEFAULT_FREE_USERNAME = "playStoreBeta";
    public final static String DEFAULT_FREE_PASSWORD = "playStoreBeta@Cy";

    /**
     * Private constructor so no one tries to instantiate the utility class.
     */
    private ServiceTestUtils() {
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

    public static Uri getMeasurementUri() {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(MeasurementTable.URI_PATH).build();
    }

    public static Uri getGeoLocationsUri() {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(GpsPointsTable.URI_PATH).build();
    }

    public static Uri getAccelerationsUri() {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(AccelerationPointTable.URI_PATH)
                .build();
    }

    public static Uri getRotationsUri() {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(RotationPointTable.URI_PATH).build();
    }

    public static Uri getDirectionsUri() {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(DirectionPointTable.URI_PATH)
                .build();
    }
}
