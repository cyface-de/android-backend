package de.cyface.synchronization;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ProtocolException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import android.content.Context;
import android.support.annotation.NonNull;

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
     * Socket Factory required to communicate with the Movebis Server using the self signed certificate issued by that
     * server. Further details are available in the
     * <a href="https://developer.android.com/training/articles/security-ssl.html#UnknownCa">Android documentation</a>
     * and for example <a href=
     * "https://stackoverflow.com/questions/24555890/using-a-custom-truststore-in-java-as-well-as-the-default-one">here</a>.
     */
    private final SSLContext sslContext;

    /**
     * 
     */
    SyncPerformer(final Context context) {

        InputStream movebisTrustStoreFile = null;
        try {
            movebisTrustStoreFile = context.getResources().openRawResource(R.raw.truststore_dev);

            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(movebisTrustStoreFile, "secret".toCharArray());
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            // Create an SSLContext that uses our TrustManager
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);

        } catch (IOException | CertificateException | NoSuchAlgorithmException | KeyStoreException
                | KeyManagementException e) {
            throw new IllegalStateException(e);
        } finally {
            if (movebisTrustStoreFile != null) {
                try {
                    movebisTrustStoreFile.close();
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }

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
        HttpsURLConnection.setFollowRedirects(false);
        HttpsURLConnection connection = null;
        String fileName = String.format("%s_%d.cyf", deviceIdentifier, measurementIdentifier);

        try {
            connection = (HttpsURLConnection)new URL(String.format("%s/measurements", dataServerUrl)).openConnection();
            connection.setSSLSocketFactory(sslContext.getSocketFactory());
            try {
                connection.setRequestMethod("POST");
            } catch (ProtocolException e) {
                throw new IllegalStateException(e);
            }
            connection.setRequestProperty("Authorization",
                    "Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJyZWYiOiI1YTgyZDU4ODE1MjAwIiwiZ2VuZGVyIjoibWFsZSIsImFnZSI6NTB9.hr2OFMrzRGEIE2544NzrfifL0n22yE5Xz1jl8EDPh9n6PfTbU_znac6bgbzof3R9TJ9EFp2jyU9fI5rp7rNlFxomu-ORaUSMSktZEHMsC6_h7TVAw0Ygp3jS76-YODlw9VjWmj__5qcqYlQ47ywEYv5uHqXecPt3I2rUtGcLsBm7Gb1eKBwxymi_pEivpo0IIiRIfDv4fYM3IB6cosL7zHFO-nDXRz3IXO3KUwljbieZMng50zCuEiN3DVME-QF1GO3PhO_4M4vTq8_uWx62WxCr2UX28U5DJepJSddDsn5VvzfGAPXB7AF5uh33mtDWkRYnA9KrXSpqeE47TkomntfQg5SV4z0CPbr-d9ThN6cynC83kvh2Up5_DA1nF8kqDibX8hIHmQu5eqG8fgLRjJEHFXLOil-Us9i-oWVhNMv8zYBOCsgJErcAOO9TvbFuoTZt1UYyFLovmOIsBHb54A3xLmlGRQy4ClpaCRdtmopz6Y7VCEN5rjXR57L1tRrPgCrOtzEs05wbnvpYWg_5it4Jx8CyzqMC4t2jHqqfRQNq9enRbp2v9Y1GfCQtFP6XB5hi5grGfh8MfHt0JQ-VQ0NYgNVYp_AKf40wz8ygOvLRDdIqBtMeZGU6ZhOPcy-lzl8SZzXtcwBwPH3POL-4qYGexGHjaM0vhWwym2nIfhY");
            String boundary = "---------------------------boundary";
            String tail = "\r\n--" + boundary + "--\r\n";
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            connection.setDoOutput(true);

            String userIdPart = addPart("deviceId", deviceIdentifier, boundary);
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
            try {
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
            } finally {
                if (out != null) {
                    out.close();
                }
            }

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
