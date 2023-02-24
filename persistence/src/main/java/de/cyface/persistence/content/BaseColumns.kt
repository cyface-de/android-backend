package de.cyface.persistence.content

interface BaseColumns {
    companion object {
        /**
         * The unique identifier of a row in a database table.
         *
         * SQLite Type: INTEGER (long)
         */
        const val ID = "id"

        /**
         * Column name for the Unix timestamp in milliseconds of the data represented by the table row.
         */
        const val TIMESTAMP = "timestamp"

        /**
         * Column name for the column storing the foreign key referencing the [de.cyface.persistence.model.Measurement]
         * for the data represented by the table row.
         */
        const val MEASUREMENT_ID = "measurementId"
    }
}