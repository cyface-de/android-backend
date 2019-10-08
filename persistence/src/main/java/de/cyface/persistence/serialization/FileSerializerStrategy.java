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
package de.cyface.persistence.serialization;

import java.io.BufferedOutputStream;
import java.io.OutputStream;

import android.content.ContentProvider;

import androidx.annotation.NonNull;

import de.cyface.persistence.MeasurementContentProviderClient;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.utils.CursorIsNullException;

/**
 * Interface for strategies to serialize Measurement data.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 5.0.0-beta1
 */
public interface FileSerializerStrategy {

    /**
     * Implements the core algorithm of loading data of a {@link Measurement} from the {@link PersistenceLayer}
     * and serializing it into an array of bytes, ready to be compressed.
     * <p>
     * We use the {@param loader} to access the measurement data.
     * <p>
     * We assemble the data using a buffer to avoid OOM exceptions.
     * <p>
     * <b>ATTENTION:</b> The caller must make sure the {@param bufferedOutputStream} is closed when no longer needed
     * or the app crashes.
     *
     * @param bufferedOutputStream The {@link OutputStream} to which the serialized data should be written. Injecting
     *            this allows us to compress the serialized data without the need to write it into a temporary file.
     *            We require a {@link BufferedOutputStream} for performance reasons.
     * @param loader The loader providing access to the {@link ContentProvider} storing all the {@link GeoLocation}s.
     * @param measurementIdentifier The id of the {@code Measurement} to load
     * @param persistence The {@code PersistenceLayer} to access file based data
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    void loadSerialized(@NonNull final BufferedOutputStream bufferedOutputStream,
            @NonNull final MeasurementContentProviderClient loader, final long measurementIdentifier,
            @NonNull final PersistenceLayer persistence)
            throws CursorIsNullException;
}
