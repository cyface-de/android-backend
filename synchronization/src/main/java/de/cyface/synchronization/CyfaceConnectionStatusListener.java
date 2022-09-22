/*
 * Copyright 2019-2022 Cyface GmbH
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
package de.cyface.synchronization;

import static de.cyface.synchronization.BundlesExtrasCodes.SYNC_PERCENTAGE_ID;
import static de.cyface.synchronization.Constants.TAG;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

/**
 * Listener for interested parties to subscribe to synchronization status updates.
 * Synchronization errors are broadcasted via the {@link ErrorHandler}.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 3.0.2
 * @since 1.0.0
 */
public final class CyfaceConnectionStatusListener implements ConnectionStatusListener {

    final static String SYNC_STARTED = TAG + ".started";
    final static String SYNC_FINISHED = TAG + ".finished";
    final static String SYNC_PROGRESS = TAG + ".progress";
    final static String SYNC_MEASUREMENT_ID = TAG + ".measurement_id";
    private final Context context;

    CyfaceConnectionStatusListener(final @NonNull Context context) {
        this.context = context;
    }

    @Override
    public void onSyncStarted() {
        final Intent intent = new Intent(SYNC_STARTED);
        // Binding the intent to the package of the app which runs this SDK [DAT-1509].
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    @Override
    public void onProgress(final float percent, final long measurementId) {
        final Intent intent = new Intent(SYNC_PROGRESS);
        // Binding the intent to the package of the app which runs this SDK [DAT-1509].
        intent.setPackage(context.getPackageName());
        intent.putExtra(SYNC_PERCENTAGE_ID, percent);
        intent.putExtra(SYNC_MEASUREMENT_ID, measurementId);
        context.sendBroadcast(intent);
    }

    @Override
    public void onSyncFinished() {
        final Intent intent = new Intent(SYNC_FINISHED);
        // Binding the intent to the package of the app which runs this SDK [DAT-1509].
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }
}
