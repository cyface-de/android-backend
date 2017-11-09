package de.cyface.datacapturing;

/**
 * <p>
 * Provides a reason why the {@link DataCapturingService} asks for a certain system permission.
 * </p>
 *
 * @author Klemens Muthmann
 * @since 1.0.0
 * @version 1.0.0
 * @see DataCapturingListener#onRequirePermission(String, Reason)
 */
public final class Reason {
    /**
     * <p>
     * Explains the reason in human understandable text.
     * </p>
     */
    private final String explanation;

    /**
     * <p>
     * Creates a new completely initialized object of this class.
     * </p>
     *
     * @param explanation The reason in human understandable text.
     */
    public Reason(final String explanation) {
        this.explanation = explanation;
    }

    /**
     * @return The reason in human understandable text.
     */
    public String getExplanation() {
        return explanation;
    }
}
