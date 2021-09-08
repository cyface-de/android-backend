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