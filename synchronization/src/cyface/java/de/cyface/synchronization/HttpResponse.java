package de.cyface.synchronization;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Internal value object class for the attributes of an HTTP response. It wrappers the HTTP
 * status code as well as a JSON body object.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 1.0.3
 * @since 1.0.0
 */
class HttpResponse {
    private int responseCode;
    private JSONObject body;

    /**
     * Checks the responseCode and responseBody before constructing the {@link HttpResponse}.
     *
     * @param responseCode the HTTP status code returned by the server
     * @param responseBody the HTTP response body returned by the server. Can be null when the login
     *            was successful and there was nothing to return (defined by the Spring API).
     * @throws ResponseParsingException when the server returned something not parsable.
     */
    HttpResponse(final int responseCode, final String responseBody) throws ResponseParsingException {
        this.responseCode = responseCode;
        try {
            this.body = new JSONObject(responseBody);
        } catch (final JSONException e) {
            if (is2xxSuccessful()) {
                this.body = null; // Nothing to complain, the login was successful
                return;
            }
            throw new ResponseParsingException(
                    String.format("Error: '%s'. Unable to read the http response.", e.getMessage()), e);
        }
    }

    JSONObject getBody() {
        return body;
    }

    int getResponseCode() {
        return responseCode;
    }

    /**
     * Checks if the HTTP response code says "successful".
     *
     * @return true if the code is a 200er code
     */
    boolean is2xxSuccessful() {
        return (responseCode >= 200 && responseCode < 300);
    }
}
