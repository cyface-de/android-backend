/*
 * Copyright 2017-2023 Cyface GmbH
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
package de.cyface.datacapturing.persistence;

import static de.cyface.datacapturing.Constants.BACKGROUND_TAG;

import android.util.Log;

import androidx.annotation.NonNull;

import de.cyface.datacapturing.model.CapturedData;
import de.cyface.persistence.serialization.Point3DFile;

/**
 * A class responsible for writing captured sensor data to the underlying persistence layer.
 * <p>
 * This is a throw away runnable. You should never run this twice. Doing so will cause duplicates inside the
 * persistence layer. Instead create a new instance per <code>CapturedData</code> to save.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 4.0.2
 * @since 1.0.0
 */
public class CapturedDataWriter implements Runnable {

    /**
     * The tag used to identify Logcat messages from this class.
     */
    private static final String TAG = BACKGROUND_TAG;
    /**
     * The data to write.
     */
    private final CapturedData data;
    /**
     * The {@link Point3DFile} to write acceleration points to.
     */
    private final Point3DFile accelerationsFile;
    /**
     * The {@link Point3DFile} to write rotation points to.
     */
    private final Point3DFile rotationsFile;
    /**
     * The {@link Point3DFile} to write direction points to.
     */
    private final Point3DFile directionsFile;
    /**
     * Callback which is called after writing data has finished.
     */
    private final WritingDataCompletedCallback callback;

    /**
     * Creates a new completely initialized writer for captured data.
     *
     * @param data The data to write.
     * @param accelerationsFile The file to write the data to.
     * @param rotationsFile The file to write the data to.
     * @param directionsFile The file to write the data to.
     * @param callback Callback which is called after writing data has finished.
     */
    CapturedDataWriter(final @NonNull CapturedData data, @NonNull final Point3DFile accelerationsFile,
            @NonNull final Point3DFile rotationsFile, @NonNull final Point3DFile directionsFile,
            final @NonNull WritingDataCompletedCallback callback) {
        this.data = data;
        this.accelerationsFile = accelerationsFile;
        this.rotationsFile = rotationsFile;
        this.directionsFile = directionsFile;
        this.callback = callback;
    }

    private void writeCapturedData() {

        Log.d(TAG, "appending " + data.getAccelerations().size() + "/" + data.getRotations().size() + "/"
                + data.getDirections().size() + " A/R/MPs on: " + Thread.currentThread().getName());
        accelerationsFile.append(data.getAccelerations());
        rotationsFile.append(data.getRotations());
        directionsFile.append(data.getDirections());
    }

    @Override
    public void run() {
        try {
            writeCapturedData();
        } finally {
            callback.writingDataCompleted();
        }
    }
}
