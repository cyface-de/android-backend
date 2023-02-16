/*
 * Copyright 2021 Cyface GmbH
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
package de.cyface.persistence.v1.model;

/**
 * Status which defines whether a {@link Measurement} is still capturing data({@link #OPEN}), {@link #PAUSED},
 * {@link #FINISHED}, {@link #SKIPPED or {@link #SYNCED}.
 *
 * Usually only one {@code Measurement} should be {@link #OPEN} or {@link #PAUSED}; else there has been some error.
 *
 * @author Armin Schnabel
 * @version 2.2.0
 * @since 3.0.0
 */
public enum MeasurementStatus {
    /**
     * This state defines that a {@link Measurement} is currently active.
     */
    OPEN("OPEN"),
    /**
     * This state defines that an active {@link Measurement} was paused and not yet {@link #FINISHED} or resumed.
     */
    PAUSED("PAUSED"),
    /**
     * This state defines that a {@link Measurement} has been completed and was not yet {@link #SYNCED}.
     */
    FINISHED("FINISHED"),
    /**
     * This state defines that a {@link Measurement} has been synchronized.
     */
    SYNCED("SYNCED"),
    /**
     * This state defines that a {@link Measurement} was rejected by the API and won't be uploaded.
     */
    SKIPPED("SKIPPED"),
    /**
     * This state defines that a {@link Measurement} is no longer supported (to be synced/resumed).
     */
    DEPRECATED("DEPRECATED");

    private final String databaseIdentifier;

    MeasurementStatus(final String databaseIdentifier) {
        this.databaseIdentifier = databaseIdentifier;
    }

    public String getDatabaseIdentifier() {
        return databaseIdentifier;
    }
}