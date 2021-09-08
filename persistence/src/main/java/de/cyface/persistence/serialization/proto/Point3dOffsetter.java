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
public class Point3dOffsetter {
    private final Offsetter ts;
    private final Offsetter x;
    private final Offsetter y;
    private final Offsetter z;

    /**
     * Constructs a fully initialized instance of this class.
     */
    public Point3dOffsetter() {
        ts = new Offsetter();
        x = new Offsetter();
        y = new Offsetter();
        z = new Offsetter();
    }

    /**
     * Calculates the offset/diff format, e.g.: 12345678901234, 1000, 1000, 1000 for timestamps.
     * <p>
     * The first number "seen" is used as offset and returned as absolute number. Subsequent numbers are returned in the
     * diff-format, i.e. as the relative difference to the previous number passed.
     *
     * @param point the data point to be converted.
     * @return the data point in the offset-format
     */
    public Formatter.Point3d offset(Formatter.Point3d point) {
        final long timestamp = ts.offset(point.getTimestamp());
        final long xValue = x.offset(point.getX());
        final long yValue = y.offset(point.getY());
        final long zValue = z.offset(point.getZ());
        Validate.isTrue(xValue <= Integer.MAX_VALUE);
        Validate.isTrue(yValue <= Integer.MAX_VALUE);
        Validate.isTrue(zValue <= Integer.MAX_VALUE);
        return new Formatter.Point3d(timestamp, (int)xValue, (int)yValue, (int)zValue);
    }
}