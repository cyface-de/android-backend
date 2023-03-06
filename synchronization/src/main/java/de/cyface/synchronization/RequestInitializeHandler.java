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

import java.io.IOException;

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;

import de.cyface.model.RequestMetaData;

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
    private final RequestMetaData metaData;

    /**
     * Constructs a fully initialized instance of this class.
     *
     * @param metaData the {@code MetaData} used to request the upload permission from the server
     * @param jwtBearer the JWT token to authenticate the upload requests
     */
    public RequestInitializeHandler(RequestMetaData metaData, String jwtBearer) {
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

    private void addMetaData(final RequestMetaData metaData, final HttpHeaders headers) {
        // Location meta data
        if (metaData.getStartLocation() != null) {
            headers.set("startLocLat", String.valueOf(metaData.getStartLocation().getLatitude()));
            headers.set("startLocLon", String.valueOf(metaData.getStartLocation().getLongitude()));
            headers.set("startLocTS", String.valueOf(metaData.getStartLocation().getTimestamp()));
        }
        if (metaData.getEndLocation() != null) {
            headers.set("endLocLat", String.valueOf(metaData.getEndLocation().getLatitude()));
            headers.set("endLocLon", String.valueOf(metaData.getEndLocation().getLongitude()));
            headers.set("endLocTS", String.valueOf(metaData.getEndLocation().getTimestamp()));
        }
        headers.set("locationCount", String.valueOf(metaData.getLocationCount()));

        // Remaining meta data
        headers.set("deviceId", metaData.getDeviceIdentifier());
        headers.set("measurementId", Long.valueOf(metaData.getMeasurementIdentifier()).toString());
        headers.set("deviceType", metaData.getDeviceType());
        headers.set("osVersion", metaData.getOperatingSystemVersion());
        headers.set("appVersion", metaData.getApplicationVersion());
        headers.set("length", String.valueOf(metaData.getLength()));
        // To support the API v2 specification we may not change the "vehicle" key name of the modality
        headers.set("modality", String.valueOf(metaData.getModality()));
        headers.set("formatVersion", String.valueOf(metaData.getFormatVersion()));
    }
}