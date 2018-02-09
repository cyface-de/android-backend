package de.cyface.synchronization;

import android.support.annotation.NonNull;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;

/**
 * Performs the actual synchronisation with a provided server, by uploading meta data and a file containing
 * measurements.
 * 
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
class SyncPerformer {

    /**
     * Triggers the data transmission to a Movebis server API. The <code>measurementIdentifier</code> and
     * <code>deviceIdentifier</code> need to be globally unique. If they are not the server will probably reject the
     * request.
     * <p>
     * Since this is a synchronous call it can take from seconds to minutes depending on the size of <code>data</code>.
     * Never call this on the UI thread. Your users are going to hate you.
     *
     * @param dataServerUrl The server URL to send the data to.
     * @param measurementIdentifier The measurement identifier of the transmitted measurement.
     * @param deviceIdentifier The device identifier of the device transmitting the measurement.
     * @param data The data to transmit as <code>InputStream</code>.
     * @param progressListener A listener that is informed about the progress of the upload.
     * @return The <a href="https://de.wikipedia.org/wiki/HTTP-Statuscode">HTTP status</a> code of the response.
     */
    int sendData(final String dataServerUrl, final long measurementIdentifier, final String deviceIdentifier,
            final @NonNull InputStream data, final @NonNull UploadProgressListener progressListener) {
        HttpURLConnection.setFollowRedirects(false);
        HttpURLConnection connection = null;
        String fileName = String.format("%s_%d.cyf", deviceIdentifier, measurementIdentifier);

        try {
            connection = (HttpURLConnection)new URL(String.format("%s/measurements", dataServerUrl)).openConnection();
            try {
                connection.setRequestMethod("POST");
            } catch (ProtocolException e) {
                throw new IllegalStateException(e);
            }
            String boundary = "---------------------------boundary";
            String tail = "\r\n--" + boundary + "--\r\n";
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            connection.setDoOutput(true);

            String userIdPart = addPart("userId", deviceIdentifier, boundary);
            String measurementIdPart = addPart("measurementId", Long.valueOf(measurementIdentifier).toString(),
                    boundary);

            String fileHeader1 = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"" + fileName + "\"\r\n"
                    + "Content-Type: application/octet-stream\r\n" + "Content-Transfer-Encoding: binary\r\n";

            // FIXME This will only work correctly as long as we are using a ByteArrayInputStream. For other streams it
            // returns only the data currently in memory or something similar.
            int dataSize = 0;
            dataSize = data.available() + tail.length();
            String fileHeader2 = "Content-length: " + dataSize + "\r\n";
            String fileHeader = fileHeader1 + fileHeader2 + "\r\n";
            String stringData = userIdPart + measurementIdPart + fileHeader;

            long requestLength = stringData.length() + dataSize;
            connection.setRequestProperty("Content-length", "" + requestLength);
            connection.setFixedLengthStreamingMode((int)requestLength);
            connection.connect();

            DataOutputStream out = null;
            out = new DataOutputStream(connection.getOutputStream());
            out.writeBytes(stringData);
            out.flush();

            int progress = 0;
            int bytesRead = 0;
            byte buf[] = new byte[1024];
            BufferedInputStream bufInput = new BufferedInputStream(data);
            while ((bytesRead = bufInput.read(buf)) != -1) {
                // write output
                out.write(buf, 0, bytesRead);
                out.flush();
                progress += bytesRead; // Here progress is total uploaded bytes
                progressListener.updatedProgress((progress * 100) / dataSize);

            }

            // Write closing boundary and close stream
            out.writeBytes(tail);
            out.flush();
            out.close();

            // Get server response
            // BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            // String line = "";
            // StringBuilder builder = new StringBuilder();
            // while ((line = reader.readLine()) != null) {
            // builder.append(line);
            // }

            return connection.getResponseCode();

        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            if (connection != null)
                connection.disconnect();
        }
    }

    private String addPart(final @NonNull String key, final @NonNull String value, final @NonNull String boundary) {
        return String.format("--%s\r\nContent-Disposition: form-data; name=\"%s\"\r\n\r\n\r\n%s\r\n", boundary, key,
                value);
    }
}
