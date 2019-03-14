package de.cyface.synchronization;

/**
 * An {@code Exception} thrown on errors while submitting data to the Cyface server. The {@code Exception} contains
 * all the details available and relevant to diagnose the error case.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 1.0.0
 */
public final class DataTransmissionException extends Exception {
    /**
     * The HTTP status error code causing this <code>Exception</code>.
     */
    private final int httpStatusCode;
    /**
     * The error name as a short form for the detailed error message.
     */
    private final String errorName;

    /**
     * @param httpStatusCode The HTTP status error code causing this <code>Exception</code>.
     * @param errorName The error name as a short form for the detailed error message.
     * @param detailedMessage A more detailed message explaining the context for this <code>Exception</code>.
     */
    DataTransmissionException(final int httpStatusCode, final String errorName, final String detailedMessage) {
        this(httpStatusCode, errorName, detailedMessage, null);

    }

    /**
     * @param httpStatusCode The HTTP status error code causing this <code>Exception</code>.
     * @param errorName The error name as a short form for the detailed error message.
     * @param detailedMessage A more detailed message explaining the context for this <code>Exception</code>.
     * @param cause The <code>Exception</code> that caused this one.
     */
    private DataTransmissionException(final int httpStatusCode, final String errorName, final String detailedMessage,
            final Exception cause) {
        super(detailedMessage, cause);
        this.httpStatusCode = httpStatusCode;
        this.errorName = errorName;
    }

    /**
     * @return The HTTP status error code causing this <code>Exception</code>.
     */
    @SuppressWarnings({"WeakerAccess", "unused"}) // Makes sense to offer those getters
    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    /**
     * @return The error name as a short form for the detailed error message.
     */
    @SuppressWarnings({"WeakerAccess", "unused"}) // Makes sense to offer those getters
    public String getErrorName() {
        return errorName;
    }
}
