package de.cyface.persistence.serialization

/**
 * An `Exception` which occurs every time someone wants to load a file which does not exist.
 *
 * @author Armin Schnabel
 * @version 1.0.1
 * @since 4.0.0
 * @property message The explanation of why this error occurred.
 */
class NoSuchFileException(message: String) : Exception(message)