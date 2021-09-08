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