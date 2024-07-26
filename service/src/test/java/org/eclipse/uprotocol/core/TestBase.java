/*
 * Copyright (c) 2024 General Motors GTO LLC
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * SPDX-FileType: SOURCE
 * SPDX-FileCopyrightText: 2023 General Motors GTO LLC
 * SPDX-License-Identifier: Apache-2.0
 */
package org.eclipse.uprotocol.core;

import static org.eclipse.uprotocol.common.util.UStatusUtils.buildStatus;
import static org.eclipse.uprotocol.communication.UPayload.packToAny;
import static org.eclipse.uprotocol.transport.UTransportAndroid.META_DATA_ENTITY_ID;
import static org.eclipse.uprotocol.transport.UTransportAndroid.META_DATA_ENTITY_VERSION;
import static org.eclipse.uprotocol.transport.UTransportAndroid.PERMISSION_ACCESS_UBUS;
import static org.eclipse.uprotocol.uri.factory.UriFactory.ANY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Int32Value;

import org.eclipse.uprotocol.communication.CallOptions;
import org.eclipse.uprotocol.communication.UPayload;
import org.eclipse.uprotocol.communication.UStatusException;
import org.eclipse.uprotocol.core.ubus.IUListener;
import org.eclipse.uprotocol.core.ubus.UBus;
import org.eclipse.uprotocol.core.ubus.client.Client;
import org.eclipse.uprotocol.core.usubscription.USubscription;
import org.eclipse.uprotocol.core.utwin.UTwin;
import org.eclipse.uprotocol.uri.validator.UriFilter;
import org.eclipse.uprotocol.uuid.factory.UuidFactory;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UPriority;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUID;
import org.eclipse.uprotocol.v1.UUri;
import org.eclipse.uprotocol.v1.internal.ParcelableUMessage;
import org.junit.function.ThrowingRunnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({"SameParameterValue", "java:S1186"})
public class TestBase {
    protected static final UStatus STATUS_UNKNOWN = buildStatus(UCode.UNKNOWN, "unknown");
    protected static final String PACKAGE_NAME = "org.eclipse.uprotocol.core.test";
    protected static final String PACKAGE2_NAME = "org.eclipse.uprotocol.core.test2";
    protected static final String AUTHORITY_REMOTE = "cloud";
    protected static final int VERSION = 1;
    protected static final int SERVICE_ID = 0x50;
    protected static final int SERVICE2_ID = 0x51;
    protected static final int CLIENT_ID = 0x52;
    protected static final int CLIENT2_ID = 0x53;
    protected static final int RESOURCE_ID = 0x8000;
    protected static final int RESOURCE2_ID = 0x8001;
    protected static final int METHOD_ID = 0x1;
    protected static final int METHOD2_ID = 0x2;
    protected static final UUri SERVICE_URI = UUri.newBuilder()
            .setUeId(SERVICE_ID)
            .setUeVersionMajor(VERSION)
            .build();
    protected static final UUri SERVICE2_URI = UUri.newBuilder()
            .setUeId(SERVICE2_ID)
            .setUeVersionMajor(VERSION)
            .build();
    protected static final UUri CLIENT_URI = UUri.newBuilder()
            .setUeId(CLIENT_ID)
            .setUeVersionMajor(VERSION)
            .build();
    protected static final UUri CLIENT2_URI = UUri.newBuilder()
            .setUeId(CLIENT2_ID)
            .setUeVersionMajor(VERSION)
            .build();
    protected static final UUri RESOURCE_URI = UUri.newBuilder(SERVICE_URI)
            .setResourceId(RESOURCE_ID)
            .build();
    protected static final UUri RESOURCE2_URI = UUri.newBuilder(SERVICE_URI)
            .setResourceId(RESOURCE2_ID)
            .build();
    protected static final UUri METHOD_URI = UUri.newBuilder(SERVICE_URI)
            .setResourceId(METHOD_ID)
            .build();
    protected static final UUri METHOD2_URI = UUri.newBuilder(SERVICE_URI)
            .setResourceId(METHOD2_ID)
            .build();
    protected static final UUri SERVICE_URI_REMOTE = UUri.newBuilder(SERVICE_URI)
            .setAuthorityName(AUTHORITY_REMOTE)
            .build();
    protected static final UUri CLIENT_URI_REMOTE = UUri.newBuilder(CLIENT_URI)
            .setAuthorityName(AUTHORITY_REMOTE)
            .build();
    protected static final UUri RESOURCE_URI_REMOTE = UUri.newBuilder(RESOURCE_URI)
            .setAuthorityName(AUTHORITY_REMOTE)
            .build();
    protected static final UUri RESOURCE2_URI_REMOTE = UUri.newBuilder(RESOURCE2_URI)
            .setAuthorityName(AUTHORITY_REMOTE)
            .build();
    protected static final UUri METHOD_URI_REMOTE = UUri.newBuilder(METHOD_URI)
            .setAuthorityName(AUTHORITY_REMOTE)
            .build();
    protected static final UUri REMOTE_CLIENT_URI = UUri.newBuilder() // uStreamer
            .setUeId(Client.REMOTE_ID)
            .setUeVersionMajor(VERSION)
            .build();
    protected static final UUri EMPTY_URI = UUri.getDefaultInstance();
    protected static final UriFilter RESOURCE_FILTER = new UriFilter(RESOURCE_URI, ANY);
    protected static final UriFilter METHOD_FILTER = new UriFilter(ANY, METHOD_URI);
    protected static final UUID ID = createId();
    protected static final String TOKEN =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG" +
            "4gU21pdGgiLCJpYXQiOjE1MTYyMzkwMjJ9.Q_w2AVguPRU2KskCXwR7ZHl09TQXEntfEA8Jj2_Jyew";
    protected static final int TTL = 1000;
    protected static final int EXPIRY_TIME = 5000;
    protected static final int PERIOD = 100;
    protected static final int DELAY_MS = 100;
    protected static final int DELAY_LONG_MS = 500;
    protected static final Int32Value DATA = Int32Value.newBuilder().setValue(101).build();
    protected static final UPayload PAYLOAD = packToAny(DATA);
    protected static final UMessage EMPTY_MESSAGE = UMessage.getDefaultInstance();
    protected static final CallOptions OPTIONS = new CallOptions(TTL, UPriority.UPRIORITY_CS4, TOKEN);
    protected static final String MESSAGE = "message";

    protected static void assertStatus(@NonNull UCode code, @NonNull UStatus status) {
        assertEquals(code, status.getCode());
    }

    @CanIgnoreReturnValue
    protected static UStatusException assertThrowsStatusException(UCode code, ThrowingRunnable runnable) {
        final UStatusException exception = assertThrows(UStatusException.class, runnable);
        assertEquals(code, exception.getCode());
        return exception;
    }

    protected static @NonNull UUID createId() {
        return UuidFactory.Factories.UPROTOCOL.factory().create();
    }

    protected @NonNull UCore.Builder newMockUCoreBuilder() {
        return newMockUCoreBuilder(mock(Context.class));
    }

    protected @NonNull UCore.Builder newMockUCoreBuilder(@NonNull Context context) {
        return new UCore.Builder(context)
                .setUBus(mock(UBus.class))
                .setUSubscription(mock(USubscription.class))
                .setUTwin(mock(UTwin.class));
    }

    public static class MetaDataBuilder {
        private Integer mEntityId;
        private Integer mEntityVersion;

        public @NonNull MetaDataBuilder setEntityId(int id) {
            mEntityId = id;
            return this;
        }

        public @NonNull MetaDataBuilder setEntityVersion(int version) {
            mEntityVersion = version;
            return this;
        }

        public Bundle build() {
            final Bundle bundle = new Bundle();
            if (mEntityId != null) {
                bundle.putInt(META_DATA_ENTITY_ID, mEntityId);
            }
            if (mEntityVersion != null) {
                bundle.putInt(META_DATA_ENTITY_VERSION, mEntityVersion);
            }
            return bundle;
        }
    }

    protected static @NonNull PackageInfo buildPackageInfo(@NonNull String packageName, Bundle metaData) {
        final ApplicationInfo appInfo = buildApplicationInfo(packageName, metaData);
        final PackageInfo packageInfo = new PackageInfo();
        packageInfo.applicationInfo = appInfo;
        packageInfo.packageName = packageName;
        packageInfo.requestedPermissions = new String[] { PERMISSION_ACCESS_UBUS };
        return packageInfo;
    }

    protected static @NonNull PackageInfo buildPackageInfo(@NonNull String packageName, ServiceInfo... services) {
        final ApplicationInfo appInfo = buildApplicationInfo(packageName, null);
        final PackageInfo packageInfo = new PackageInfo();
        packageInfo.applicationInfo = appInfo;
        packageInfo.packageName = packageName;
        packageInfo.services = services;
        for (ServiceInfo service : services) {
            service.packageName = packageName;
            service.applicationInfo = appInfo;
        }
        packageInfo.requestedPermissions = new String[] { PERMISSION_ACCESS_UBUS };
        return packageInfo;
    }

    protected static @NonNull ApplicationInfo buildApplicationInfo(@NonNull String packageName, Bundle metaData) {
        final ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.flags = ApplicationInfo.FLAG_INSTALLED;
        appInfo.packageName = packageName;
        if (metaData != null && !metaData.isEmpty()) {
            appInfo.metaData = metaData;
        }
        return appInfo;
    }

    protected static @NonNull ServiceInfo buildServiceInfo(ComponentName component, Bundle metaData) {
        final ServiceInfo serviceInfo = new ServiceInfo();
        if (component != null) {
            serviceInfo.packageName = component.getPackageName();
            serviceInfo.name = component.getClassName();
        }
        if (metaData != null && !metaData.isEmpty()) {
            serviceInfo.metaData = metaData;
        }
        return serviceInfo;
    }

    protected void sleep(long timeout) {
        try {
            new CompletableFuture<>().get(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
            // Nothing to do
        }
    }

    protected static class MockListener extends IUListener.Stub {
        public MockListener() {}

        @Override
        public void onReceive(ParcelableUMessage parcelableUMessage) {}
    }
}
