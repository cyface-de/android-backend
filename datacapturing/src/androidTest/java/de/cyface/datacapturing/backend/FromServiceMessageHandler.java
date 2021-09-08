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
package de.cyface.datacapturing.backend;

import static android.content.ContentValues.TAG;

import java.util.ArrayList;
import java.util.List;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import de.cyface.datacapturing.MessageCodes;
import de.cyface.datacapturing.model.CapturedData;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.utils.Validate;

/**
 * A handler for messages received from the capturing service.
 *
 * @author Klemens Muthmann
 * @version 1.0.5
 * @since 2.0.0
 */
class FromServiceMessageHandler extends Handler {
    /**
     * The data previously captured by the service and send to this handler.
     */
    private final List<CapturedData> capturedData = new ArrayList<>();
    /**
     * A flag that is set to <code>true</code> if no permission to access fine location has been granted to the
     * background service.
     */
    private boolean accessWasNotGranted = false;

    @Override
    public void handleMessage(Message msg) {
        // super.handleMessage(msg);
        Bundle dataBundle = msg.getData();
        switch (msg.what) {
            case MessageCodes.DATA_CAPTURED:
                dataBundle.setClassLoader(getClass().getClassLoader());
                CapturedData data = dataBundle.getParcelable("data");

                if (data != null) {
                    capturedData.add(data);
                } else {
                    throw new IllegalStateException("Test received point captured message without associated data!");
                }
                Log.d(TAG, "Test received sensor data.");
                break;
            case MessageCodes.LOCATION_CAPTURED:
                dataBundle.setClassLoader(getClass().getClassLoader());
                GeoLocation location = dataBundle.getParcelable("data");
                Validate.notNull(location);
                Log.d(TAG, String.format("Test received location %f,%f", location.getLat(), location.getLon()));
                break;
            case MessageCodes.GEOLOCATION_FIX:
                Log.d(TAG, "Test received GeoLocation fix.");
                break;
            case MessageCodes.ERROR_PERMISSION:
                Log.d(TAG, "Test was not granted permission for ACCESS_FINE_LOCATION!");
                accessWasNotGranted = true;
            default:
                throw new IllegalStateException(String.format("Test is unable to handle message %s!", msg.what));
        }
    }

    /**
     * @return The data previously captured by the service and send to this handler.
     */
    List<CapturedData> getCapturedData() {
        return capturedData;
    }
}
