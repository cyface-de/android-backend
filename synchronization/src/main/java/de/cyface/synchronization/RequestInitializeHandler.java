/*
 * Copyright 2021 Cyface GmbH
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

import static de.cyface.persistence.serialization.MeasurementSerializer.TRANSFER_FILE_FORMAT_VERSION;

import java.io.IOException;

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;

/**
 * Assembles a request as requested to upload data.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.0.0
 */
public class RequestInitializeHandler implements HttpRequestInitializer {
    /**
     * The JWT token to authenticate the upload requests.
     */
    private final String jwtBearer;
    /**
     * The {@code MetaData} used to request the upload permission from the server.
     */
    private final SyncAdapter.MetaData metaData;

    /**
     * Constructs a fully initialized instance of this class.
     *
     * @param metaData the {@code MetaData} used to request the upload permission from the server
     * @param jwtBearer the JWT token to authenticate the upload requests
     */
    public RequestInitializeHandler(SyncAdapter.MetaData metaData, String jwtBearer) {
        this.jwtBearer = jwtBearer;
        this.metaData = metaData;
    }

    @Override
    public void initialize(HttpRequest request) throws IOException {
        final HttpHeaders headers = new HttpHeaders();
        headers.setAuthorization(jwtBearer);
        addMetaData(metaData, headers);
        // sets the metadata in both requests but until we don't use the session-URI
        // feature we can't store the meta data from the pre-request to be used in the upload
        // and the library does not support just to set the upload request header
        request.setHeaders(headers);
    }

    private void addMetaData(SyncAdapter.MetaData metaData, HttpHeaders headers) {
        // Location meta data
        if (metaData.startLocation != null) {
            headers.set("startLocLat", String.valueOf(metaData.startLocation.getLat()));
            headers.set("startLocLon", String.valueOf(metaData.startLocation.getLon()));
            headers.set("startLocTS", String.valueOf(metaData.startLocation.getTimestamp()));
        }
        if (metaData.endLocation != null) {
            headers.set("endLocLat", String.valueOf(metaData.endLocation.getLat()));
            headers.set("endLocLon", String.valueOf(metaData.endLocation.getLon()));
            headers.set("endLocTS", String.valueOf(metaData.endLocation.getTimestamp()));
        }
        headers.set("locationCount", String.valueOf(metaData.locationCount));

        // Remaining meta data
        headers.set("deviceId", metaData.deviceId);
        headers.set("measurementId", Long.valueOf(metaData.measurementId).toString());
        headers.set("deviceType", metaData.deviceType);
        headers.set("osVersion", metaData.osVersion);
        headers.set("appVersion", metaData.appVersion);
        headers.set("length", String.valueOf(metaData.length));
        // To support the API v2 specification we may not change the "vehicle" key name of the modality
        headers.set("vehicle", String.valueOf(metaData.modality.getDatabaseIdentifier()));
        headers.set("formatVersion", String.valueOf(TRANSFER_FILE_FORMAT_VERSION));
    }
}