package de.cyface.synchronization

/**
 * Exception thrown when no location data is available but needed, e.g. to generate the upload
 * path in `de.cyface.app.digural.upload.WebdavUploader`.
 *
 * @author Armin Schnabel
 */
class NoLocationData(message: String?) : Exception(message)
