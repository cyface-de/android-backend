package de.cyface.datacapturing;

import static de.cyface.datacapturing.Constants.BACKGROUND_TAG;
import static de.cyface.datacapturing.Constants.MINIMUM_MEGABYTES_REQUIRED;

import java.util.Locale;

import android.os.Environment;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.StatFs;
import android.util.Log;
import androidx.annotation.NonNull;
import de.cyface.persistence.model.Measurement;

/**
 * Objects of this class represent the current disk (or rather SD card) space used and available. This space is mostly
 * filled with unsynchronized {@link Measurement}s. To avoid filling up the users SD card it is advisable to delete
 * {@link Measurement}s as soon as they use up too much space.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.0.6
 * @since 1.0.0
 */
public final class DiskConsumption implements Parcelable {

    /**
     * The tag used to identify log messages send to logcat.
     */
    private final static String TAG = BACKGROUND_TAG;
    /**
     * The count of bytes currently used by the {@link DataCapturingService}.
     */
    private final long consumedBytes;
    /**
     * The count of bytes still available for the {@link DataCapturingService}.
     */
    private final long availableBytes;

    /**
     * Creates a new completely initialized {@code DiskConsumption} object.
     * 
     * @param consumedBytes The count of bytes currently used by the {@link DataCapturingService}.
     * @param availableBytes The count of bytes still available for the {@link DataCapturingService}.
     */
    public DiskConsumption(final long consumedBytes, final long availableBytes) {
        if (consumedBytes < 0) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Illegal value for consumed bytes. May not be smaller then 0 but was %d", consumedBytes));
        }
        if (availableBytes < 0) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Illegal value for available bytes. May not be smaller then 0 but was %d", availableBytes));
        }

        this.consumedBytes = consumedBytes;
        this.availableBytes = availableBytes;
    }

    /**
     * @return The count of bytes currently used by the {@link DataCapturingService}.
     */
    private long getConsumedBytes() {
        return consumedBytes;
    }

    /**
     * @return The count of bytes still available for the {@link DataCapturingService}.
     */
    private long getAvailableBytes() {
        return availableBytes;
    }

    /*
     * MARK: Parcelable Interface
     */

    /**
     * Constructor as required by <code>Parcelable</code> implementation.
     *
     * @param in A <code>Parcel</code> that is a serialized version of a <code>DiskConsumption</code>.
     */
    private DiskConsumption(final @NonNull Parcel in) {
        consumedBytes = in.readLong();
        availableBytes = in.readLong();
    }

    /**
     * The <code>Parcelable</code> creator as required by the Android Parcelable specification.
     */
    public static final Creator<DiskConsumption> CREATOR = new Creator<DiskConsumption>() {
        @Override
        public DiskConsumption createFromParcel(final Parcel in) {
            return new DiskConsumption(in);
        }

        @Override
        public DiskConsumption[] newArray(final int size) {
            return new DiskConsumption[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        dest.writeLong(consumedBytes);
        dest.writeLong(availableBytes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        DiskConsumption that = (DiskConsumption)o;
        return consumedBytes == that.consumedBytes && availableBytes == that.availableBytes;
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + (int)(consumedBytes ^ (consumedBytes >>> 32));
        result = 31 * result + (int)(availableBytes ^ (availableBytes >>> 32));
        return result;
    }

    /**
     * Checks how much storage is left.
     *
     * @return The number of bytes of space available.
     */
    public static long bytesAvailable() {
        final StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
        final long bytesAvailable;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
            bytesAvailable = stat.getBlockSizeLong() * stat.getAvailableBlocksLong();
        } else {
            bytesAvailable = (long)stat.getBlockSize() * (long)stat.getAvailableBlocks();
        }
        Log.v(TAG, "Space available: " + (bytesAvailable / (1024 * 1024)) + " MB");
        return bytesAvailable;
    }

    /**
     * Checks the size of the storage.
     *
     * @return The number of bytes of storage.
     */
    public static long storageSize() {
        final StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
        final long bytesStorage;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
            bytesStorage = stat.getBlockSizeLong() * stat.getBlockCountLong();
        } else {
            bytesStorage = (long)stat.getBlockSize() * (long)stat.getBlockCount();
        }
        Log.d(TAG, "Total disk space: " + (bytesStorage / (1024 * 1024)) + " MB");
        return bytesStorage;
    }

    /**
     * Checks if at last {@code MINIMUM_MEGABYTES_REQUIRED} of space is available.
     *
     * @return True if enough space is available.
     */
    public static boolean spaceAvailable() {
        return (bytesAvailable() / (1024 * 1024)) > MINIMUM_MEGABYTES_REQUIRED;
    }
}
