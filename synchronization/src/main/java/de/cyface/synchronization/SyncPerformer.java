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
 * Created by muthmann on 08.02.18.
 */

public class SyncPerformer {

    private static final String TAG = "de.cyface.sync";

    public void sendData(final String dataServerUrl, final long measurementIdentifier, final String deviceIdentifier, final @NonNull InputStream data,
                                final @NonNull UploadProgressListener progressListener) {
        HttpURLConnection.setFollowRedirects(false);
        HttpURLConnection connection = null;
        String fileName = String.format("%s_%d.cyf",deviceIdentifier,measurementIdentifier);

        try {
            try {
                connection = (HttpURLConnection)new URL(String.format("%s/measurements",dataServerUrl)).openConnection();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            try {
                connection.setRequestMethod("POST");
            } catch (ProtocolException e) {
                throw new IllegalStateException(e);
            }
            String boundary = "---------------------------boundary";
            String tail = "\r\n--" + boundary + "--\r\n";
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            connection.setDoOutput(true);

//            String metadataPart = "--" + boundary + "\r\n" + "Content-Disposition: form-data; name=\"metadata\"\r\n\r\n"
//                    + "" + "\r\n";
            String userIdPart = addPart("userId",deviceIdentifier,boundary);
            String measurementIdPart = addPart("measurementId",Long.valueOf(measurementIdentifier).toString(),boundary);

            String fileHeader1 = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"" + fileName + "\"\r\n"
                    + "Content-Type: application/octet-stream\r\n" + "Content-Transfer-Encoding: binary\r\n";

            // byte[] source = "test".getBytes();
            // long fileLength = source.length + tail.length();
            // FIXME This will only work correctly as long as we are using a ByteArrayInputStream. For other streams it
            // returns only the data currently in memory or something similar.
            int dataSize = 0;
            try {
                dataSize = data.available()+tail.length();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            String fileHeader2 = "Content-length: " + dataSize + "\r\n";
            String fileHeader = fileHeader1 + fileHeader2 + "\r\n";
            String stringData = userIdPart + measurementIdPart + fileHeader;

            long requestLength = stringData.length() + dataSize;
            connection.setRequestProperty("Content-length", "" + requestLength);
            connection.setFixedLengthStreamingMode((int)requestLength);
            try {
                connection.connect();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }

            DataOutputStream out = null;
            try {
                out = new DataOutputStream(connection.getOutputStream());
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            try {
                out.writeBytes(stringData);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            try {
                out.flush();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }

            int progress = 0;
            int bytesRead = 0;
            byte buf[] = new byte[1024];
            BufferedInputStream bufInput = new BufferedInputStream(data);
            try {
                while ((bytesRead = bufInput.read(buf)) != -1) {
                    // write output
                    out.write(buf, 0, bytesRead);
                    out.flush();
                    progress += bytesRead; // Here progress is total uploaded bytes
                    progressListener.updatedProgress((progress * 100) / dataSize);

                    // publishProgress(""+(int)((progress*100)/totalSize)); // sending progress percent to publishProgress
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }

            // Write closing boundary and close stream
            try {
                out.writeBytes(tail);
                out.flush();
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Get server response
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line = "";
                StringBuilder builder = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }

                Log.d(TAG, builder.toString());
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }

        } finally {
            if (connection != null)
                connection.disconnect();
        }
    }

    private String addPart(final @NonNull String key, final @NonNull String value, final @NonNull String boundary) {
        return String.format("--%s\r\nContent-Disposition: form-data; name=\"%s\"\r\n\r\n\r\n%s\r\n",boundary,key,value);
    }
}
