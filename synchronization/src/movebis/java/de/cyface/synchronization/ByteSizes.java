package de.cyface.synchronization;

/**
 * This class encapsulates some constants required to serialize measurement data into the Cyface binary format.
 * 
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
public final class ByteSizes {
    /**
     * Since our current API Level does not support <code>Long.Bytes</code>.
     */
    public final static int LONG_BYTES = Long.SIZE / 8;
    /**
     * Since our current API Level does not support <code>Integer.Bytes</code>.
     */
    public final static int INT_BYTES = Integer.SIZE / 8;
    /**
     * Since our current API Level does not support <code>Double.Bytes</code>.
     */
    public final static int DOUBLE_BYTES = Double.SIZE / 8;
}
