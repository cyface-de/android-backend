/*
 * Copyright 2018-2023 Cyface GmbH
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
package de.cyface.datacapturing.backend;

import android.location.LocationManager;

import androidx.annotation.NonNull;
import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.model.CapturedData;
import de.cyface.persistence.model.ParcelableGeoLocation;

/**
 * Interface for all classes that need to listen to events sent by a <code>CapturingProcess</code>.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 7.0.0
 * @since 1.0.0
 */
public interface CapturingProcessListener {
    /**
     * Called every time a new data point with a valid geo location coordinate has been captured.
     *
     * @param location Captured data wrapper object.
     */
    void onLocationCaptured(@NonNull ParcelableGeoLocation location);

    /**
     * Transmits the accelerations, rotations and directions captured in intervals of approximately one geo
     * location fix or one second if no fix occurs..
     *
     * @param data The data captured covering a list of accelerations, rotations and directions.
     */
    void onDataCaptured(@NonNull CapturedData data) throws DataCapturingException;

    /**
     * Called when the {@link LocationManager} this object is registered with thinks there was a successful
     * geo location fix.
     */
    void onLocationFix();

    /**
     * Called when the {@link LocationManager} this object is registered with thinks geo location fix was lost.
     */
    void onLocationFixLost();
}
