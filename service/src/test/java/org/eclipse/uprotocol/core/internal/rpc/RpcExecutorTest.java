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

import static org.eclipse.uprotocol.common.util.UStatusUtils.STATUS_OK;
import static org.eclipse.uprotocol.common.util.UStatusUtils.buildStatus;
import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.communication.UPayload.packToAny;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.os.Binder;
import android.os.IBinder;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.communication.CallOptions;
import org.eclipse.uprotocol.communication.UPayload;
import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.core.ubus.UBus;
import org.eclipse.uprotocol.transport.builder.UMessageBuilder;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class RpcExecutorTest extends TestBase {
    protected static final UPayload REQUEST_PAYLOAD = PAYLOAD;
    protected static final UPayload RESPONSE_PAYLOAD = packToAny(STATUS_OK);

    private final UBus mUBus = mock(UBus.class);
    private final IBinder mClientToken = new Binder();
    private RpcExecutor mRpcExecutor;

    @Before
    public void setUp() {
        mRpcExecutor = new RpcExecutor(mUBus, CLIENT_URI, mClientToken);
        doReturn(STATUS_OK).when(mUBus).send(any(), any());
    }

    @Test
    public void testEmpty() {
        final RpcExecutor empty = RpcExecutor.empty();
        assertNotNull(empty);
        final CompletableFuture<UPayload> responseFuture =
                empty.invokeMethod(METHOD_URI, REQUEST_PAYLOAD, OPTIONS).toCompletableFuture();
        assertTrue(responseFuture.isCompletedExceptionally());
        assertFalse(empty.hasPendingRequests());
        empty.onReceive(UMessageBuilder.response(METHOD_URI, CLIENT_URI, ID).build());
    }

    @Test
    public void testInvokeMethod() throws Exception {
        final CompletableFuture<UPayload> responseFuture =
                mRpcExecutor.invokeMethod(METHOD_URI, REQUEST_PAYLOAD, OPTIONS).toCompletableFuture();
        assertTrue(mRpcExecutor.hasPendingRequests());
        final ArgumentCaptor<UMessage> captor = ArgumentCaptor.forClass(UMessage.class);
        verify(mUBus, timeout(DELAY_MS).times(1)).send(captor.capture(), any());
        assertFalse(responseFuture.isDone());
        mRpcExecutor.onReceive(UMessageBuilder.response(captor.getValue().getAttributes()).build(RESPONSE_PAYLOAD));
        assertEquals(RESPONSE_PAYLOAD, responseFuture.get(DELAY_MS, TimeUnit.MILLISECONDS));
        assertTrue(responseFuture.isDone());
        assertFalse(mRpcExecutor.hasPendingRequests());
    }

    @Test
    public void testInvokeMethodCommunicationFailure() {
        final CompletableFuture<UPayload> responseFuture =
                mRpcExecutor.invokeMethod(METHOD_URI, REQUEST_PAYLOAD, OPTIONS).toCompletableFuture();
        assertTrue(mRpcExecutor.hasPendingRequests());
        final ArgumentCaptor<UMessage> captor = ArgumentCaptor.forClass(UMessage.class);
        verify(mUBus, timeout(DELAY_MS).times(1)).send(captor.capture(), any());
        assertFalse(responseFuture.isDone());
        mRpcExecutor.onReceive(UMessageBuilder.response(captor.getValue().getAttributes())
                .withCommStatus(UCode.ABORTED)
                .build());
        final Exception exception = assertThrows(ExecutionException.class,
                () -> responseFuture.get(DELAY_MS, TimeUnit.MILLISECONDS));
        assertStatus(UCode.ABORTED, toStatus(exception));
        assertFalse(mRpcExecutor.hasPendingRequests());
    }

    @Test
    public void testInvokeMethodSendFailed() {
        doReturn(buildStatus(UCode.ABORTED)).when(mUBus).send(any(), any());
        final CompletableFuture<UPayload> responseFuture =
                mRpcExecutor.invokeMethod(METHOD_URI, REQUEST_PAYLOAD, CallOptions.DEFAULT).toCompletableFuture();
        verify(mUBus, timeout(DELAY_LONG_MS).times(1)).send(any(), any());
        final Exception exception = assertThrows(ExecutionException.class,
                () -> responseFuture.get(DELAY_MS, TimeUnit.MILLISECONDS));
        assertStatus(UCode.ABORTED, toStatus(exception));
        assertFalse(mRpcExecutor.hasPendingRequests());
    }

    @Test
    public void testResponseListenerUnexpectedType() {
        mRpcExecutor.onReceive(UMessageBuilder.publish(RESOURCE_URI).build());
        assertFalse(mRpcExecutor.hasPendingRequests());
    }

    @Test
    public void testResponseListenerUnexpectedResponse() {
        mRpcExecutor.invokeMethod(METHOD_URI, PAYLOAD, OPTIONS);
        mRpcExecutor.onReceive(UMessageBuilder.response(METHOD_URI, CLIENT_URI, ID).build());
        assertTrue(mRpcExecutor.hasPendingRequests());
    }
}
