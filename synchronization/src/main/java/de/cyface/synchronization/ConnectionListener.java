package de.cyface.synchronization;

/**
 * Listener interface for connection information. Interested parties can subscribe to synchronization
 * status updates.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 1.0.2
 * @since 1.0.0
 */
public interface ConnectionListener {

    void onSyncFinished();

    void onProgressInfo(long transmittedPoints, long totalPoints);

    void onDisconnected(String exceptionType, String errorMessage);

    void onSyncStarted();
}