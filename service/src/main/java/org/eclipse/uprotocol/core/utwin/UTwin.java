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
package org.eclipse.uprotocol.core.utwin;

import static org.eclipse.uprotocol.common.util.UStatusUtils.STATUS_OK;
import static org.eclipse.uprotocol.common.util.UStatusUtils.buildStatus;
import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.common.util.log.Formatter.status;
import static org.eclipse.uprotocol.common.util.log.Formatter.tag;
import static org.eclipse.uprotocol.communication.UPayload.packToAny;
import static org.eclipse.uprotocol.core.internal.util.UUriUtils.checkTopicUriValid;
import static org.eclipse.uprotocol.uri.validator.UriValidator.DEFAULT_RESOURCE_ID;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.protobuf.Descriptors.ServiceDescriptor;

import org.eclipse.uprotocol.Uoptions;
import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.communication.UPayload;
import org.eclipse.uprotocol.communication.UStatusException;
import org.eclipse.uprotocol.core.UCore;
import org.eclipse.uprotocol.core.internal.handler.MessageHandler;
import org.eclipse.uprotocol.core.ubus.UBus;
import org.eclipse.uprotocol.core.utwin.v2.GetLastMessagesRequest;
import org.eclipse.uprotocol.core.utwin.v2.GetLastMessagesResponse;
import org.eclipse.uprotocol.core.utwin.v2.MessageResponse;
import org.eclipse.uprotocol.core.utwin.v2.UTwinProto;
import org.eclipse.uprotocol.transport.builder.UMessageBuilder;
import org.eclipse.uprotocol.uri.factory.UriFactory;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;

import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@SuppressWarnings("java:S3008")
public class UTwin extends UCore.Component {
    public static final ServiceDescriptor DESCRIPTOR = UTwinProto.getDescriptor().getServices().get(0);
    public static final String NAME = DESCRIPTOR.getOptions().getExtension(Uoptions.serviceName);
    public static final UUri SERVICE = UriFactory.fromProto(DESCRIPTOR, DEFAULT_RESOURCE_ID);
    public static final UUri METHOD_GET_LAST_MESSAGES = UriFactory.fromProto(DESCRIPTOR, 1);
    public static final UUri METHOD_SET_LAST_MESSAGE = UriFactory.fromProto(DESCRIPTOR, 2);

    protected static final String TAG = tag(NAME);
    protected static boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    private final IBinder mClientToken = new Binder();
    private final ExecutorService mExecutor = Executors.newCachedThreadPool();
    private final MessageCache mMessageCache = new MessageCache();
    private UBus mUBus;
    private MessageHandler mMessageHandler;

    public UTwin(@NonNull Context ignored) {}

    @Override
    protected void init(@NonNull UCore uCore) {
        Log.i(TAG, join(Key.STATE, "Init"));
        mUBus = uCore.getUBus();
        mMessageHandler = new MessageHandler(mUBus, SERVICE, mClientToken, mExecutor);
        mUBus.registerClient(SERVICE, mClientToken, mMessageHandler);
        mMessageHandler.registerListener(METHOD_GET_LAST_MESSAGES, this::getLastMessages);
        mMessageHandler.registerListener(METHOD_SET_LAST_MESSAGE, this::setLastMessage);
    }

    @Override
    protected void startup() {
        Log.i(TAG, join(Key.STATE, "Startup"));
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
        mMessageCache.clear();
    }

    @Override
    protected void clearCache() {
        Log.w(TAG, join(Key.EVENT, "Clear cache"));
        mMessageCache.clear();
    }

    private static @NonNull MessageResponse buildMessageResponse(@NonNull UStatus status, @NonNull UUri topic, UMessage message) {
        final MessageResponse.Builder builder = MessageResponse.newBuilder()
                .setStatus(status)
                .setTopic(topic);
        if (message != null) {
            builder.setMessage(message);
        }
        return builder.build();
    }

    private void getLastMessages(@NonNull UMessage requestMessage) {
        try {
            final GetLastMessagesResponse.Builder builder = GetLastMessagesResponse.newBuilder();
            UPayload.unpack(requestMessage, GetLastMessagesRequest.class)
                    .orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, "Invalid payload"))
                    .getTopics().getUrisList().forEach(topic -> builder.addResponses(getLastMessage(topic)));
            sendResponse(requestMessage, packToAny(builder.build()));
        } catch (Exception e) {
            final UStatus status = toStatus(e);
            Log.e(TAG, status("getLastMessages", status));
            sendFailureResponse(requestMessage, status);
        }
    }

    private @NonNull MessageResponse getLastMessage(@NonNull UUri topic) {
        try {
            checkTopicUriValid(topic);
            final UMessage message = mMessageCache.getMessage(topic);
            final UStatus status = (message == null) ? buildStatus(UCode.NOT_FOUND) : STATUS_OK;
            return buildMessageResponse(status, topic, message);
        } catch (Exception e) {
            final UStatus status = toStatus(e);
            Log.e(TAG, status("getLastMessage", status, Key.TOPIC, topic));
            return buildMessageResponse(status, topic, null);
        }
    }

    private void setLastMessage(@NonNull UMessage requestMessage) {
        sendFailureResponse(requestMessage, buildStatus(UCode.PERMISSION_DENIED));
    }

    private void sendResponse(@NonNull UMessage requestMessage, @NonNull UPayload responsePayload) {
        mUBus.send(UMessageBuilder.response(requestMessage.getAttributes()).build(responsePayload), mClientToken);
    }

    private void sendFailureResponse(@NonNull UMessage requestMessage, @NonNull UStatus status) {
        mUBus.send(UMessageBuilder.response(requestMessage.getAttributes())
                .withCommStatus(status.getCode())
                .build(packToAny(status)), mClientToken);
    }

    public boolean addMessage(@NonNull UMessage message) {
        return addMessage(message, null);
    }

    public boolean addMessage(@NonNull UMessage message, Consumer<UMessage> onAdded) {
        return mMessageCache.addMessage(message, onAdded);
    }

    public boolean removeMessage(@NonNull UUri topic) {
        return mMessageCache.removeMessage(topic);
    }

    public @Nullable UMessage getMessage(@NonNull UUri topic) {
        return mMessageCache.getMessage(topic);
    }

    public @NonNull Set<UUri> getTopics() {
        return mMessageCache.getTopics();
    }

    public int getMessageCount() {
        return mMessageCache.size();
    }

    @VisibleForTesting
    Executor getExecutor() {
        return mExecutor;
    }

    @VisibleForTesting
    void inject(@NonNull UMessage message) {
        mMessageHandler.onReceive(message);
    }
}
