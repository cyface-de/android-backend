/**
 * This package contains all classes and code required for synchronization.
 * <p>
 * It provides a stub authenticator for default account realised as described in the
 * <a href="https://developer.android.com/training/sync-adapters/creating-authenticator.html">Android documentation</a>
 * with an {@link de.cyface.synchronization.AuthenticatorService} and the actual
 * {@link de.cyface.synchronization.CyfaceAuthenticator}.
 * <p>
 * It also provides a synchronization adapter as described in the
 * <a href="https://developer.android.com/training/sync-adapters/creating-sync-adapter.html">Android documentation</a>.
 * The synchronization adapter is the service running in the background to upload data to a server. It is realised by
 * the two classes {@link de.cyface.synchronization.SyncService} and
 * {@link de.cyface.synchronization.SyncAdapter}. The former is required to run the adapter independent of the
 * application using the framework, while the later does the actual synchronisation work.
 * The {@code ContentProvider} required by the synchronisation adapter is the
 * {@link de.cyface.persistence.content.StubProvider} provided via the persistence module, which is not used to access data.
 * <p>
 * The {@link de.cyface.synchronization.SyncPerformer} is a class containing the actual synchronisation code.
 * {@link de.cyface.persistence.serialization.MeasurementSerializer} transforms data from the
 * {@link de.cyface.persistence.PersistenceLayer} into the Cyface binary format.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.0.2
 * @since 2.0.0
 */
package de.cyface.synchronization;