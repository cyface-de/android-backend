/**
 * This package contains classes for running the data capturing background service. The actual Android service is
 * implemented by {@link de.cyface.datacapturing.backend.DataCapturingBackgroundService}. All other classes are there to
 * support that service. Most of the business logic is implemented by the
 * {@link de.cyface.datacapturing.backend.CapturingProcess} and
 * {@link de.cyface.datacapturing.backend.GeoLocationDeviceStatusHandler}. The former reads actual sensor values while the latter
 * checks whether location fix is available or not.
 * 
 * @author Klemens Muthmann
 * @version 1.0.1
 * @since 2.0.0
 */
package de.cyface.datacapturing.backend;