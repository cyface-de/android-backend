package de.cyface.persistence.serialization;

/**
 * This class encapsulates some constants required to serialize measurement data into the Cyface binary format.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.3.0
 * @since 2.0.0
 */
final class ByteSizes {
    /**
     * Since our current API Level does not support <code>Long.Bytes</code>.
     */
    final static int LONG_BYTES = Long.SIZE / Byte.SIZE;
    /**
     * Since our current API Level does not support <code>Integer.Bytes</code>.
     */
    final static int INT_BYTES = Integer.SIZE / Byte.SIZE;
    /**
     * Since our current API Level does not support <code>Double.Bytes</code>.
     */
    final static int DOUBLE_BYTES = Double.SIZE / Byte.SIZE;
    /**
     * Since our current API Level does not support <code>Short.Bytes</code>.
     */
    final static int SHORT_BYTES = Short.SIZE / Byte.SIZE;
}