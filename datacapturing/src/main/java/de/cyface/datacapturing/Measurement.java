package de.cyface.datacapturing;

import android.support.annotation.Nullable;

/**
 * <p>
 * An object of this class represents a single measurement captured by the {@link DataCapturingService}. This usually
 * happens between complementary calls to {@link DataCapturingService#startCapturing()} and
 * {@link DataCapturingService#stopCapturing()}.
 * </p>
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
public final class Measurement {
    /**
     * <p>
     * The system wide unique identifier of this measurement. Usually this value is generated by a data store (i.e.
     * database).
     * </p>
     */
    private Long id;

    /**
     * <p>
     * Creates a new completely initialized {@link Measurement}.
     * </p>
     * 
     * @param id The system wide unique identifier of this measurement. Usually this value is generated by a data store
     *            (i.e. database).
     */
    public Measurement(@Nullable final long id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        Measurement that = (Measurement)o;

        return id == that.id;
    }

    @Override
    public int hashCode() {
        return (int)(id ^ (id >>> 32));
    }
}
