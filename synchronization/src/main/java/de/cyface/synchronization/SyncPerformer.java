package de.cyface.synchronization;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ProtocolException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Locale;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

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
    private SSLContext sslContext;

    /**
     * This is an array with only one <code>TrustManager</code> trusting all certificates. TODO This is evil and needs
     * to be fixed for the final release.
     */
    private TrustManager[] trustAllCerts = new TrustManager[] {new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {

        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {

        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }};

    /**
     * Creates a new completely initialized <code>SyncPerformer</code> for a given Android <code>Context</code>.
     *
     * @param context The Android <code>Context</code> to use for setting the correct server certification information.
     */
    SyncPerformer(final @NonNull Context context) {

        // InputStream movebisTrustStoreFile = null;
        // InputStream movebisCertificateChain = null;
        try {
            // movebisTrustStoreFile = context.getResources().openRawResource(R.raw.truststore_dev);
            // movebisCertificateChain = context.getResources().openRawResource(R.raw.certificate_chain);
            // byte[] der = loadPemCertificate(movebisCertificateChain);
            // ByteArrayInputStream derInputStream = new ByteArrayInputStream(der);
            // CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            // X509Certificate cert = (X509Certificate)certificateFactory.generateCertificate(derInputStream);
            // String alias = cert.getSubjectX500Principal().getName();
            //
            // KeyStore trustStore = KeyStore.getInstance("PKCS12");
            // trustStore.load(movebisTrustStoreFile, "secret".toCharArray());
            // trustStore.setCertificateEntry(alias, cert);
            // TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
            // tmf.init(trustStore);

            // Create an SSLContext that uses our TrustManager
            sslContext = SSLContext.getInstance("TLSv1");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            // sslContext.init(null, tmf.getTrustManagers(), null);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        } catch (KeyManagementException e) {
            throw new IllegalStateException(e);
        }
        // } finally {
        // try {
        // if (movebisTrustStoreFile != null) {
        // movebisTrustStoreFile.close();
        // }
        // if (movebisCertificateChain != null) {
        // movebisCertificateChain.close();
        // }
        // } catch (IOException e) {
        // throw new IllegalStateException(e);
        // }
        // }
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
        String fileName = String.format(Locale.US, "%s_%d.cyf", deviceIdentifier, measurementIdentifier);

        try {
            connection = (HttpsURLConnection)new URL(String.format("%s/measurements", dataServerUrl)).openConnection();
            connection.setSSLSocketFactory(sslContext.getSocketFactory());
            connection.setHostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });
            try {
                connection.setRequestMethod("POST");
            } catch (ProtocolException e) {
                throw new IllegalStateException(e);
            }
            connection.setRequestProperty("Authorization", "Bearer test");
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
