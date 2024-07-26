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

import static org.eclipse.uprotocol.common.util.UStatusUtils.STATUS_OK;
import static org.eclipse.uprotocol.common.util.UStatusUtils.buildStatus;
import static org.eclipse.uprotocol.common.util.UStatusUtils.checkArgument;
import static org.eclipse.uprotocol.common.util.UStatusUtils.isOk;
import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.common.util.log.Formatter.stringify;
import static org.eclipse.uprotocol.core.internal.util.CommonUtils.emptyIfNull;
import static org.eclipse.uprotocol.core.internal.util.UUriUtils.isLocalUri;
import static org.eclipse.uprotocol.core.internal.util.UUriUtils.isRemoteUri;
import static org.eclipse.uprotocol.core.internal.util.UUriUtils.isSameClient;
import static org.eclipse.uprotocol.core.internal.util.log.FormatterExt.stringify;
import static org.eclipse.uprotocol.uri.validator.UriValidator.hasWildcard;
import static org.eclipse.uprotocol.uri.validator.UriValidator.isEmpty;
import static org.eclipse.uprotocol.uri.validator.UriValidator.isRpcMethod;
import static org.eclipse.uprotocol.uri.validator.UriValidator.isRpcResponse;
import static org.eclipse.uprotocol.uri.validator.UriValidator.isTopic;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.core.ubus.client.Client;
import org.eclipse.uprotocol.core.ubus.client.ClientManager;
import org.eclipse.uprotocol.core.ubus.client.ClientManager.RegistrationListener;
import org.eclipse.uprotocol.core.usubscription.SubscriptionData;
import org.eclipse.uprotocol.core.usubscription.SubscriptionListener;
import org.eclipse.uprotocol.core.usubscription.USubscription;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus;
import org.eclipse.uprotocol.core.usubscription.v3.Update;
import org.eclipse.uprotocol.core.utwin.UTwin;
import org.eclipse.uprotocol.uri.serializer.UriSerializer;
import org.eclipse.uprotocol.uri.validator.UriFilter;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UMessageType;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUID;
import org.eclipse.uprotocol.v1.UUri;

import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Dispatcher extends UBus.Component {
    private static final int DISPATCH_RETRY_DELAY_MS = 50;

    private final RpcHandler mRpcHandler;
    private final ScheduledExecutorService mExecutor = Executors.newScheduledThreadPool(1);
    private final SubscriptionCache mSubscriptionCache = new SubscriptionCache();
    private final LinkedClients mLinkedClients = new LinkedClients();
    private UTwin mUTwin;
    private USubscription mUSubscription;
    private ClientManager mClientManager;
    private ThrottlingMonitor mThrottlingMonitor;

    private final SubscriptionListener mSubscriptionListener = this::handleSubscriptionChange;

    private final RegistrationListener mClientRegistrationListener = new RegistrationListener() {
        @Override
        public void onClientUnregistered(@NonNull Client client) {
            mLinkedClients.unlinkFromDispatch(client);
        }
    };

    public Dispatcher() {
        mRpcHandler = new RpcHandler();
    }

    @VisibleForTesting
    Dispatcher(@NonNull RpcHandler rpcHandler) {
        mRpcHandler = rpcHandler;
    }

    @Override
    public void init(@NonNull UBus.Components components) {
        mUTwin = components.getUCore().getUTwin();
        mUSubscription = components.getUCore().getUSubscription();
        mSubscriptionCache.setService(mUSubscription);
        mClientManager = components.getClientManager();
        mThrottlingMonitor = components.getThrottlingMonitor();

        mRpcHandler.init(components);
        mUSubscription.registerListener(mSubscriptionListener);
        mClientManager.registerListener(mClientRegistrationListener);
    }

    @Override
    public void startup() {
        mRpcHandler.startup();
    }

    @Override
    public void shutdown() {
        mRpcHandler.shutdown();
        mExecutor.shutdown();
        try {
            if (!mExecutor.awaitTermination(100, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, join(Key.EVENT, "Timeout while waiting for executor termination"));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        mUSubscription.unregisterListener(mSubscriptionListener);
        mClientManager.unregisterListener(mClientRegistrationListener);
        mSubscriptionCache.clear();
        mLinkedClients.clear();
    }

    @Override
    public void clearCache() {
        mRpcHandler.clearCache();
        mSubscriptionCache.clear();
    }

    @VisibleForTesting
    @NonNull SubscriptionCache getSubscriptionCache() {
        return mSubscriptionCache;
    }

    @VisibleForTesting
    @NonNull SubscriptionListener getSubscriptionListener() {
        return mSubscriptionListener;
    }

    @VisibleForTesting
    @NonNull RegistrationListener getClientRegistrationListener() {
        return mClientRegistrationListener;
    }

    public @NonNull ScheduledExecutorService getExecutor() {
        return mExecutor;
    }

    public Client getCaller(@NonNull UUID requestId) {
        return mRpcHandler.getCaller(requestId);
    }

    public @NonNull UStatus enableDispatching(@NonNull UriFilter filter, @NonNull Client client) {
        try {
            if (client.isRemote() && hasWildcard(filter.sink()) && hasWildcard(filter.source())) {
                // All messages with remote authority are sent to remote client by default
                return STATUS_OK;
            }
            if (isTopic(filter.source()) && !hasWildcard(filter.source())) {
                checkAuthority(filter.sink(), client);
                mLinkedClients.linkToDispatch(filter.source(), client);
                return STATUS_OK;
            }
            if (isRpcMethod(filter.sink())) {
                return mRpcHandler.registerServer(filter.sink(), client);
            }
            if (isRpcResponse(filter.sink())) {
                checkAuthority(filter.sink(), client);
                return STATUS_OK;
            }
            return buildStatus(UCode.INVALID_ARGUMENT, "Invalid or unsupported filter");
        } catch (Exception e) {
            return toStatus(e);
        }
    }

    public @NonNull UStatus disableDispatching(@NonNull UriFilter filter, @NonNull Client client) {
        try {
            if (client.isRemote() && hasWildcard(filter.sink()) && hasWildcard(filter.source())) {
                // All messages with remote authority are sent to remote client by default
                return STATUS_OK;
            }
            if (isTopic(filter.source()) && !hasWildcard(filter.source())) {
                checkAuthority(filter.sink(), client);
                mLinkedClients.unlinkFromDispatch(filter.source(), client);
                return STATUS_OK;
            }
            if (isRpcMethod(filter.sink())) {
                return mRpcHandler.unregisterServer(filter.sink(), client);
            }
            if (isRpcResponse(filter.sink())) {
                checkAuthority(filter.sink(), client);
                return STATUS_OK;
            }
            return buildStatus(UCode.INVALID_ARGUMENT, "Invalid filter");
        } catch (Exception e) {
            return toStatus(e);
        }
    }

    public @NonNull UStatus dispatchFrom(@NonNull UMessage message, @NonNull Client client) {
        final UMessageType type = message.getAttributes().getType();
        return switch (type) {
            case UMESSAGE_TYPE_REQUEST -> mRpcHandler.handleRequestMessage(message, client);
            case UMESSAGE_TYPE_RESPONSE -> mRpcHandler.handleResponseMessage(message, client);
            case UMESSAGE_TYPE_PUBLISH -> handlePublishMessage(message, client);
            case UMESSAGE_TYPE_NOTIFICATION -> handleNotificationMessage(message, client);
            default -> buildStatus(UCode.UNIMPLEMENTED, "Message type '" + type + "' is not supported");
        };
    }

    public boolean dispatchTo(UMessage message, @NonNull Client client) {
        if (message == null) {
            return false;
        }
        UStatus status = STATUS_OK;
        try {
            client.send(message);
        } catch (Exception ignored) {
            // Pause and retry
            try {
                Thread.sleep(DISPATCH_RETRY_DELAY_MS);
                client.send(message);
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                status = toStatus(e);
            }
        }
        if (isOk(status)) {
            if (VERBOSE) {
                logStatus(Log.VERBOSE, "dispatch", status, Key.MESSAGE, stringify(message), Key.CLIENT, client);
            }
            return true;
        } else {
            logStatus(Log.WARN, "dispatch", status, Key.MESSAGE, stringify(message), Key.CLIENT, client);
            return false;
        }
    }

    @VisibleForTesting
    void dispatch(UMessage message) {
        if (message == null) {
            return;
        }
        final UUri source = message.getAttributes().getSource();
        final UUri sink = message.getAttributes().getSink();
        final Client remoteClient = mClientManager.getRemoteClient();
        final Set<Client> clients = new HashSet<>();
        final Consumer<SubscriptionData> subscriptionProcessor = it -> {
            if (isLocalUri(it.subscriber())) {
                mLinkedClients.getClients(it.topic(), it.subscriber(), Collectors.toCollection(() -> clients));
            } else if (remoteClient != null) {
                if (mThrottlingMonitor.canProceed(message, it)) {
                    clients.add(remoteClient);
                } else if (VERBOSE) {
                    Log.v(TAG, join(Key.MESSAGE, "Throttling event", Key.EVENT, stringify(message)));
                }
            }
        };
        final Consumer<UUri> sinkProcessor = it -> {
            if (isLocalUri(it)) {
                mLinkedClients.getClients(source, it, Collectors.toCollection(() -> clients));
            } else if (remoteClient != null) {
                clients.add(remoteClient);
            }
        };
        if (isEmpty(sink)) {
            mSubscriptionCache.getSubscriptions(source).forEach(subscriptionProcessor);
        } else {
            sinkProcessor.accept(sink);
        }
        clients.forEach(client -> dispatchTo(message, client));
    }

    public static void checkAuthority(@NonNull UUri uri, @NonNull Client client) {
        if (client.isLocal()) {
            checkArgument(isSameClient(uri, client.getUri()), UCode.UNAUTHENTICATED,
                    "'" + stringify(uri) + "' doesn't match to '" + stringify(client.getUri()) + "'");
        } else {
            if (!isSameClient(uri, client.getUri())) { // uStreamer on behalf of remote clients
                checkArgument(isRemoteUri(uri), UCode.UNAUTHENTICATED, "URI authority is not remote");
            }
        }
    }

    private @NonNull UStatus handlePublishMessage(@NonNull UMessage message, @NonNull Client client) {
        try {
            checkAuthority(message.getAttributes().getSource(), client);
            if (mUTwin.addMessage(message)) {
                dispatch(message);
            }
            return STATUS_OK;
        } catch (Exception e) {
            return toStatus(e);
        }
    }

    private @NonNull UStatus handleNotificationMessage(@NonNull UMessage message, @NonNull Client client) {
        try {
            checkAuthority(message.getAttributes().getSource(), client);
            dispatch(message);
            return STATUS_OK;
        } catch (Exception e) {
            return toStatus(e);
        }
    }

    private void handleSubscriptionChange(@NonNull Update update) {
        if (DEBUG) {
            Log.d(TAG, join(Key.EVENT, "Subscription changed", Key.SUBSCRIPTION, stringify(update)));
        }
        final SubscriptionData subscription = new SubscriptionData(update);
        if (isEmpty(subscription.topic()) || isEmpty(subscription.subscriber())) {
            return;
        }
        if (update.getStatus().getState() == SubscriptionStatus.State.SUBSCRIBED) {
            mSubscriptionCache.addSubscription(subscription);
        } else {
            mSubscriptionCache.removeSubscription(subscription);
            mThrottlingMonitor.removeTracker(subscription);
        }
    }

    @VisibleForTesting
    @NonNull Set<Client> getLinkedClients(@NonNull UUri topic) {
        return mLinkedClients.getClients(topic);
    }

    protected void dump(@NonNull PrintWriter writer, String[] args) {
        args = emptyIfNull(args);
        if (args.length > 0) {
            if ("-t".equals(args[0])) {
                if (args.length > 1) {
                    dumpTopic(writer, UriSerializer.deserialize(args[1]));
                    return;
                }
            } else {
                mRpcHandler.dump(writer, args);
                return;
            }
        }
        dumpSummary(writer);
        mRpcHandler.dump(writer, args);
    }

    private void dumpSummary(@NonNull PrintWriter writer) {
        writer.println("  ========");
        final Set<Client> clients = mClientManager.getClients();
        writer.println("  There are " + mUTwin.getMessageCount() + " topic(s) with published data, " +
                clients.size() + " registered client(s)");
        clients.forEach(client -> writer.println("    " + client));

        dumpAllTopics(writer);
    }

    private void dumpAllTopics(@NonNull PrintWriter writer) {
        final Set<UUri> publishedTopics = mUTwin.getTopics();
        publishedTopics.forEach(topic -> dumpTopic(writer, topic));
        mLinkedClients.getTopics().stream()
                .filter(topic -> !publishedTopics.contains(topic))
                .forEach(topic -> dumpTopic(writer, topic));
    }

    private void dumpTopic(PrintWriter writer, UUri topic) {
        final UMessage message = mUTwin.getMessage(topic);
        final StringBuilder sb = new StringBuilder("{");
        getLinkedClients(topic).forEach(client -> {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            boolean subscribed = mSubscriptionCache.isTopicSubscribed(topic, client.getUri());
            sb.append(client).append(": ").append((subscribed) ? "SUBSCRIBED" : "NOT SUBSCRIBED");
        });
        final String formattedSubscribers = sb.append("}").toString();

        writer.println("  --------");
        writer.println("    Topic: " + stringify(topic));
        writer.println("  Message: " + stringify(message));
        writer.println("  Clients: " + formattedSubscribers);
    }
}
