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
package org.eclipse.uprotocol.core.internal.rpc;

import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import org.eclipse.uprotocol.communication.CallOptions;
import org.eclipse.uprotocol.communication.RpcClient;
import org.eclipse.uprotocol.communication.UPayload;
import org.eclipse.uprotocol.communication.UStatusException;
import org.eclipse.uprotocol.core.ubus.UBus;
import org.eclipse.uprotocol.transport.UListener;
import org.eclipse.uprotocol.transport.builder.UMessageBuilder;
import org.eclipse.uprotocol.v1.UAttributes;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UMessageType;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUID;
import org.eclipse.uprotocol.v1.UUri;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public class RpcExecutor implements RpcClient, UListener {
    private static final RpcExecutor EMPTY = new Empty();

    private final UBus mUBus;
    private final UUri mClientUri;
    private final IBinder mClientToken;
    private final Map<UUID, CompletableFuture<UMessage>> mRequests = new ConcurrentHashMap<>();

    public static RpcExecutor empty() {
        return EMPTY;
    }

    private RpcExecutor() {
        mUBus = null;
        mClientUri = UUri.getDefaultInstance();
        mClientToken = null;
    }

    public RpcExecutor(@NonNull UBus uBus, @NonNull UUri clientUri, @NonNull IBinder clientToken) {
        mUBus = uBus;
        mClientUri = clientUri;
        mClientToken = clientToken;
    }

    @VisibleForTesting
    boolean hasPendingRequests() {
        return !mRequests.isEmpty();
    }

    @Override
    public @NonNull CompletionStage<UPayload> invokeMethod(@NonNull UUri methodUri, @NonNull UPayload requestPayload,
            @NonNull CallOptions options) {
        final UMessageBuilder builder = UMessageBuilder.request(mClientUri, methodUri, options.timeout());
        if (!options.token().isBlank()) {
            builder.withToken(options.token());
        }
        final UMessage requestMessage = builder.build(requestPayload);

        final CompletableFuture<UMessage> responseFuture = new CompletableFuture<>();
        responseFuture.whenComplete((response, exception) -> mRequests.remove(requestMessage.getAttributes().getId()));
        mRequests.put(requestMessage.getAttributes().getId(), responseFuture);

        CompletableFuture.runAsync(() -> {
            final UStatus status = mUBus.send(requestMessage, mClientToken);
            if (status.getCode() != UCode.OK) {
                responseFuture.completeExceptionally(new UStatusException(status));
            }
        });
        return responseFuture.thenApply(responseMessage ->
                UPayload.pack(responseMessage.getPayload(), responseMessage.getAttributes().getPayloadFormat())
        );
    }

    @Override
    public void onReceive(@NonNull UMessage message) {
        final UAttributes attributes = message.getAttributes();
        if (attributes.getType() == UMessageType.UMESSAGE_TYPE_RESPONSE) {
            final UUID requestId = attributes.getReqid();
            final CompletableFuture<UMessage> responseFuture = mRequests.remove(requestId);
            if (responseFuture != null) {
                final UCode code = attributes.getCommstatus();
                if (code == UCode.OK) {
                    responseFuture.complete(message);
                } else {
                    responseFuture.completeExceptionally(new UStatusException(code, "Communication failure"));
                }
            }
        }
    }

    private static class Empty extends RpcExecutor {
        @Override
        public @NonNull CompletionStage<UPayload> invokeMethod(@NonNull UUri methodUri,
                @NonNull UPayload requestPayload,  @NonNull CallOptions options) {
            return CompletableFuture.failedFuture(new UStatusException(UCode.UNIMPLEMENTED, "Dummy implementation"));
        }

        @Override
        public void onReceive(@NonNull UMessage message) {
            // Dummy implementation
        }
    }
}
