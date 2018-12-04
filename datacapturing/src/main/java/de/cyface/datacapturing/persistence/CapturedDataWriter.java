package de.cyface.datacapturing.persistence;

import static de.cyface.datacapturing.Constants.BACKGROUND_TAG;

import androidx.annotation.NonNull;
import android.util.Log;

import de.cyface.datacapturing.model.CapturedData;
import de.cyface.persistence.AccelerationsFile;

/**
 * A class responsible for writing captured sensor data to the underlying persistence layer.
 * <p>
 * This is a throw away runnable. You should never run this twice. Doing so will cause duplicates inside the
 * persistence layer. Instead create a new instance per <code>CapturedData</code> to save.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 4.0.0
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
    private final AccelerationsFile accelerationsFile;
    /**
     * Callback which is called after writing data has finished.
     */
    private final WritingDataCompletedCallback callback;

    /**
     * Creates a new completely initialized writer for captured data.
     *
     * @param data The data to write.
     * @param accelerationsFile The file to write the data to.
     * @param callback Callback which is called after writing data has finished.
     */
    CapturedDataWriter(final @NonNull CapturedData data, final AccelerationsFile accelerationsFile,
            final @NonNull WritingDataCompletedCallback callback) {
        this.data = data;
        this.accelerationsFile = accelerationsFile;
        this.callback = callback;
    }

    private void writeCapturedData() {
        Log.d(TAG, "appending " + data.getAccelerations().size() + "/" + data.getRotations().size() + "/"
                + data.getDirections().size() + " A/R/MPs on: " + Thread.currentThread().getName());

        accelerationsFile.append(data.getAccelerations());
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
