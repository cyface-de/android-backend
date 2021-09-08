/*
 * Copyright 2019 Cyface GmbH
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
package de.cyface.persistence;

import android.content.ContentProvider;
import androidx.annotation.NonNull;

import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.Modality;
import de.cyface.utils.CursorIsNullException;

/**
 * The {@link PersistenceBehaviour} defines how the {@link PersistenceLayer} works. Select a behaviour depending on
 * if you want to use the {@code PersistenceLayer} to capture a new {@link Measurement} or to load existing data.
 *
 * @author Armin Schnabel
 * @version 1.0.4
 * @since 3.0.0
 */
public interface PersistenceBehaviour {

    /**
     * This is called in the {@code Persistence}'s constructor.
     */
    void onStart(@NonNull PersistenceLayer persistenceLayer);

    /**
     * This is called after a {@link PersistenceLayer#newMeasurement(Modality)} was created.
     * 
     * @param measurementId The id of the recently created {@link Measurement}
     */
    void onNewMeasurement(long measurementId);

    /**
     * This is called when the {@link PersistenceLayer} is no longer needed by {@link PersistenceLayer#shutdown()}.
     */
    void shutdown();

    /**
     * Loads the current {@link Measurement} if an {@link MeasurementStatus#OPEN} or {@link MeasurementStatus#PAUSED}
     * {@code Measurement} exists.
     *
     * @return The currently captured {@code Measurement}
     * @throws NoSuchMeasurementException If neither the cache nor the persistence layer have an an
     *             {@link MeasurementStatus#OPEN} or {@link MeasurementStatus#PAUSED} {@code Measurement}
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @NonNull
    Measurement loadCurrentlyCapturedMeasurement() throws NoSuchMeasurementException, CursorIsNullException;
}
