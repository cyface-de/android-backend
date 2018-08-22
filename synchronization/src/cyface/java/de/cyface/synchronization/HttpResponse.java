package de.cyface.synchronization;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Internal value object class for the attributes of an HTTP response. It wrappers the HTTP
 * status code as well as a JSON body object.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 1.0.1
 * @since 1.0.0
 */
class HttpResponse {
    private int responseCode;
    private JSONObject body;

    HttpResponse(int responseCode, String responseBodyAsString) throws ResponseParsingException {
        this.responseCode = responseCode;
        try {
            this.body = new JSONObject(responseBodyAsString);
        } catch (final JSONException e) {
            if (is2xxSuccessful()) {
                this.body = null; // this is expected, continue.
            } else {
                throw new ResponseParsingException(
                        "Empty response body for unsuccessful response (code " + responseCode + "): " + e.getMessage());
            }
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
