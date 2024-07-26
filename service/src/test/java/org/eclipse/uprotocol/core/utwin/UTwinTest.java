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
import static org.eclipse.uprotocol.communication.UPayload.packToAny;
import static org.eclipse.uprotocol.communication.UPayload.unpack;
import static org.eclipse.uprotocol.core.utwin.UTwin.METHOD_GET_LAST_MESSAGES;
import static org.eclipse.uprotocol.core.utwin.UTwin.METHOD_SET_LAST_MESSAGE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.protobuf.Message;

import org.eclipse.uprotocol.communication.UStatusException;
import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.core.UCore;
import org.eclipse.uprotocol.core.ubus.UBus;
import org.eclipse.uprotocol.core.utwin.v2.GetLastMessagesRequest;
import org.eclipse.uprotocol.core.utwin.v2.GetLastMessagesResponse;
import org.eclipse.uprotocol.core.utwin.v2.MessageResponse;
import org.eclipse.uprotocol.transport.builder.UMessageBuilder;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;
import org.eclipse.uprotocol.v1.UUriBatch;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RuntimeEnvironment;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class UTwinTest extends TestBase {
    private final UBus mUBus = mock(UBus.class);
    private UTwin mUTwin;

    @Before
    public void setUp() {
        final Context context = RuntimeEnvironment.getApplication();
        mUTwin = new UTwin(context);
        final UCore uCore = newMockUCoreBuilder(context)
                .setUBus(mUBus)
                .setUTwin(mUTwin)
                .build();
        doReturn(STATUS_OK).when(mUBus).registerClient(any(), any(), any());
        doReturn(STATUS_OK).when(mUBus).unregisterClient(any());
        doReturn(STATUS_OK).when(mUBus).enableDispatching(any(), any());
        doReturn(STATUS_OK).when(mUBus).disableDispatching(any(), any());
        mUTwin.init(uCore);
    }

    private static @NonNull UUriBatch buildUUriBatch(@NonNull Collection<UUri> uris) {
        final UUriBatch.Builder builder = UUriBatch.newBuilder();
        uris.forEach(builder::addUris);
        return builder.build();
    }

    private @NonNull GetLastMessagesResponse invokeGetLastMessages(@NonNull Collection<UUri> topics) {
        return invokeGetLastMessages(GetLastMessagesRequest.newBuilder()
                .setTopics(buildUUriBatch(topics))
                .build());
    }

    private @NonNull GetLastMessagesResponse invokeGetLastMessages(@NonNull Message message) {
        final ArgumentCaptor<UMessage> captor = ArgumentCaptor.forClass(UMessage.class);
        mUTwin.inject(UMessageBuilder.request(CLIENT_URI, METHOD_GET_LAST_MESSAGES, TTL).build(packToAny(message)));
        verify(mUBus, timeout(DELAY_LONG_MS).times(1)).send(captor.capture(), any());
        System.out.println();
        return unpack(captor.getValue(), GetLastMessagesResponse.class)
                .orElseThrow(() -> new UStatusException(captor.getValue().getAttributes().getCommstatus(), ""));
    }

    private @NonNull UStatus invokeSetLastMessage(UMessage message) {
        final ArgumentCaptor<UMessage> captor = ArgumentCaptor.forClass(UMessage.class);
        mUTwin.inject(UMessageBuilder.request(CLIENT_URI, METHOD_SET_LAST_MESSAGE, TTL).build(packToAny(message)));
        verify(mUBus, timeout(DELAY_LONG_MS).times(1)).send(captor.capture(), any());
        return unpack(captor.getValue(), UStatus.class).orElseThrow(RuntimeException::new);
    }

    @Test
    public void testInit() {
        verify(mUBus, times(1)).registerClient(eq(UTwin.SERVICE), any(), any());
        verify(mUBus, times(1)).enableDispatching(argThat(it -> METHOD_GET_LAST_MESSAGES.equals(it.sink())), any());
        verify(mUBus, times(1)).enableDispatching(argThat(it -> METHOD_SET_LAST_MESSAGE.equals(it.sink())), any());
    }

    @Test
    public void testStartup() {
        mUTwin.startup();
        assertTrue(mUTwin.getTopics().isEmpty());
    }

    @Test
    public void testShutdown() {
        mUTwin.shutdown();
        verify(mUBus, times(1)).disableDispatching(argThat(filter -> METHOD_GET_LAST_MESSAGES.equals(filter.sink())), any());
        verify(mUBus, times(1)).disableDispatching(argThat(it -> METHOD_SET_LAST_MESSAGE.equals(it.sink())), any());
        verify(mUBus, times(1)).unregisterClient(any());
    }

    @Test
    public void testShutdownTimeout() {
        mUTwin.getExecutor().execute(() -> sleep(200));
        mUTwin.shutdown();
        verify(mUBus, times(1)).unregisterClient(any());
    }

    @Test
    public void testShutdownInterrupted() {
        mUTwin.getExecutor().execute(() -> sleep(200));
        final Thread thread = new Thread(() -> mUTwin.shutdown());
        thread.start();
        thread.interrupt();
        verify(mUBus, timeout(DELAY_LONG_MS).times(1)).unregisterClient(any());
    }

    @Test
    public void testClearCache() {
        assertTrue(mUTwin.addMessage(UMessageBuilder.publish(RESOURCE_URI).build()));
        assertEquals(1, mUTwin.getMessageCount());
        mUTwin.clearCache();
        assertEquals(0, mUTwin.getMessageCount());
    }

    @Test
    public void testSetLastMessage() {
        assertStatus(UCode.PERMISSION_DENIED, invokeSetLastMessage(UMessageBuilder.publish(RESOURCE_URI).build()));
    }

    @Test
    public void testGetLastMessages() {
        final List<UUri> topics = List.of(RESOURCE_URI, RESOURCE2_URI);
        final List<UMessage> messages = topics.stream()
                .map(topic -> UMessageBuilder.publish(topic).build())
                .toList();
        messages.forEach(message -> mUTwin.addMessage(message));
        final List<MessageResponse> responses = invokeGetLastMessages(topics).getResponsesList();
        assertEquals(topics.size(), responses.size());
        responses.forEach(response -> {
            assertStatus(UCode.OK, response.getStatus());
            final UUri topic = response.getTopic();
            final UMessage message = response.getMessage();
            assertEquals(topic, message.getAttributes().getSource());
            assertTrue(topics.contains(topic));
            assertTrue(messages.contains(message));
        });
    }

    @Test
    public void testGetLastMessageNotFound() {
        final List<MessageResponse> responses = invokeGetLastMessages(List.of(RESOURCE_URI)).getResponsesList();
        assertEquals(1, responses.size());
        assertStatus(UCode.NOT_FOUND, responses.get(0).getStatus());
        assertEquals(RESOURCE_URI, responses.get(0).getTopic());
        assertFalse(responses.get(0).hasMessage());
    }

    @Test
    public void testGetLastMessageEmptyTopic() {
        final List<MessageResponse> responses = invokeGetLastMessages(List.of(EMPTY_URI)).getResponsesList();
        assertEquals(1, responses.size());
        assertStatus(UCode.INVALID_ARGUMENT, responses.get(0).getStatus());
        assertEquals(EMPTY_URI, responses.get(0).getTopic());
        assertFalse(responses.get(0).hasMessage());
    }

    @Test
    public void testGetLastMessageUnexpectedPayload() {
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () -> invokeGetLastMessages(EMPTY_URI));
    }

    @Test
    public void testAddMessage() {
        final UMessage message = UMessageBuilder.publish(RESOURCE_URI).build();
        assertTrue(mUTwin.addMessage(message));
        assertEquals(message, mUTwin.getMessage(RESOURCE_URI));
    }

    @Test
    public void testRemoveMessage() {
        testAddMessage();
        assertTrue(mUTwin.removeMessage(RESOURCE_URI));
        assertNull(mUTwin.getMessage(RESOURCE_URI));
    }

    @Test
    public void testGetMessage() {
        final UMessage message = UMessageBuilder.publish(RESOURCE_URI).build();
        assertTrue(mUTwin.addMessage(message));
        assertEquals(message, mUTwin.getMessage(RESOURCE_URI));
        assertTrue(mUTwin.removeMessage(RESOURCE_URI));
        assertNull(mUTwin.getMessage(RESOURCE_URI));
    }

    @Test
    public void testGetTopics() {
        assertTrue(mUTwin.getTopics().isEmpty());
        final Set<UUri> topics = Set.of(RESOURCE_URI, RESOURCE2_URI);
        topics.forEach(topic -> mUTwin.addMessage(UMessageBuilder.publish(topic).build()));
        assertEquals(topics, mUTwin.getTopics());
    }
}
