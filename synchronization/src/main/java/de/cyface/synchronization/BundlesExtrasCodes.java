/*
 * Copyright 2017 Cyface GmbH
 *
 * This file is part of the Cyface SDK for Android.
 *
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.synchronization;

import static de.cyface.synchronization.Constants.TAG;

/**
 * A utility class collecting all codes identifying extras used to transmit data via bundles in this application.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.7.0
 * @since 2.1.0
 */
public class BundlesExtrasCodes {

    /**
     * Code that identifies the extra transmitted to the background service to tell it which measurement to capture.
     */
    public final static String MEASUREMENT_ID = "de.cyface.extra.mid";
    /**
     * Code that identifies the extra transmitted between ping and pong messages to associated a ping with a pong, while
     * checking whether the service is running.
     */
    public final static String PING_PONG_ID = "de.cyface.pingpong.id";
    /**
     * Code that identifies the authority id if transmitted via an Android bundle.
     */
    public final static String AUTHORITY_ID = "de.cyface.authority.id";
    /**
     * Code that identifies the status information in the message send when the data capturing service has been stopped.
     * This should be a boolean extra that is <code>false</code> if the service was not actually stopped (if stop is
     * called twice in succession) or <code>true</code> is stopping was successful. In the first case the intent should
     * also contain the measurement id as an extra identified by {@link #MEASUREMENT_ID}.
     */
    public final static String STOPPED_SUCCESSFULLY = "de.cyface.extra.stopped_successfully";
    /**
     * Code that identifies the {@code EventHandlingStrategy} if transmitted via an Android bundle.
     */
    public final static String EVENT_HANDLING_STRATEGY_ID = "de.cyface.event_handling_strategy.id";
    /**
     * Code that identifies the {@code DistanceCalculationStrategy} if transmitted via an Android bundle.
     */
    public final static String DISTANCE_CALCULATION_STRATEGY_ID = "de.cyface.distance_calculation_strategy.id";
    /**
     * Code that identifies the {@code LocationCleaningStrategy} if transmitted via an Android bundle.
     */
    public final static String LOCATION_CLEANING_STRATEGY_ID = "de.cyface.location_cleaning_strategy.id";
    /**
     * Code that identifies the percentage of the {@link CyfaceConnectionStatusListener#SYNC_PROGRESS}.
     */
    public final static String SYNC_PERCENTAGE_ID = TAG + ".percentage";
    /**
     * Code that identifies the extra transmitted to the background service to tell it which sensor frequency to use.
     */
    public final static String SENSOR_FREQUENCY = "de.cyface.extra.sensor_frequency";

    /**
     * Constructor is private to prevent creation of utility class.
     */
    private BundlesExtrasCodes() {
        // Nothing to do here.
    }
}
