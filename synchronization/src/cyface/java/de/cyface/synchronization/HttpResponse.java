package de.cyface.synchronization;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Internal value object class for the attributes of an HTTP response. It wrappers the HTTP
 * status code as well as a JSON body object.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 1.0.2
 * @since 1.0.0
 */
class HttpResponse {
    private int responseCode;
    private JSONObject body;

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
                    "Http request returned http code " + responseCode + " and has a non-JSON body: " + responseBody, e);
        }
    }

    JSONObject getBody() {
        return body;
    }

    int getResponseCode() {
        return responseCode;
    }

    boolean is2xxSuccessful() {
        return (responseCode >= 200 && responseCode <= 300);
    }
}
