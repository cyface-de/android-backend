/*
 * Copyright 2025 Cyface GmbH
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
package de.cyface.persistence.model

import org.junit.Test
import kotlin.io.path.Path

/**
 * Tests the inner workings of the [Attachment].
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.13.0
 */
class AttachmentTest {
    /**
     * Ensures instantiation works.
     *
     * This was a reproducing test for a bug where `fileFormatVersion` was `0` in the
     * `ParcelableAttachment` `init`-block as the overwritten attributes where not yet initialized.
     * We solved this by decoupling `ParcelableAttachment` from `Attachment`. [STAD-561]
     */
    @Test
    fun test_happyPath() {
        // Arrange

        // Act
        Attachment(
            1000L,
            AttachmentStatus.SAVED,
            de.cyface.protos.model.File.FileType.CSV,
            1,
            1234L,
            Path("./some/test/file.ext"),
            null,
            null,
            999L,
            1L,
        )

        // Assert
    }
}
