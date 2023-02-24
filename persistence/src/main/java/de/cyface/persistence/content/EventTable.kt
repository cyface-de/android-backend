package de.cyface.persistence.content

import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import android.util.Log
import de.cyface.persistence.Constants

/**
 * Table for storing [Event]s.
 *
 * @author Armin Schnabel
 * @version 1.1.0
 * @since 4.0.0
 */
class EventTable
/**
 * Provides a completely initialized object as a representation of a table containing [Event]s in the
 * database.
 */
internal constructor(
    /**
     * An array containing all the column names used by this table.
     */
    override val databaseTableColumns: Array<String> = arrayOf(
        BaseColumns._ID,
        COLUMN_TIMESTAMP,
        COLUMN_TYPE,
        COLUMN_MEASUREMENT_FK,
        COLUMN_VALUE
    )
) : AbstractCyfaceTable(URI_PATH) {
    /**
     * Don't forget to update the [MeasurementProviderHelper]'s `DATABASE_VERSION` if you upgrade this table.
     *
     *
     * The Upgrade is automatically executed in a transaction, do not wrap the code in another transaction!
     *
     *
     * This upgrades are called incrementally by [MeasurementProviderHelper.onUpgrade].
     *
     *
     * @see CyfaceTable.onUpgrade
     */
    fun onUpgrade(database: SQLiteDatabase, fromVersion: Int, toVersion: Int) {
        when (fromVersion) {
            11 ->                 // This table was added in version 12
                onCreate(database)
            14 -> {
                // This column was added in version 15
                Log.d(Constants.TAG, "Upgrading event table from V14")
                database.execSQL("ALTER TABLE events ADD COLUMN value TEXT")
            }
        }
    }

    companion object {
        /**
         * The path segment in the table URI identifying the [EventTable].
         */
        const val URI_PATH = "events"

        /**
         * Column name for the column storing the [Event] timestamp.
         */
        const val COLUMN_TIMESTAMP = "timestamp"

        /**
         * Column name for the column storing the [Event.EventType.getDatabaseIdentifier].
         */
        const val COLUMN_TYPE = "type"

        /**
         * Column name for the column storing the foreign key referencing the [Measurement] for this
         * [Event] if the `Event` is linked to a `Measurement`.
         */
        const val COLUMN_MEASUREMENT_FK = "measurement_fk"

        /**
         * Column name for the column storing the [Event.EventType.getValue].
         */
        const val COLUMN_VALUE = "value"
    }
}