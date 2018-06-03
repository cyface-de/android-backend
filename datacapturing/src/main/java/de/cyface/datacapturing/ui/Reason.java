package de.cyface.datacapturing.ui;

import de.cyface.datacapturing.DataCapturingService;

/**
 * Provides a reason why the {@link DataCapturingService} asks for a certain system permission.
 *
 * @author Klemens Muthmann
 * @since 1.0.0
 * @version 1.0.0
 * @see UIListener#onRequirePermission(String, Reason)
 */
public final class Reason {
    /**
     * Explains the reason in human understandable text.
     */
    private final String explanation;

    /**
     * Creates a new completely initialized object of this class.
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
