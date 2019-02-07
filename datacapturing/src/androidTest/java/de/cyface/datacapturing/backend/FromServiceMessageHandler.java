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
 * @version 1.0.4
 * @since 2.0.0
 */
class FromServiceMessageHandler extends Handler {
    /**
     * The data previously captured by the service and send to this handler.
     */
    private List<CapturedData> capturedData = new ArrayList<>();
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
