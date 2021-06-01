package de.cyface.synchronization.serialization.proto;

/**
 * Calculates the offset/diff format, e.g.: 12345678901234, 1000, 1000, 1000 for timestamps.
 * <p>
 * The first number "seen" is used as offset and returned as absolute number. Subsequent numbers are returned in the
 * diff-format, i.e. as the relative difference to the previous number passed.
 * <p>
 * This format is used by the Cyface ProtoBuf Messages: https://github.com/cyface-de/protos
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.0.0
 */
public class DeOffsetter {

    /**
     * The previous number which is required to calculate the difference for the subsequent number.
     */
    private Long previous = null;

    /**
     * Calculates the absolute number from a offset/diff format (e.g. 12345678901234, 1000, 1000).
     * <p>
     * The first number "seen" is expected to be an absolute number. Subsequent numbers expected to be in the
     * diff-format, i.e. as the relative difference to the previous number passed.
     *
     * @param number the diff-format number to be returned as absolute number
     * @return the absolute number
     */
    public long absolute(final long number) {
        if (previous == null) {
            previous = number;
            return number;
        }
        final long absolute = previous + number;
        previous = absolute;
        return absolute;
    }
}
