package de.cyface.datacapturing;

import java.util.Locale;

/**
 * <p>
 * Objects of this class represent the current disk (or rather SD card) space used and available. This space is mostly
 * filled with unsynchronized {@link Measurement}s. To avoid filling up the users SD card it is advisable to delete
 * {@link Measurement}s as soon as they use up too much space.
 * </p>
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
public final class DiskConsumption {
    /**
     * <p>
     * The count of bytes currently used by the {@link DataCapturingService}.
     * </p>
     */
    private final int consumedBytes;
    /**
     * <p>
     * The count of bytes still available for the {@link DataCapturingService}.
     * </p>
     */
    private final int availableBytes;

    /**
     * <p>
     * Creates a new completely initialized {@code DiskConsumption} object.
     * </p>
     * 
     * @param consumedBytes The count of bytes currently used by the {@link DataCapturingService}.
     * @param availableBytes The count of bytes still available for the {@link DataCapturingService}.
     */
    public DiskConsumption(final int consumedBytes, final int availableBytes) {
        if (consumedBytes < 0) {
            throw new IllegalArgumentException(String
                    .format(Locale.US,"Illegal value for consumed bytes. May not be smaller then 0 but was %d", consumedBytes));
        }
        if (availableBytes < 0) {
            throw new IllegalArgumentException(String
                    .format(Locale.US,"Illegal value for available bytes. May not be smaller then 0 but was %d", availableBytes));
        }

        this.consumedBytes = consumedBytes;
        this.availableBytes = availableBytes;
    }

    /**
     * @return The count of bytes currently used by the {@link DataCapturingService}.
     */
    private int getConsumedBytes() {
        return consumedBytes;
    }

    /**
     * @return The count of bytes still available for the {@link DataCapturingService}.
     */
    private int getAvailableBytes() {
        return availableBytes;
    }
}
