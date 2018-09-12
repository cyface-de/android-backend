package de.cyface.datacapturing;

import android.os.Parcel;
import android.support.annotation.NonNull;
import android.util.Log;

/**
 * A default implementation of the {@link EventHandlingStrategy} used if not strategy was provided.
 * This does practically nothing and just allows the strategy to be optional.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 2.5.0
 */
public class IgnoreEverythingStrategy implements EventHandlingStrategy {

    private final static String TAG = "de.cyface.background";

    public IgnoreEverythingStrategy() {
    }

    @Override
    public void handleSpaceWarning(final DataCapturingService dataCapturingService) {
        Log.d(TAG, "No strategy provided for the handleSpaceWarning event. Ignoring.");
    }

    /*
     * MARK: Parcelable Interface
     */

    /**
     * Constructor as required by <code>Parcelable</code> implementation.
     *
     * @param in A <code>Parcel</code> that is a serialized version of a <code>IgnoreEverythingStrategy</code>.
     */
    private IgnoreEverythingStrategy(final @NonNull Parcel in) {
    }

    /**
     * The <code>Parcelable</code> creator as required by the Android Parcelable specification.
     */
    public static final Creator<IgnoreEverythingStrategy> CREATOR = new Creator<IgnoreEverythingStrategy>() {
        @Override
        public IgnoreEverythingStrategy createFromParcel(final Parcel in) {
            return new IgnoreEverythingStrategy(in);
        }

        @Override
        public IgnoreEverythingStrategy[] newArray(final int size) {
            return new IgnoreEverythingStrategy[size];
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
