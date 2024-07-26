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
package org.eclipse.uprotocol.core.usubscription;

import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.common.util.log.Formatter.error;
import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.common.util.log.Formatter.status;
import static org.eclipse.uprotocol.common.util.log.Formatter.tag;
import static org.eclipse.uprotocol.communication.UPayload.packToAny;
import static org.eclipse.uprotocol.transport.UTransportAndroid.PERMISSION_ACCESS_UBUS;
import static org.eclipse.uprotocol.uri.validator.UriValidator.DEFAULT_RESOURCE_ID;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.google.protobuf.Descriptors.ServiceDescriptor;

import org.eclipse.uprotocol.Uoptions;
import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.communication.UPayload;
import org.eclipse.uprotocol.communication.UStatusException;
import org.eclipse.uprotocol.core.UCore;
import org.eclipse.uprotocol.core.internal.handler.MessageHandler;
import org.eclipse.uprotocol.core.ubus.UBus;
import org.eclipse.uprotocol.core.ubus.client.Credentials;
import org.eclipse.uprotocol.core.usubscription.v3.USubscriptionProto;
import org.eclipse.uprotocol.core.usubscription.v3.Update;
import org.eclipse.uprotocol.transport.builder.UMessageBuilder;
import org.eclipse.uprotocol.uri.factory.UriFactory;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUID;
import org.eclipse.uprotocol.v1.UUri;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SuppressWarnings({"java:S1200", "java:S3008"})
public class USubscription extends UCore.Component {
    public static final ServiceDescriptor DESCRIPTOR = USubscriptionProto.getDescriptor().getServices().get(0);
    public static final String NAME = DESCRIPTOR.getOptions().getExtension(Uoptions.serviceName);
    public static final UUri SERVICE = UriFactory.fromProto(DESCRIPTOR, DEFAULT_RESOURCE_ID);
    public static final UUri METHOD_SUBSCRIBE = UriFactory.fromProto(DESCRIPTOR, 1);
    public static final UUri METHOD_UNSUBSCRIBE = UriFactory.fromProto(DESCRIPTOR, 2);
    public static final UUri METHOD_FETCH_SUBSCRIPTIONS = UriFactory.fromProto(DESCRIPTOR, 3);
    public static final UUri METHOD_REGISTER_FOR_NOTIFICATIONS = UriFactory.fromProto(DESCRIPTOR, 6);
    public static final UUri METHOD_UNREGISTER_FOR_NOTIFICATIONS = UriFactory.fromProto(DESCRIPTOR, 7);
    public static final UUri METHOD_FETCH_SUBSCRIBERS = UriFactory.fromProto(DESCRIPTOR, 8);
    public static final UUri TOPIC_SUBSCRIPTION_UPDATE = UriFactory.fromProto(DESCRIPTOR, 0x8000);

    protected static final String TAG = tag(NAME);
    protected static boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    protected static boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    private final IBinder mClientToken = new Binder();
    private final Context mContext;
    private final SubscriptionHandler mSubscriptionHandler;
    private final ExpiryMonitor mExpiryMonitor;
    private final Set<SubscriptionListener> mSubscriptionListeners = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService mExecutor = Executors.newSingleThreadScheduledExecutor();
    private final BroadcastReceiver mPackageChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(intent.getAction())) {
                final Uri packageData = intent.getData();
                final String packageName = (packageData != null) ? packageData.getSchemeSpecificPart() : "";
                mExecutor.execute(() -> handlePackageRemoved(packageName));
            }
        }
    };
    private UBus mUBus;
    private MessageHandler mMessageHandler;

    public USubscription(@NonNull Context context) {
        mContext = context;
        mSubscriptionHandler = new SubscriptionHandler(context);
        mExpiryMonitor = new ExpiryMonitor(context, mExecutor);
    }

    @VisibleForTesting
    public USubscription(@NonNull Context context, @NonNull SubscriptionHandler subscriptionHandler,
            @NonNull ExpiryMonitor expiryMonitor) {
        mContext = context;
        mSubscriptionHandler = subscriptionHandler;
        mExpiryMonitor = expiryMonitor;
    }

    @Override
    protected void init(@NonNull UCore uCore) {
        Log.i(TAG, join(Key.STATE, "Init"));
        mUBus = uCore.getUBus();
        mMessageHandler = new MessageHandler(mUBus, SERVICE, mClientToken, mExecutor);
        mSubscriptionHandler.init(this);
        mExpiryMonitor.init(this);

        mUBus.registerClient(SERVICE, mClientToken, mMessageHandler);
        mMessageHandler.registerListener(METHOD_SUBSCRIBE, this::subscribe);
        mMessageHandler.registerListener(METHOD_UNSUBSCRIBE, this::unsubscribe);
        mMessageHandler.registerListener(METHOD_FETCH_SUBSCRIPTIONS, this::fetchSubscriptions);
        mMessageHandler.registerListener(METHOD_REGISTER_FOR_NOTIFICATIONS, this::registerForNotifications);
        mMessageHandler.registerListener(METHOD_UNREGISTER_FOR_NOTIFICATIONS, this::unregisterForNotifications);
        mMessageHandler.registerListener(METHOD_FETCH_SUBSCRIBERS, this::fetchSubscribers);

        final IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        filter.addDataScheme("package");
        try {
            mContext.registerReceiver(mPackageChangeReceiver, filter, Context.RECEIVER_EXPORTED);
        } catch (Exception e) {
            Log.e(TAG, error("Failed to register receiver", e));
        }
    }

    @Override
    protected void startup() {
        Log.i(TAG, join(Key.STATE, "Startup"));
        mSubscriptionHandler.startup();
        mExpiryMonitor.startup();
        mExecutor.execute(this::cleanup);
    }

    @Override
    protected void shutdown() {
        Log.i(TAG, join(Key.STATE, "Shutdown"));
        mExecutor.shutdown();
        try {
            if (!mExecutor.awaitTermination(100, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, join(Key.EVENT, "Timeout while waiting for executor termination"));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        mMessageHandler.unregisterAllListeners();
        mUBus.unregisterClient(mClientToken);
        mSubscriptionListeners.clear();
        mSubscriptionHandler.shutdown();
        mExpiryMonitor.shutdown();

        try {
            mContext.unregisterReceiver(mPackageChangeReceiver);
        } catch (Exception e) {
            Log.e(TAG, error("Failed to unregister receiver", e));
        }
    }

    private void cleanup() {
        Log.i(TAG, join(Key.EVENT, "Cleanup"));
        final Set<String> installedPackages = getInstalledPackages();
        mSubscriptionHandler.getSubscribedPackages().stream()
                .filter(it -> !installedPackages.contains(it))
                .forEach(this::handlePackageRemoved);
    }

    private @NonNull Set<String> getInstalledPackages() {
        final PackageManager manager = mContext.getPackageManager();
        final String[] permissions = new String[] { PERMISSION_ACCESS_UBUS };
        return manager.getPackagesHoldingPermissions(permissions, 0x00400000 /* MATCH_ANY_USER */).stream()
                .filter(Objects::nonNull)
                .map(it -> it.packageName)
                .collect(Collectors.toSet());
    }

    private void handlePackageRemoved(@NonNull String packageName) {
        Log.i(TAG, join(Key.EVENT, "Package removed", Key.PACKAGE, packageName));
        mSubscriptionHandler.unsubscribeAllFromPackage(packageName);
    }

    private void subscribe(@NonNull UMessage requestMessage) {
        try {
            sendResponse(requestMessage, packToAny(mSubscriptionHandler.subscribe(requestMessage)));
        } catch (Exception e) {
            final UStatus status = logStatus(Log.ERROR, "subscribe", toStatus(e));
            sendFailureResponse(requestMessage, status);
        }
    }

    private void unsubscribe(@NonNull UMessage requestMessage) {
        try {
            sendResponse(requestMessage, packToAny(mSubscriptionHandler.unsubscribe(requestMessage)));
        } catch (Exception e) {
            final UStatus status = logStatus(Log.ERROR, "unsubscribe", toStatus(e));
            sendFailureResponse(requestMessage, status);
        }
    }

    private void fetchSubscriptions(@NonNull UMessage requestMessage) {
        try {
            sendResponse(requestMessage, packToAny(mSubscriptionHandler.fetchSubscriptions(requestMessage)));
        } catch (Exception e) {
            final UStatus status = logStatus(Log.ERROR, "fetchSubscriptions", toStatus(e));
            sendFailureResponse(requestMessage, status);
        }
    }

    private void fetchSubscribers(@NonNull UMessage requestMessage) {
        try {
            sendResponse(requestMessage, packToAny(mSubscriptionHandler.fetchSubscribers(requestMessage)));
        } catch (Exception e) {
            final UStatus status = logStatus(Log.ERROR, "fetchSubscribers", toStatus(e));
            sendFailureResponse(requestMessage, status);
        }
    }

    private void registerForNotifications(@NonNull UMessage requestMessage) {
        try {
            sendResponse(requestMessage, packToAny(mSubscriptionHandler.registerForNotifications(requestMessage)));
        } catch (Exception e) {
            final UStatus status = logStatus(Log.ERROR, "registerForNotifications", toStatus(e));
            sendFailureResponse(requestMessage, status);
        }
    }

    private void unregisterForNotifications(@NonNull UMessage requestMessage) {
        try {
            sendResponse(requestMessage, packToAny(mSubscriptionHandler.unregisterForNotifications(requestMessage)));
        } catch (Exception e) {
            final UStatus status = logStatus(Log.ERROR, "unregisterForNotifications", toStatus(e));
            sendFailureResponse(requestMessage, status);
        }
    }

    private void sendResponse(@NonNull UMessage requestMessage, @NonNull UPayload responsePayload) {
        mUBus.send(UMessageBuilder.response(requestMessage.getAttributes()).build(responsePayload), mClientToken);
    }

    private void sendFailureResponse(@NonNull UMessage requestMessage, @NonNull UStatus status) {
        mUBus.send(UMessageBuilder.response(requestMessage.getAttributes())
                .withCommStatus(status.getCode())
                .build(packToAny(status)), mClientToken);
    }

    protected void sendSubscriptionUpdate(@NonNull UUri sink, @NonNull Update update) {
        mUBus.send(UMessageBuilder.notification(TOPIC_SUBSCRIPTION_UPDATE, sink).build(packToAny(update)), mClientToken);
    }

    protected void notifySubscriptionChanged(@NonNull Update update) {
        mSubscriptionListeners.forEach((listener -> listener.onSubscriptionChanged(update)));
    }

    public void registerListener(@NonNull SubscriptionListener listener) {
        mSubscriptionListeners.add(listener);
    }

    public void unregisterListener(@NonNull SubscriptionListener listener) {
        mSubscriptionListeners.remove(listener);
    }

    public @NonNull Set<SubscriptionData> getSubscriptions(@NonNull UUri topic) {
        return mSubscriptionHandler.getSubscriptions(topic);
    }

    public @NonNull Set<SubscriptionData> getSubscriptionsWithExpiryTime() {
        return mSubscriptionHandler.getSubscriptionsWithExpiryTime();
    }

    protected void unsubscribe(@NonNull UUri topic, @NonNull UUri subscriber) {
        mSubscriptionHandler.unsubscribe(topic, subscriber);
    }

    protected @NonNull Credentials getCallerCredentials(@NonNull UUID requestId) throws UStatusException {
        return mUBus.getCallerCredentials(requestId);
    }

    protected static @NonNull UStatus logStatus(int priority, @NonNull String method, @NonNull UStatus status,
            Object... args) {
        Log.println(priority, TAG, status(method, status, args));
        return status;
    }

    @VisibleForTesting
    @NonNull ScheduledExecutorService getExecutor() {
        return mExecutor;
    }

    @VisibleForTesting
    void inject(@NonNull UMessage message) {
        mMessageHandler.onReceive(message);
    }

    @VisibleForTesting
    void inject(@NonNull Intent intent) {
        mPackageChangeReceiver.onReceive(mContext, intent);
    }
}
