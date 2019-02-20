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
 * @version 7.1.0
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
     * @throws SetupException If writing the components preferences or registering the dummy user account fails.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    private CyfaceDataCapturingService(final @NonNull Context context, final @NonNull ContentResolver resolver,
            final @NonNull String authority, final @NonNull String accountType,
            final @NonNull String dataUploadServerAddress, final @NonNull EventHandlingStrategy eventHandlingStrategy,
            @NonNull final DistanceCalculationStrategy distanceCalculationStrategy)
            throws SetupException, CursorIsNullException {
        super(context, authority, accountType, dataUploadServerAddress, eventHandlingStrategy,
                new PersistenceLayer<>(context, resolver, authority, new CapturingPersistenceBehaviour()),
                distanceCalculationStrategy);
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
     * @throws SetupException If writing the components preferences or registering the dummy user account fails.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @SuppressWarnings("WeakerAccess") // This is the constructor used by SDK implementing apps
    public CyfaceDataCapturingService(final @NonNull Context context, final @NonNull ContentResolver resolver,
            final @NonNull String authority, final @NonNull String accountType,
            final @NonNull String dataUploadServerAddress, final @NonNull EventHandlingStrategy eventHandlingStrategy)
            throws SetupException, CursorIsNullException {
        this(context, resolver, authority, accountType, dataUploadServerAddress, eventHandlingStrategy,
                new DefaultDistanceCalculationStrategy());
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
     * For documentation see {@link DataCapturingService#start(DataCapturingListener, Vehicle, StartUpFinishedHandler)}.
     * <p>
     * This wrapper was requested by RM to avoid an unrecoverable state after the app crashed with an
     * un{@link MeasurementStatus#FINISHED} {@link Measurement}.
     * <p>
     * This wrapper deletes "dead" {@link MeasurementStatus#OPEN} measurements because the {@link Point3d} counts
     * was lost during app crash. "Dead" {@link MeasurementStatus#PAUSED} measurements are marked as {@code FINISHED}.
     */
    @Override
    public void start(@NonNull DataCapturingListener listener, @NonNull Vehicle vehicle,
            @NonNull StartUpFinishedHandler finishedHandler)
            throws DataCapturingException, MissingPermissionException, CursorIsNullException {

        try {
            super.start(listener, vehicle, finishedHandler);
        } catch (final CorruptedMeasurementException e) {
            final List<Measurement> openMeasurements = this.persistenceLayer.loadMeasurements(MeasurementStatus.OPEN);
            for (final Measurement measurement : openMeasurements) {
                Log.w(TAG, "Deleting dead open measurement (mid " + measurement.getIdentifier() + ").");
                this.persistenceLayer.delete(measurement.getIdentifier());
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
                super.start(listener, vehicle, finishedHandler);
            } catch (final CorruptedMeasurementException e1) {
                throw new IllegalStateException(e1);
            }
        }
    }
}
