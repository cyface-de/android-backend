package de.cyface.synchronization;

/**
 * Listens for progress during a data upload and reports the progress in percent.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 * @see SyncPerformer
 */
interface UploadProgressListener {
    /**
     * Reports the progress of the current data upload.
     *
     * @param percent The data upload progress in percent (between 0.0 and 100.0).
     */
    void updatedProgress(float percent);
}
