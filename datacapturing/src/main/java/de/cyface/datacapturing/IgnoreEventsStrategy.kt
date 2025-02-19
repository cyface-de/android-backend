/*
 * Copyright 2017-2021 Cyface GmbH
 *
 * This file is part of the Cyface SDK for Android.
 *
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.datacapturing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Parcel
import android.os.Parcelable.Creator
import android.util.Log
import androidx.core.app.NotificationCompat
import de.cyface.datacapturing.backend.DataCapturingBackgroundService
import de.cyface.utils.R
import de.cyface.utils.Validate.notNull

/**
 * A default implementation of the [EventHandlingStrategy] used if not strategy was provided.
 * For most events it does practically nothing and just allows the strategy to be optional.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 2.0.4
 * @since 2.5.0
 */
class IgnoreEventsStrategy : EventHandlingStrategy {
    /**
     * No arguments constructor is declared here, since it is overwritten by the constructor required by
     * `Parcelable`.
     */
    constructor()

    /**
     * Constructor as required by `Parcelable` implementation.
     *
     * @param in A `Parcel` that is a serialized version of a `IgnoreEventsStrategy`.
     */
    @Suppress("UNUSED_PARAMETER") // Required by Parcelable
    private constructor(@Suppress("unused") `in`: Parcel)

    override fun handleSpaceWarning(dataCapturingBackgroundService: DataCapturingBackgroundService) {
        Log.d(
            Constants.BACKGROUND_TAG,
            "No strategy provided for the handleSpaceWarning event. Ignoring."
        )
    }

    override fun buildCapturingNotification(context: DataCapturingBackgroundService): Notification {
        notNull(context, "No context provided!")

        // The NotificationChannel settings are cached so you need to temporarily change the channel id for testing
        val channelId = context.getString(R.string.cyface_notification_channel_id)
        val notificationManager = context
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notNull(notificationManager)
        if (notificationManager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(
                channelId, "Cyface Data Capturing",
                NotificationManager.IMPORTANCE_LOW
            ) // to disable vibration
            notificationManager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(context, channelId)
            .setContentTitle(context.getString(R.string.notification_title))
            .setSmallIcon(R.drawable.ic_hourglass_empty_black_24dp)
            .setContentText(context.getString(R.string.notification_text)).setOngoing(true)
            .setAutoCancel(false)
            .build()
    }

    override fun describeContents(): Int {
        // Nothing to do
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        // Nothing to do
    }

    companion object {
        /**
         * The `Parcelable` creator as required by the Android Parcelable specification.
         *
         * `cannot use '<>' with anonymous inner classes`
         */
        @Suppress("unused") // Required by Parcelable
        @JvmField
        val CREATOR: Creator<IgnoreEventsStrategy> = object : Creator<IgnoreEventsStrategy> {
            override fun createFromParcel(`in`: Parcel): IgnoreEventsStrategy {
                return IgnoreEventsStrategy(`in`)
            }

            override fun newArray(size: Int): Array<IgnoreEventsStrategy?> {
                return arrayOfNulls(size)
            }
        }
    }
}
