/*
 * Copyright 2023 Cyface GmbH
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
package de.cyface.persistence.dao

import de.cyface.persistence.Database
import de.cyface.persistence.model.Identifier
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Tests the CRUD operations of the [IdentifierDao].
 *
 * @author Armin Schnabel
 * @version 1.0.1
 * @since 7.5.0
 */
class IdentifierDaoTest {
    private lateinit var database: Database
    private lateinit var identifierDao: IdentifierDao

    @Before
    fun setupDatabase() {
        database = TestUtils.createDatabase()
        identifierDao = database.identifierDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun testInsert() = runBlocking{
        // Arrange
        // Act
        createIdentifier()

        // Assert
        assertThat(identifierDao.getAll().size, equalTo(1))
    }

    @Test
    fun testGetAll() = runBlocking {
        // Arrange
        val identifier1 = createIdentifier()
        val identifier2 = createIdentifier()

        // Act
        val identifiers = identifierDao.getAll()

        // Assert
        assertThat(identifiers.size, equalTo(2))
        assertThat(identifiers, equalTo(listOf(identifier1, identifier2)))
    }

    /**
     * Creates an [Identifier] in the test database.
     *
     * @return The created object.
     */
    private fun createIdentifier(): Identifier = runBlocking {
        val identifier = fixture()
        val id = identifierDao.insert(identifier)
        identifier.id = id
        return@runBlocking identifier
    }

    private fun fixture(): Identifier {
        return Identifier(UUID.randomUUID().toString())
    }
}