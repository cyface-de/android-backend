package de.cyface.datacapturing;

import androidx.annotation.Nullable;

import de.cyface.persistence.model.Vehicle;

/**
 * An object of this class represents a single measurement captured by the {@link DataCapturingService}. This usually
 * happens between complementary calls to
 * {@link DataCapturingService#startAsync(DataCapturingListener, Vehicle, StartUpFinishedHandler)} and
 * {@link DataCapturingService#stopAsync(ShutDownFinishedHandler)}.
 *
 * @author Klemens Muthmann
 * @version 1.1.3
 * @since 1.0.0
 */
public final class Measurement {
    /**
     * The system wide unique identifier of this measurement. Usually this value is generated by a data store (i.e.
     * database).
     */
    private Long id;

    /**
     * Creates a new completely initialized {@link Measurement}.
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

        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return (int)(id ^ (id >>> 32));
    }

    public long getIdentifier() {
        return id;
    }

    @Override
    public String toString() {
        return "Measurement{" + "id=" + id + '}';
    }
}
