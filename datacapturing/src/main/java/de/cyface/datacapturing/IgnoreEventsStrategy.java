package de.cyface.datacapturing;

import static de.cyface.datacapturing.Constants.BACKGROUND_TAG;

import android.os.Parcel;
import android.support.annotation.NonNull;
import android.util.Log;

import de.cyface.datacapturing.backend.DataCapturingBackgroundService;

/**
 * A default implementation of the {@link EventHandlingStrategy} used if not strategy was provided.
 * This does practically nothing and just allows the strategy to be optional.
 *
 * @author Armin Schnabel
 * @version 1.0.1
 * @since 2.5.0
 */
public class IgnoreEventsStrategy implements EventHandlingStrategy {

    /**
     * The tag used to identify log messages send to logcat.
     */
    private final static String TAG = BACKGROUND_TAG;

    public IgnoreEventsStrategy() {
    }

    @Override
    public void handleSpaceWarning(final DataCapturingBackgroundService dataCapturingBackgroundService) {
        Log.d(TAG, "No strategy provided for the handleSpaceWarning event. Ignoring.");
    }

    /*
     * MARK: Parcelable Interface
     */

    /**
     * Constructor as required by <code>Parcelable</code> implementation.
     *
     * @param in A <code>Parcel</code> that is a serialized version of a <code>IgnoreEventsStrategy</code>.
     */
    private IgnoreEventsStrategy(final @NonNull Parcel in) {
    }

    /**
     * The <code>Parcelable</code> creator as required by the Android Parcelable specification.
     */
    public static final Creator<IgnoreEventsStrategy> CREATOR = new Creator<IgnoreEventsStrategy>() {
        @Override
        public IgnoreEventsStrategy createFromParcel(final Parcel in) {
            return new IgnoreEventsStrategy(in);
        }

        @Override
        public IgnoreEventsStrategy[] newArray(final int size) {
            return new IgnoreEventsStrategy[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
    }
}
