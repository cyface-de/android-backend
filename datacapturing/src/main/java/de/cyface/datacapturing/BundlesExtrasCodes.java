package de.cyface.datacapturing;

import android.content.Context;
import android.content.Intent;
import de.cyface.datacapturing.backend.DataCapturingBackgroundService;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;

/**
 * A utility class collecting all codes identifying extras used to transmit data via bundles in this application.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.3.0
 * @since 2.1.0
 */
public class BundlesExtrasCodes {
    /**
     * Code that identifies the extra transmitted to the background service to tell it which measurement to capture.
     */
    public static final String MEASUREMENT_ID = "de.cyface.extra.mid";
    /**
     * Code that identifies the extra transmitted between ping and pong messages to associated a ping with a pong, while
     * checking whether the service is running.
     */
    public static final String PING_PONG_ID = "de.cyface.pingpong.id";
    /**
     * Code that identifies the authority id if transmitted via an Android bundle.
     */
    public static final String AUTHORITY_ID = "de.cyface.authority.id";
    /**
     * Code that identifies the status information in the message send when the data capturing service has been stopped.
     * This should be a boolean extra that is <code>false</code> if the service was not actually stopped (if stop is
     * called twice in succession) or <code>true</code> is stopping was successful. In the first case the intent should
     * also contain the measurement id as an extra identified by {@link #MEASUREMENT_ID}.
     */
    public static final String STOPPED_SUCCESSFULLY = "de.cyface.extra.stopped_successfully";
    /**
     * Code that identifies the {@link EventHandlingStrategy} if transmitted via an Android bundle.
     */
    public static final String EVENT_HANDLING_STRATEGY_ID = "de.cyface.event_handling_strategy.id";
    /**
     * Code that identifies {@link Context#startService(Intent)} calls used to stop the service.
     * <p>
     * The goal is to use {@code Context#startService(Intent)} for all messages from the {@link DataCapturingService} to
     * the {@link DataCapturingBackgroundService}. TODO [CY-4097]: register client is still sent differently
     * <p>
     * for all the status of the {@link Measurement} which is currently captured. This is used to tell the
     * {@link DataCapturingBackgroundService} when calling {@link Context#stopService(Intent)} if it should set the
     * {@link MeasurementStatus} to {@link MeasurementStatus#FINISHED} or {@link MeasurementStatus#PAUSED}.
     */
    public static final String ACTION_STOP_SERVICE = "de.cyface.extra.action_stop_service";
    /**
     * Code that identifies the status of the {@link Measurement} which is currently captured. This is used to tell the
     * {@link DataCapturingBackgroundService} when calling {@link Context#stopService(Intent)} if it should set the
     * {@link MeasurementStatus} to {@link MeasurementStatus#FINISHED} or {@link MeasurementStatus#PAUSED}.
     */
    public static final String SET_PAUSED = "de.cyface.extra.set_paused";

    /**
     * Constructor is private to prevent creation of utility class.
     */
    private BundlesExtrasCodes() {
        // Nothing to do here.
    }
}
