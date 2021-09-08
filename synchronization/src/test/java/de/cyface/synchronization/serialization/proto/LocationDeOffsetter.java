/*
 * Copyright 2021 Cyface GmbH
 *
 * This file is part of the Cyface SDK for Android.
 *
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.synchronization.serialization.proto;

import de.cyface.persistence.serialization.proto.Formatter;
import de.cyface.utils.Validate;

/**
 * Calculates the absolute number from the offset/diff format, e.g.: 12345678901234, 12345678902234, 12345678903234 from
 * 12345678901234, 1000, 1000 for timestamps.
 * <p>
 * The first number "seen" is used as offset and as absolute number. Subsequent numbers are seen as in the
 * diff-format, i.e. as the relative difference to the previous number passed.
 * <p>
 * This format is used by the Cyface ProtoBuf Messages: https://github.com/cyface-de/protos
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.0.0
 */
public class LocationDeOffsetter {
    private final DeOffsetter ts;
    private final DeOffsetter lat;
    private final DeOffsetter lon;
    private final DeOffsetter acc;
    private final DeOffsetter spe;

    /**
     * Constructs a fully initialized instance of this class.
     */
    public LocationDeOffsetter() {
        ts = new DeOffsetter();
        lat = new DeOffsetter();
        lon = new DeOffsetter();
        acc = new DeOffsetter();
        spe = new DeOffsetter();
    }

    /**
     * Calculates the absolute number from the offset/diff format, e.g.: 12345678901234, 12345678902234, 12345678903234
     * from 12345678901234, 1000, 1000 for timestamps.
     * <p>
     * The first number "seen" is used as offset and as absolute number. Subsequent numbers are seen as in the
     * diff-format, i.e. as the relative difference to the previous number passed.
     *
     * @param offsets the data in the offset-format. Make subsequent calls in order!
     * @return the data as absolute number.
     */
    public Formatter.Location absolute(Formatter.Location offsets) {
        final long timestamp = ts.absolute(offsets.getTimestamp());
        final long latitude = lat.absolute(offsets.getLatitude());
        final long longitude = lon.absolute(offsets.getLongitude());
        final long accuracy = acc.absolute(offsets.getAccuracy());
        final long speed = spe.absolute(offsets.getSpeed());
        Validate.isTrue(latitude <= Integer.MAX_VALUE);
        Validate.isTrue(longitude <= Integer.MAX_VALUE);
        Validate.isTrue(accuracy <= Integer.MAX_VALUE);
        Validate.isTrue(speed <= Integer.MAX_VALUE);
        return new Formatter.Location(timestamp, (int)latitude, (int)longitude, (int)accuracy, (int)speed);
    }
}