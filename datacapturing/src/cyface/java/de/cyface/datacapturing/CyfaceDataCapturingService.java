package de.cyface.datacapturing;

import static de.cyface.datacapturing.Constants.TAG;
import static de.cyface.synchronization.CyfaceAuthenticator.LOGIN_ACTIVITY;

import java.util.List;

import android.accounts.Account;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import de.cyface.datacapturing.backend.DataCapturingBackgroundService;
import de.cyface.datacapturing.exception.CorruptedMeasurementException;
import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.exception.MissingPermissionException;
import de.cyface.datacapturing.exception.SetupException;
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour;
import de.cyface.datacapturing.ui.UIListener;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.Point3d;
import de.cyface.persistence.model.Vehicle;
import de.cyface.synchronization.SynchronisationException;
import de.cyface.utils.CursorIsNullException;

/**
 * An implementation of a <code>DataCapturingService</code> using a dummy Cyface account for data synchronization.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 9.0.0
 * @since 2.0.0
 */
public final class CyfaceDataCapturingService extends DataCapturingService {

    /**
     * Creates a new completely initialized {@link DataCapturingService}.
     *
     * @param context The context (i.e. <code>Activity</code>) handling this service.
     * @param resolver Resolver used to access the content provider for storing measurements.
     * @param authority The <code>ContentProvider</code> authority used to identify the content provider used by this
     *            <code>DataCapturingService</code>. You should use something world wide unique, like your domain, to
     *            avoid collisions between different apps using the Cyface SDK.
     * @param accountType The type of the account to use to synchronize data.
     * @param dataUploadServerAddress The server address running an API that is capable of receiving data captured by
     *            this service.
     * @param eventHandlingStrategy The {@link EventHandlingStrategy} used to react to selected events
     *            triggered by the {@link DataCapturingBackgroundService}.
     * @param distanceCalculationStrategy The {@link DistanceCalculationStrategy} used to calculate the
     *            {@link Measurement#distance}
     * @param capturingListener A {@link DataCapturingListener} that is notified of important events during data
     *            capturing.
     * @throws SetupException If writing the components preferences or registering the dummy user account fails.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    private CyfaceDataCapturingService(@NonNull final Context context, @NonNull final ContentResolver resolver,
            @NonNull final String authority, @NonNull final String accountType,
            @NonNull final String dataUploadServerAddress, @NonNull final EventHandlingStrategy eventHandlingStrategy,
            @NonNull final DistanceCalculationStrategy distanceCalculationStrategy,
            @NonNull final DataCapturingListener capturingListener) throws SetupException, CursorIsNullException {
        super(context, authority, accountType, dataUploadServerAddress, eventHandlingStrategy,
                new PersistenceLayer<>(context, resolver, authority, new CapturingPersistenceBehaviour()),
                distanceCalculationStrategy, capturingListener);
        if (LOGIN_ACTIVITY == null) {
            throw new IllegalStateException("No LOGIN_ACTIVITY was set from the SDK using app.");
        }
    }

    /**
     * Creates a new completely initialized {@link DataCapturingService}.
     *
     * @param context The context (i.e. <code>Activity</code>) handling this service.
     * @param resolver Resolver used to access the content provider for storing measurements.
     * @param authority The <code>ContentProvider</code> authority used to identify the content provider used by this
     *            <code>DataCapturingService</code>. You should use something world wide unique, like your domain, to
     *            avoid collisions between different apps using the Cyface SDK.
     * @param accountType The type of the account to use to synchronize data.
     * @param dataUploadServerAddress The server address running an API that is capable of receiving data captured by
     *            this service.
     * @param eventHandlingStrategy The {@link EventHandlingStrategy} used to react to selected events
     *            triggered by the {@link DataCapturingBackgroundService}.
     * @param capturingListener A {@link DataCapturingListener} that is notified of important events during data
     *            capturing.
     * @throws SetupException If writing the components preferences or registering the dummy user account fails.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @SuppressWarnings("WeakerAccess") // This is the constructor used by SDK implementing apps
    public CyfaceDataCapturingService(@NonNull final Context context, @NonNull final ContentResolver resolver,
            @NonNull final String authority, @NonNull final String accountType,
            @NonNull final String dataUploadServerAddress, @NonNull final EventHandlingStrategy eventHandlingStrategy,
            @NonNull final DataCapturingListener capturingListener) throws SetupException, CursorIsNullException {
        this(context, resolver, authority, accountType, dataUploadServerAddress, eventHandlingStrategy,
                new DefaultDistanceCalculationStrategy(), capturingListener);
    }

    /**
     * Frees up resources used by CyfaceDataCapturingService
     * 
     * @throws SynchronisationException if no current Android {@link Context} is available
     */
    @SuppressWarnings("unused") // This is called by the SDK implementing app in its onDestroyView
    public void shutdownDataCapturingService() throws SynchronisationException {

        getWiFiSurveyor().stopSurveillance();
        shutdownConnectionStatusReceiver();
    }

    /**
     * Starts a <code>WifiSurveyor</code>. A synchronization account must be available at that time.
     *
     * @throws SetupException when no account is available.
     */
    @SuppressWarnings("unused") // This is called by the SDK implementing app after an account was created
    public void startWifiSurveyor() throws SetupException {
        try {
            // We require SDK users (other than Movebis) to always have exactly one account available
            final Account account = getWiFiSurveyor().getAccount();
            getWiFiSurveyor().startSurveillance(account);
        } catch (SynchronisationException e) {
            throw new SetupException(e);
        }
    }

    /**
     * Starts the capturing process with a {@link DataCapturingListener}, that is notified of important events occurring
     * while the capturing process is running.
     * <p>
     * This is an asynchronous method. This method returns as soon as starting the service was initiated. You may not
     * assume the service is running, after the method returns. Please use the {@link StartUpFinishedHandler} to receive
     * a callback, when the service has been started.
     * <p>
     * This method is thread safe to call.
     * <p>
     * <b>ATTENTION:</b> If there are errors while starting the service, your handler might never be called. You may
     * need to apply some timeout mechanism to not wait indefinitely.
     * <p>
     * This wrapper avoids an unrecoverable state after the app crashed with an un{@link MeasurementStatus#FINISHED}
     * {@link Measurement}. It deletes the {@link Point3d}s of "dead" {@link MeasurementStatus#OPEN} measurements
     * because the {@link Point3d} counts gets lost during app crash. "Dead" {@code MeasurementStatus#OPEN} and
     * {@link MeasurementStatus#PAUSED} measurements are then marked as {@code FINISHED}.
     *
     * @param vehicle The {@link Vehicle} used to capture this data. If you have no way to know which kind of
     *            <code>Vehicle</code> was used, just use {@link Vehicle#UNKNOWN}.
     * @param finishedHandler A handler called if the service started successfully.
     * @throws DataCapturingException If the asynchronous background service did not start successfully or no valid
     *             Android context was available.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     * @throws MissingPermissionException If no Android <code>ACCESS_FINE_LOCATION</code> has been granted. You may
     *             register a {@link UIListener} to ask the user for this permission and prevent the
     *             <code>Exception</code>. If the <code>Exception</code> was thrown the service does not start.
     */
    @Override
    @SuppressWarnings("unused") // This is called by the SDK implementing app to start a measurement
    public void start(@NonNull Vehicle vehicle, @NonNull StartUpFinishedHandler finishedHandler)
            throws DataCapturingException, MissingPermissionException, CursorIsNullException {

        try {
            super.start(vehicle, finishedHandler);
        } catch (final CorruptedMeasurementException e) {
            final List<Measurement> openMeasurements = this.persistenceLayer.loadMeasurements(MeasurementStatus.OPEN);
            for (final Measurement measurement : openMeasurements) {
                Log.w(TAG, "Cleaning and finishing dead open measurement (mid " + measurement.getIdentifier() + ").");
                this.persistenceLayer.deletePoint3dData(measurement.getIdentifier());
                try {
                    this.persistenceLayer.setStatus(measurement.getIdentifier(), MeasurementStatus.FINISHED);
                } catch (NoSuchMeasurementException e1) {
                    throw new IllegalStateException(e);
                }
            }
            final List<Measurement> pausedMeasurements = this.persistenceLayer
                    .loadMeasurements(MeasurementStatus.PAUSED);
            for (final Measurement measurement : pausedMeasurements) {
                Log.w(TAG, "Finishing dead paused measurement (mid " + measurement.getIdentifier() + ").");
                try {
                    this.persistenceLayer.setStatus(measurement.getIdentifier(), MeasurementStatus.FINISHED);
                } catch (NoSuchMeasurementException e1) {
                    throw new IllegalStateException(e);
                }
            }

            // Now try again to start Capturing - now there can't be any corrupted measurements
            try {
                super.start(vehicle, finishedHandler);
            } catch (final CorruptedMeasurementException e1) {
                throw new IllegalStateException(e1);
            }
        }
    }
}
