package de.cyface.persistence.serialization.proto;

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
public class Offsetter {

    /**
     * The previous number which is required to calculate the difference for the subsequent number.
     */
    private Long offset = null;

    /**
     * Calculates the offset/diff format, e.g.: 12345678901234, 1000, 1000, 1000 for timestamps.
     * <p>
     * The first number "seen" is used as offset and returned as absolute number. Subsequent numbers are returned in the
     * diff-format, i.e. as the relative difference to the previous number passed.
     *
     * @param absoluteNumber the number to be returned in the diff-format
     * @return the difference to the previous number or the absolute number for the first number.
     */
    public long offset(final long absoluteNumber) {
        if (offset == null) {
            offset = absoluteNumber;
            return absoluteNumber;
        }
        final long diff = absoluteNumber - offset;
        offset = absoluteNumber;
        return diff;
    }
}
