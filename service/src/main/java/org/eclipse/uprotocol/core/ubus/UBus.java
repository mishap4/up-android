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
package org.eclipse.uprotocol.core.ubus;

import static org.eclipse.uprotocol.common.util.UStatusUtils.checkNotNull;
import static org.eclipse.uprotocol.common.util.UStatusUtils.isOk;
import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.common.util.log.Formatter.status;
import static org.eclipse.uprotocol.common.util.log.Formatter.stringify;
import static org.eclipse.uprotocol.common.util.log.Formatter.tag;
import static org.eclipse.uprotocol.core.internal.util.UMessageUtils.checkMessageValid;
import static org.eclipse.uprotocol.core.ubus.UBus.Component.DEBUG;
import static org.eclipse.uprotocol.core.ubus.UBus.Component.TAG;
import static org.eclipse.uprotocol.core.ubus.UBus.Component.VERBOSE;
import static org.eclipse.uprotocol.core.ubus.UBus.Component.debugOrError;
import static org.eclipse.uprotocol.core.ubus.UBus.Component.logStatus;
import static org.eclipse.uprotocol.core.ubus.UBus.Component.verboseOrError;

import static java.lang.String.join;
import static java.util.Optional.ofNullable;

import android.content.Context;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.communication.UStatusException;
import org.eclipse.uprotocol.core.UCore;
import org.eclipse.uprotocol.core.ubus.client.Client;
import org.eclipse.uprotocol.core.ubus.client.ClientManager;
import org.eclipse.uprotocol.core.ubus.client.Credentials;
import org.eclipse.uprotocol.transport.UListener;
import org.eclipse.uprotocol.uri.validator.UriFilter;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUID;
import org.eclipse.uprotocol.v1.UUri;

import java.io.PrintWriter;
import java.util.List;

@SuppressWarnings("java:S3008")
public class UBus extends UCore.Component {
    private final Context mContext;
    private final ClientManager mClientManager;
    private final Dispatcher mDispatcher;
    private final ThrottlingMonitor mThrottlingMonitor;
    private final Components mComponents;

    public abstract static class Component {
        protected static final String TAG = tag("core.ubus");
        protected static boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
        protected static boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

        protected void init(@NonNull Components components) {}
        protected void startup() {}
        protected void shutdown() {}
        protected void clearCache() {}

        protected static @NonNull UStatus logStatus(int priority, @NonNull String method, @NonNull UStatus status,
                Object... args) {
            Log.println(priority, TAG, status(method, status, args));
            return status;
        }

        protected static @NonNull UStatus logStatus(int priority, @NonNull String tag, @NonNull String method,
                @NonNull UStatus status, Object... args) {
            Log.println(priority, tag, status(method, status, args));
            return status;
        }

        protected static int debugOrError(@NonNull UStatus status) {
            return isOk(status) ? Log.DEBUG : Log.ERROR;
        }

        protected static int verboseOrError(@NonNull UStatus status) {
            return isOk(status) ? Log.VERBOSE : Log.ERROR;
        }
    }

    public class Components {
        private final List<Component> mDependentComponents;
        private UCore mUCore;

        Components() {
            mDependentComponents = List.of(mClientManager, mDispatcher, mThrottlingMonitor);
        }

        public UCore getUCore() {
            return mUCore;
        }

        public @NonNull ClientManager getClientManager() {
            return mClientManager;
        }

        public @NonNull Dispatcher getDispatcher() {
            return mDispatcher;
        }

        public @NonNull ThrottlingMonitor getThrottlingMonitor() {
            return mThrottlingMonitor;
        }

        void init(@NonNull UCore uCore) {
            mUCore = uCore;
            mDependentComponents.forEach(component -> component.init(this));
        }

        void startup() {
            mDependentComponents.forEach(Component::startup);
        }

        void shutdown() {
            mDependentComponents.forEach(Component::shutdown);
        }

        void clearCache() {
            mDependentComponents.forEach(Component::clearCache);
        }
    }

    public UBus(@NonNull Context context) {
        this(context, null, null, null);
    }

    @VisibleForTesting
    public UBus(@NonNull Context context, ClientManager clientManager, Dispatcher dispatcher,
            ThrottlingMonitor throttlingMonitor) {
        mContext = context;
        mClientManager = ofNullable(clientManager).orElseGet(() -> new ClientManager(context));
        mDispatcher = ofNullable(dispatcher).orElseGet(Dispatcher::new);
        mThrottlingMonitor = ofNullable(throttlingMonitor).orElseGet(ThrottlingMonitor::new);
        mComponents = new Components();
    }

    @Override
    protected void init(@NonNull UCore uCore) {
        Log.i(TAG, join(Key.STATE, "Init"));
        mComponents.init(uCore);
    }

    @Override
    protected void startup() {
        Log.i(TAG, join(Key.STATE, "Startup"));
        mComponents.startup();
    }

    @Override
    protected void shutdown() {
        Log.i(TAG, join(Key.STATE, "Shutdown"));
        mComponents.shutdown();
    }

    @Override
    protected void clearCache() {
        Log.w(TAG, join(Key.EVENT, "Clear cache"));
        mComponents.clearCache();
    }

    @VisibleForTesting
    @NonNull List<Component> getComponents() {
        return mComponents.mDependentComponents;
    }

    public @NonNull UStatus registerClient(@NonNull UUri clientUri, @NonNull IBinder clientToken,
            @NonNull UListener listener) {
        return mClientManager.registerClient(mContext.getPackageName(), clientUri, clientToken, listener);
    }

    public @NonNull <T> UStatus registerClient(@NonNull String packageName, @NonNull UUri clientUri,
            @NonNull IBinder clientToken, @NonNull T listener) {
        return mClientManager.registerClient(packageName, clientUri, clientToken, listener);
    }

    public @NonNull UStatus unregisterClient(@NonNull IBinder clientToken) {
        return mClientManager.unregisterClient(clientToken);
    }

    public @NonNull UStatus send(@NonNull UMessage message, @NonNull IBinder clientToken) {
        Client client = null;
        UStatus status;
        try {
            client = mClientManager.getClientOrThrow(clientToken);
            status = mDispatcher.dispatchFrom(checkMessageValid(message), client);
        } catch (Exception e) {
            status = toStatus(e);
        }
        if (!isOk(status) || VERBOSE) {
            logStatus(verboseOrError(status), "send", status, Key.MESSAGE, stringify(message), Key.CLIENT, client);
        }
        return status;
    }

    public @NonNull UStatus enableDispatching(@NonNull UriFilter filter, @NonNull IBinder clientToken) {
        Client client = null;
        UStatus status;
        try {
            client = mClientManager.getClientOrThrow(clientToken);
            status = mDispatcher.enableDispatching(filter, client);
        } catch (Exception e) {
            status = toStatus(e);
        }
        if (!isOk(status) || DEBUG) {
            logStatus(debugOrError(status), "enableDispatching", status, Key.FILTER, stringify(filter), Key.CLIENT, client);
        }
        return status;
    }

    public @NonNull UStatus disableDispatching(@NonNull UriFilter filter, @NonNull IBinder clientToken) {
        Client client = null;
        UStatus status;
        try {
            client = mClientManager.getClientOrThrow(clientToken);
            status = mDispatcher.disableDispatching(filter, client);
        } catch (Exception e) {
            status = toStatus(e);
        }
        if (!isOk(status) || DEBUG) {
            logStatus(debugOrError(status), "disableDispatching", status, Key.FILTER, stringify(filter), Key.CLIENT, client);
        }
        return status;
    }

    public @NonNull Credentials getCallerCredentials(@NonNull UUID requestId) throws UStatusException {
        final Client client = checkNotNull(mDispatcher.getCaller(requestId), UCode.UNAVAILABLE, "Caller not found");
        return client.getCredentials();
    }

    @Override
    protected void dump(@NonNull PrintWriter writer, String[] args) {
        mDispatcher.dump(writer, args);
    }
}
