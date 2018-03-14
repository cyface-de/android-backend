/**
 * This package contains model classes required by the data capturing service to represent the captured data.
 * <p>
 * There is a {@link de.cyface.datacapturing.model.CapturedData} object, that represents the captured 3D data points
 * ({@link de.cyface.datacapturing.model.Point3D}). They are
 * {@link de.cyface.datacapturing.model.DataPoint}s captured at a certain point in time identified by a timestamp in
 * milliseconds since 1.1.1970.
 * <p>
 * {@link de.cyface.datacapturing.model.GeoLocation} is a single captured geo location with all associated data.
 * 
 * @author Klemens Muthmann
 * @version 2.0.0
 * @since 2.0.0
 */
package de.cyface.datacapturing.model;