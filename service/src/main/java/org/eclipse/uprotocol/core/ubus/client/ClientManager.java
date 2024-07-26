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
package org.eclipse.uprotocol.core.ubus.client;

import static android.os.Binder.getCallingPid;
import static android.os.Binder.getCallingUid;
import static android.os.Process.myPid;
import static android.os.Process.myUid;

import static org.eclipse.uprotocol.common.util.UStatusUtils.STATUS_OK;
import static org.eclipse.uprotocol.common.util.UStatusUtils.buildStatus;
import static org.eclipse.uprotocol.common.util.UStatusUtils.checkArgument;
import static org.eclipse.uprotocol.common.util.UStatusUtils.checkNotNull;
import static org.eclipse.uprotocol.common.util.UStatusUtils.checkStringNotEmpty;
import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.common.util.log.Formatter.quote;
import static org.eclipse.uprotocol.common.util.log.Formatter.stringify;
import static org.eclipse.uprotocol.common.util.log.Formatter.tag;
import static org.eclipse.uprotocol.core.internal.util.CommonUtils.emptyIfNull;
import static org.eclipse.uprotocol.core.internal.util.UUriUtils.isLocalUri;
import static org.eclipse.uprotocol.core.internal.util.log.FormatterExt.stringify;
import static org.eclipse.uprotocol.transport.UTransportAndroid.META_DATA_ENTITY_ID;
import static org.eclipse.uprotocol.transport.UTransportAndroid.META_DATA_ENTITY_VERSION;
import static org.eclipse.uprotocol.uri.validator.UriValidator.isEmpty;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.communication.UStatusException;
import org.eclipse.uprotocol.core.ubus.IUListener;
import org.eclipse.uprotocol.core.ubus.UBus;
import org.eclipse.uprotocol.transport.UListener;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class ClientManager extends UBus.Component {
    private static final String TAG = tag(UBus.Component.TAG, "ClientManager");

    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private final Map<IBinder, Client> mClients = new HashMap<>();
    @GuardedBy("mLock")
    private Client mRemoteClient;
    private final Set<RegistrationListener> mRegistrationListeners = ConcurrentHashMap.newKeySet();
    private final PackageManager mPackageManager;

    public ClientManager(@NonNull Context context) {
        mPackageManager = context.getPackageManager();
    }

    @Override
    public void shutdown() {
        synchronized (mLock) {
            mClients.values().forEach(Client::release);
            mClients.clear();
        }
        mRegistrationListeners.clear();
    }

    public interface RegistrationListener {
        default void onClientRegistered(@NonNull Client client) {}
        default void onClientUnregistered(@NonNull Client client) {}
    }

    public void registerListener(@NonNull RegistrationListener listener) {
        mRegistrationListeners.add(listener);
    }

    public void unregisterListener(@NonNull RegistrationListener listener) {
        mRegistrationListeners.remove(listener);
    }

    @VisibleForTesting
    boolean isRegistered(@NonNull RegistrationListener listener) {
        return mRegistrationListeners.contains(listener);
    }

    private void notifyRegistered(@NonNull Client client) {
        mRegistrationListeners.forEach((listener -> listener.onClientRegistered(client)));
    }

    private void notifyUnregistered(@NonNull Client client) {
        mRegistrationListeners.forEach((listener -> listener.onClientUnregistered(client)));
    }

    @SuppressWarnings("java:S2201")
    private void checkCallerCredentials(@NonNull Credentials credentials) {
        if (myPid() == credentials.pid() && myUid() == credentials.uid()) {
            return;
        }
        checkArgument(isLocalUri(credentials.uri()), UCode.UNAUTHENTICATED, "Client URI authority is not local");
        final UUri clientUri = credentials.uri();
        Arrays.stream(emptyIfNull(mPackageManager.getPackagesForUid(credentials.uid())))
                .filter(packageName -> credentials.packageName().equals(packageName))
                .filter(packageName -> {
                    try {
                        return contains(mPackageManager.getPackageInfo(packageName,
                                PackageManager.GET_SERVICES | PackageManager.GET_META_DATA), clientUri);
                    } catch (PackageManager.NameNotFoundException e) {
                        logStatus(Log.WARN, TAG, "getPackageInfo", buildStatus(UCode.UNAVAILABLE, e.getMessage()));
                        return false;
                    }
                })
                .findFirst()
                .orElseThrow(() -> new UStatusException(UCode.UNAUTHENTICATED, "Missing or not matching '" +
                        META_DATA_ENTITY_ID + "' and '" + META_DATA_ENTITY_VERSION + "' meta-data in manifest"));
    }

    private static boolean contains(@NonNull PackageInfo packageInfo, @NonNull UUri clientUri) {
        return Stream.concat(Stream.of(packageInfo.applicationInfo),
                        (packageInfo.services != null) ? Stream.of(packageInfo.services) : Stream.empty())
                .filter(Objects::nonNull)
                .map(info -> clientUri.equals(getClientUri(info)) ? clientUri : null)
                .anyMatch(Objects::nonNull);
    }

    private static UUri getClientUri(@NonNull PackageItemInfo info) {
        if (info.metaData != null) {
            final int id = info.metaData.getInt(META_DATA_ENTITY_ID, -1);
            final int version = info.metaData.getInt(META_DATA_ENTITY_VERSION, -1);
            if (id >= 0 && version > 0) {
                return UUri.newBuilder()
                        .setUeId(id)
                        .setUeVersionMajor(version)
                        .build();
            }
        }
        return null;
    }

    private static void checkCallerCredentials(int pid, int uid, @NonNull Client client) {
        if (pid == myPid() && uid == myUid()) {
            return;
        }
        final Credentials credentials = client.getCredentials();
        if (pid == credentials.pid() && uid == credentials.uid()) {
            return;
        }
        throw new UStatusException(UCode.UNAUTHENTICATED,
                "Client '" + stringify(client.getUri()) + "' is not registered by the caller");
    }

    private @NonNull DeathRecipient newDeathRecipient(@NonNull IBinder clientToken) {
        return () -> {
            Log.w(TAG, join(Key.EVENT, "Client died", Key.TOKEN, stringify(clientToken)));
            unregisterClient(clientToken);
        };
    }

    private @NonNull <T> Client newClient(@NonNull Credentials credentials, @NonNull IBinder clientToken,
            @NonNull T listener) {
        if (listener instanceof UListener internalListener) {
            return new InternalClient(credentials, clientToken, internalListener);
        } else if (listener instanceof IUListener binderListener) {
            return BindingClient.newClient(credentials, clientToken, newDeathRecipient(clientToken), binderListener);
        } else {
            throw new UnsupportedOperationException("Listener type is not supported");
        }
    }

    public @NonNull <T> UStatus registerClient(@NonNull String packageName, @NonNull UUri clientUri,
            @NonNull IBinder clientToken, @NonNull T listener) {
        try {
            checkStringNotEmpty(packageName, "Package name is empty");
            checkArgument(!isEmpty(clientUri), "Client URI is empty");
            checkNotNull(clientToken, "Client token is null");
            checkNotNull(listener, "Listener is null");
            Client client;
            synchronized (mLock) {
                client = getClient(clientToken);
                if (client != null) {
                    checkArgument(client.getListener() == listener,
                            "Client is already registered with a different listener");
                    return STATUS_OK;
                }
                final Credentials credentials = new Credentials(packageName, getCallingPid(), getCallingUid(), clientUri);
                checkCallerCredentials(credentials);
                client = newClient(credentials, clientToken, listener);
                mClients.put(clientToken, client);
                if (client.isRemote()) {
                    mRemoteClient = client;
                }
                logStatus(Log.INFO, TAG, "registerClient", STATUS_OK, Key.CLIENT, client);
            }
            notifyRegistered(client);
            return STATUS_OK;
        } catch (Exception e) {
            return logStatus(Log.ERROR, TAG, "registerClient", toStatus(e),
                    Key.PACKAGE, quote(packageName), Key.URI, stringify(clientUri));
        }
    }

    public UStatus unregisterClient(@NonNull IBinder clientToken) {
        try {
            Client client;
            synchronized (mLock) {
                client = getClient(clientToken);
                if (client == null) {
                    return STATUS_OK;
                }

                checkCallerCredentials(getCallingPid(), getCallingUid(), client);
                mClients.remove(clientToken);
                if (client.isRemote()) {
                    mRemoteClient = null;
                }
                client.release();
                logStatus(Log.INFO, TAG, "unregisterClient", STATUS_OK, Key.CLIENT, client);
            }
            notifyUnregistered(client);
            return STATUS_OK;
        } catch (Exception e) {
            return logStatus(Log.ERROR, TAG,"unregisterClient", toStatus(e), Key.TOKEN, stringify(clientToken));
        }
    }

    public Set<Client> getClients() {
        synchronized (mLock) {
            return new ArraySet<>(mClients.values());
        }
    }

    public Client getClient(@NonNull IBinder clientToken) {
        synchronized (mLock) {
            return mClients.get(checkNotNull(clientToken, UCode.UNAUTHENTICATED, "Token is null"));
        }
    }

    public @NonNull Client getClientOrThrow(@NonNull IBinder clientToken) {
        synchronized (mLock) {
            return checkNotNull(getClient(clientToken), UCode.UNAUTHENTICATED, "Client is not registered");
        }
    }

    public Client getRemoteClient() {
        synchronized (mLock) {
            return mRemoteClient;
        }
    }
}
