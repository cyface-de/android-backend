package de.cyface.persistence

/**
 * An `Exception` that is thrown each time setting up the `DataCapturingService` fails.
 *
 * @author Klemens Muthmann
 * @version 1.0.1
 * @since 2.0.0
 */
class SetupException : Exception {
    /**
     * Creates a new completely initialized `SetupException`, wrapping another `Exception` from
     * deeper within the system.
     *
     * @param e The wrapped `Exception`.
     */
    constructor(e: Exception) : super(e)

    /**
     * Creates a new completely initialized `SetupException`, providing a detailed explanation about the
     * error.
     *
     * @param message The message explaining the error condition.
     */
    constructor(message: String) : super(message)
}