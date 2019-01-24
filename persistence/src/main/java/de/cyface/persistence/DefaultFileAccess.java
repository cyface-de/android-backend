package de.cyface.persistence;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import de.cyface.utils.Validate;

/**
 * Implementation of the {@link FileAccessLayer} which accesses the real file system.
 *
 * @author Armin Schnabel
 * @version 3.0.0
 * @since 3.0.0
 */
public final class DefaultFileAccess implements FileAccessLayer {

    @Override
    @NonNull
    public FileFilter directoryFilter() {
        return new FileFilter() {
            public boolean accept(File pathname) {
                return pathname.isDirectory();
            }
        };
    }

    @Override
    @NonNull
    public byte[] loadBytes(File file) {
        Validate.isTrue(file.exists());
        final byte[] bytes = new byte[(int)file.length()];

        try {
            BufferedInputStream bufferedInputStream = null;
            try {
                bufferedInputStream = new BufferedInputStream(new FileInputStream(file));
                DataInputStream inputStream = null;
                try {
                    inputStream = new DataInputStream(bufferedInputStream);
                    try {
                        inputStream.readFully(bytes);
                        Log.d(Constants.TAG, "Read " + bytes.length + " bytes (from " + file.getPath() + ")");
                    } finally {
                        inputStream.close();
                        bufferedInputStream.close();
                    }
                } finally {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                }
                return bytes;
            } finally {
                if (bufferedInputStream != null) {
                    bufferedInputStream.close();
                }
            }
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to read file.");
        }
    }

    @Override
    @NonNull
    public File getFolderPath(@NonNull Context context, @NonNull String folderName) {
        return new File(context.getFilesDir() + File.separator + folderName);
    }

    @Override
    @NonNull
    public File getFilePath(@NonNull Context context, long measurementId, String folderName, String fileExtension) {
        final File folder = getFolderPath(context, folderName);
        return new File(folder.getPath() + File.separator + measurementId + "." + fileExtension);
    }

    @Override
    @NonNull
    public File createFile(@NonNull Context context, long measurementId, String folderName, String fileExtension) {
        final File file = getFilePath(context, measurementId, folderName, fileExtension);
        Validate.isTrue(!file.exists(), "Failed to createFile as it already exists: " + file.getPath());
        try {
            if (!file.createNewFile()) {
                throw new IOException("Failed to createFile: " + file.getPath());
            }
        } catch (final IOException e) {
            throw new IllegalStateException("Failed createFile: " + file.getPath());
        }
        return file;
    }

    @Override
    public void write(File file, byte[] data, boolean append) {
        Validate.isTrue(file.exists(), "Failed to write to file as it does not exist: " + file.getPath());
        try {
            BufferedOutputStream outputStream = null;
            try {
                outputStream = new BufferedOutputStream(new FileOutputStream(file, append));
                try {
                    outputStream.write(data);
                } finally {
                    outputStream.close();
                }
            } finally {
                if (outputStream != null) {
                    outputStream.close();
                }
            }
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to append data to file.");
        }
    }
}