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
package de.cyface.persistence.serialization.proto;

import de.cyface.utils.Validate;

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
public class LocationOffsetter {
    private final Offsetter ts;
    private final Offsetter lat;
    private final Offsetter lon;
    private final Offsetter acc;
    private final Offsetter spe;

    /**
     * Constructs a fully initialized instance of this class.
     */
    public LocationOffsetter() {
        ts = new Offsetter();
        lat = new Offsetter();
        lon = new Offsetter();
        acc = new Offsetter();
        spe = new Offsetter();
    }

    /**
     * Calculates the offset/diff format, e.g.: 12345678901234, 1000, 1000, 1000 for timestamps.
     * <p>
     * The first number "seen" is used as offset and returned as absolute number. Subsequent numbers are returned in the
     * diff-format, i.e. as the relative difference to the previous number passed.
     *
     * @param location the data point to be converted.
     * @return the data point in the offset-format
     */
    public Formatter.Location offset(Formatter.Location location) {
        final long timestamp = ts.offset(location.getTimestamp());
        final long latitude = lat.offset(location.getLatitude());
        final long longitude = lon.offset(location.getLongitude());
        final long accuracy = acc.offset(location.getAccuracy());
        final long speed = spe.offset(location.getSpeed());
        Validate.isTrue(latitude <= Integer.MAX_VALUE);
        Validate.isTrue(longitude <= Integer.MAX_VALUE);
        Validate.isTrue(accuracy <= Integer.MAX_VALUE);
        Validate.isTrue(speed <= Integer.MAX_VALUE);
        return new Formatter.Location(timestamp, (int)latitude, (int)longitude, (int)accuracy, (int)speed);
    }
}