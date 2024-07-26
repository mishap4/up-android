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

import static com.google.protobuf.util.Timestamps.fromMillis;

import static org.eclipse.uprotocol.common.util.log.Formatter.stringify;
import static org.eclipse.uprotocol.uri.factory.UriFactory.ANY;
import static org.eclipse.uprotocol.uri.factory.UriFactory.WILDCARD_RESOURCE_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.core.UCore;
import org.eclipse.uprotocol.core.ubus.client.Client;
import org.eclipse.uprotocol.core.ubus.client.ClientManager;
import org.eclipse.uprotocol.core.usubscription.SubscriptionData;
import org.eclipse.uprotocol.core.usubscription.SubscriptionListener;
import org.eclipse.uprotocol.core.usubscription.USubscription;
import org.eclipse.uprotocol.core.usubscription.v3.SubscribeAttributes;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriberInfo;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus.State;
import org.eclipse.uprotocol.core.usubscription.v3.Update;
import org.eclipse.uprotocol.core.utwin.UTwin;
import org.eclipse.uprotocol.transport.UListener;
import org.eclipse.uprotocol.transport.builder.UMessageBuilder;
import org.eclipse.uprotocol.uri.validator.UriFilter;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UUri;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class DispatcherTest extends TestBase {
    private UTwin mUTwin;
    private USubscription mUSubscription;
    private ClientManager mClientManager;
    private RpcHandler mRpcHandler;
    private Dispatcher mDispatcher;
    private ThrottlingMonitor mThrottlingMonitor;
    private SubscriptionListener mSubscriptionListener;
    private SubscriptionCache mSubscriptionCache;
    private Client mClient;
    private Client mServer;

    @Before
    public void setUp() {
        setLogLevel(Log.VERBOSE);
        final Context context = spy(Context.class);
        mUTwin = spy(new UTwin(context));
        mUSubscription = mock(USubscription.class);
        mClientManager = spy(new ClientManager(context));
        mRpcHandler = mock(RpcHandler.class);
        mDispatcher = new Dispatcher(mRpcHandler);
        mThrottlingMonitor = spy(new ThrottlingMonitor());

        final UCore uCore = newMockUCoreBuilder(context)
                .setUBus(new UBus(context, mClientManager, mDispatcher, mThrottlingMonitor))
                .setUTwin(mUTwin)
                .setUSubscription(mUSubscription)
                .build();
        uCore.init();

        mSubscriptionListener = mDispatcher.getSubscriptionListener();
        mSubscriptionCache = mDispatcher.getSubscriptionCache();
        mClient = registerNewClient(CLIENT_URI);
        mServer = registerNewClient(SERVICE_URI);
    }

    private static void setLogLevel(int level) {
        UBus.Component.DEBUG = (level <= Log.DEBUG);
        UBus.Component.VERBOSE = (level <= Log.VERBOSE);
    }

    @SuppressWarnings("SameParameterValue")
    private void waitAsyncCompletion(long timeout) {
        try {
            mDispatcher.getExecutor().submit(() -> {}).get(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    private Client registerNewClient(@NonNull UUri clientUri) {
        return registerNewClient(clientUri, new Binder(), mock(UListener.class));
    }

    private <T> Client registerNewClient(@NonNull UUri clientUri, @NonNull IBinder clientToken, @NonNull T listener) {
        assertStatus(UCode.OK, mClientManager.registerClient(PACKAGE_NAME, clientUri, clientToken, listener));
        final Client client = mClientManager.getClient(clientToken);
        assertNotNull(client);
        return client;
    }

    private @NonNull Client registerRemoteServer(@NonNull IBinder clientToken) {
        assertStatus(UCode.OK, mClientManager.registerClient(PACKAGE_NAME, REMOTE_CLIENT_URI, clientToken, mock(UListener.class)));
        final Client client = mClientManager.getClient(clientToken);
        assertNotNull(client);
        return client;
    }

    @SuppressWarnings("UnusedReturnValue")
    private @NonNull Client registerReceiver(@NonNull UUri topic, @NonNull final Client client, boolean shouldSubscribe) {
        if (shouldSubscribe) {
            injectSubscription(new SubscriptionData(topic, client.getUri()));
        }
        assertStatus(UCode.OK, mDispatcher.enableDispatching(new UriFilter(topic, ANY), client));
        return client;
    }

    private void injectSubscription(@NonNull SubscriptionData subscription) {
        doReturn(Set.of(subscription)).when(mUSubscription).getSubscriptions(subscription.topic());
    }

    private static @NonNull Update buildUpdate(@NonNull SubscriptionData subscription, @NonNull State state) {
        return Update.newBuilder()
                .setTopic(subscription.topic())
                .setSubscriber(SubscriberInfo.newBuilder()
                        .setUri(subscription.subscriber()))
                .setStatus(SubscriptionStatus.newBuilder()
                        .setState(state))
                .setAttributes(SubscribeAttributes.newBuilder()
                        .setExpire(fromMillis(subscription.expiryTime()))
                        .setSamplePeriodMs(subscription.samplingPeriod()))
                .build();
    }

    private static void assertEqualsExt(SubscriptionData expected, SubscriptionData actual) {
        assertEquals(expected, actual);
        assertEquals(expected.samplingPeriod(), actual.samplingPeriod());
    }

    private void verifyMessageReceived(UMessage message, int times, @NonNull Client client) {
        if (message != null) {
            verify(((UListener) client.getListener()), timeout(DELAY_LONG_MS).times(times)).onReceive(message);
        } else {
            verify(((UListener) client.getListener()), timeout(DELAY_LONG_MS).times(times)).onReceive(any());
        }
    }

    private void verifyMessageNotReceived(UMessage message, @NonNull Client client) {
        verifyMessageReceived(message, 0, client);
    }

    private void verifyNoMessagesReceived(@NonNull Client client) {
        verifyMessageReceived(null, 0, client);
    }

    private void verifyMessageCached(@NonNull UMessage message) {
        assertEquals(message, mUTwin.getMessage(message.getAttributes().getSource()));
    }

    private void verifyMessageNotCached(@NonNull UMessage message) {
        final UMessage cachedMessage = mUTwin.getMessage(message.getAttributes().getSource());
        if (cachedMessage != null) {
            assertNotEquals(message.getAttributes().getId(), cachedMessage.getAttributes().getId());
        }
    }

    @Test
    public void testInit() {
        verify(mRpcHandler, times(1)).init(any());
        verify(mUSubscription, times(1)).registerListener(mDispatcher.getSubscriptionListener());
        verify(mClientManager, times(1)).registerListener(mDispatcher.getClientRegistrationListener());
    }

    @Test
    public void testStartup() {
        mDispatcher.startup();
        verify(mRpcHandler, times(1)).startup();
    }

    @Test
    public void testShutdown() {
        mDispatcher.shutdown();
        verify(mRpcHandler, times(1)).shutdown();
        verify(mUSubscription, times(1)).unregisterListener(mDispatcher.getSubscriptionListener());
        verify(mClientManager, times(1)).unregisterListener(mDispatcher.getClientRegistrationListener());
    }

    @Test
    public void testShutdownTimeout() {
        mDispatcher.getExecutor().schedule(() -> sleep(200), 0, TimeUnit.MILLISECONDS);
        mDispatcher.shutdown();
        verify(mRpcHandler, times(1)).shutdown();
        verify(mUSubscription, times(1)).unregisterListener(any());
        verify(mClientManager, times(1)).unregisterListener(any());
    }

    @Test
    public void testShutdownInterrupted() {
        mDispatcher.getExecutor().schedule(() -> sleep(200), 0, TimeUnit.MILLISECONDS);
        final Thread thread = new Thread(() -> mDispatcher.shutdown());
        thread.start();
        thread.interrupt();
        verify(mRpcHandler, timeout(DELAY_MS).times(1)).shutdown();
        verify(mUSubscription, timeout(DELAY_MS).times(1)).unregisterListener(any());
        verify(mClientManager, timeout(DELAY_MS).times(1)).unregisterListener(any());
    }

    @Test
    public void testClearCache() {
        testDispatchFromPublishMessage();
        assertFalse(mSubscriptionCache.isEmpty());
        mDispatcher.clearCache();
        assertTrue(mSubscriptionCache.isEmpty());
        verify(mRpcHandler, times(1)).clearCache();
    }

    @Test
    public void testOnSubscriptionChanged() {
        setLogLevel(Log.INFO);

        SubscriptionData subscription = new SubscriptionData(RESOURCE_URI, CLIENT_URI);
        injectSubscription(subscription);
        mSubscriptionListener.onSubscriptionChanged(buildUpdate(subscription, State.SUBSCRIBED));
        waitAsyncCompletion(DELAY_MS);
        assertEqualsExt(subscription, mSubscriptionCache.getSubscriptions(subscription.topic()).iterator().next());

        subscription = new SubscriptionData(RESOURCE_URI, CLIENT_URI, 0, 100);
        mSubscriptionListener.onSubscriptionChanged(buildUpdate(subscription, State.SUBSCRIBED));
        waitAsyncCompletion(DELAY_MS);
        assertEqualsExt(subscription, mSubscriptionCache.getSubscriptions(subscription.topic()).iterator().next());

        mSubscriptionListener.onSubscriptionChanged(buildUpdate(subscription, State.UNSUBSCRIBED));
        waitAsyncCompletion(DELAY_MS);
        assertFalse(mSubscriptionCache.getSubscriptions(subscription.topic()).contains(subscription));
        verify(mThrottlingMonitor, times(1)).removeTracker(subscription);
    }

    @Test
    public void testOnSubscriptionChangedNegative() {
        SubscriptionData subscription = new SubscriptionData(RESOURCE_URI, CLIENT_URI);
        injectSubscription(subscription);
        mSubscriptionListener.onSubscriptionChanged(buildUpdate(subscription, State.SUBSCRIBED));
        waitAsyncCompletion(DELAY_MS);
        assertEqualsExt(subscription, mSubscriptionCache.getSubscriptions(subscription.topic()).iterator().next());

        SubscriptionData invalidSubscription = new SubscriptionData(EMPTY_URI, subscription.subscriber());
        mSubscriptionListener.onSubscriptionChanged(buildUpdate(invalidSubscription, State.UNSUBSCRIBED));
        waitAsyncCompletion(DELAY_MS);
        assertEqualsExt(subscription, mSubscriptionCache.getSubscriptions(subscription.topic()).iterator().next());

        invalidSubscription = new SubscriptionData(subscription.topic(), EMPTY_URI);
        mSubscriptionListener.onSubscriptionChanged(buildUpdate(invalidSubscription, State.UNSUBSCRIBED));
        waitAsyncCompletion(DELAY_MS);
        assertEqualsExt(subscription, mSubscriptionCache.getSubscriptions(subscription.topic()).iterator().next());

        invalidSubscription = new SubscriptionData(EMPTY_URI, EMPTY_URI);
        mSubscriptionListener.onSubscriptionChanged(buildUpdate(invalidSubscription, State.UNSUBSCRIBED));
        waitAsyncCompletion(DELAY_MS);
        assertEqualsExt(subscription, mSubscriptionCache.getSubscriptions(subscription.topic()).iterator().next());
    }

    @Test
    public void testOnClientUnregistered() {
        assertStatus(UCode.OK, mDispatcher.enableDispatching(RESOURCE_FILTER, mClient));
        assertTrue(mDispatcher.getLinkedClients(RESOURCE_URI).contains(mClient));
        mClientManager.unregisterClient(mClient.getToken());
        assertFalse(mDispatcher.getLinkedClients(RESOURCE_URI).contains(mClient));
    }

    @Test
    public void testEnableDispatching() {
        assertStatus(UCode.OK, mDispatcher.enableDispatching(RESOURCE_FILTER, mClient));
        assertTrue(mDispatcher.getLinkedClients(RESOURCE_URI).contains(mClient));
    }

    @Test
    public void testEnableDispatchingAlreadyEnabled() {
        setLogLevel(Log.INFO);
        testEnableDispatching();
        assertStatus(UCode.OK, mDispatcher.enableDispatching(RESOURCE_FILTER, mClient));
        assertTrue(mDispatcher.getLinkedClients(RESOURCE_URI).contains(mClient));
    }

    @Test
    public void testEnableDispatchingUnauthenticated() {
        assertStatus(UCode.UNAUTHENTICATED, mDispatcher.enableDispatching(new UriFilter(RESOURCE_URI, CLIENT2_URI), mClient));
        assertStatus(UCode.UNAUTHENTICATED, mDispatcher.enableDispatching(new UriFilter(METHOD_URI, CLIENT2_URI), mClient));
    }

    @Test
    public void testEnableDispatchingUnsupported() {
        final UUri uri = UUri.newBuilder(RESOURCE_URI).setResourceId(WILDCARD_RESOURCE_ID).build();
        assertStatus(UCode.INVALID_ARGUMENT, mDispatcher.enableDispatching(new UriFilter(uri, ANY), mClient));
    }

    @Test
    public void testEnableDispatchingForServer() {
        mDispatcher.enableDispatching(METHOD_FILTER, mServer);
        verify(mRpcHandler, times(1)).registerServer(METHOD_URI, mServer);
    }

    @Test
    public void testEnableDispatchingForCaller() {
        assertStatus(UCode.OK, mDispatcher.enableDispatching(new UriFilter(METHOD_URI, CLIENT_URI), mClient));
    }

    @Test
    public void testEnableDispatchingForStreamer() {
        Client server = registerRemoteServer(new Binder());
        assertStatus(UCode.OK, mDispatcher.enableDispatching(new UriFilter(ANY, ANY), server));
        assertStatus(UCode.OK, mDispatcher.enableDispatching(new UriFilter(ANY, REMOTE_CLIENT_URI), server));
        assertStatus(UCode.OK, mDispatcher.enableDispatching(new UriFilter(RESOURCE_URI, ANY), server));
    }

    @Test
    public void testDisableDispatching() {
        assertStatus(UCode.OK, mDispatcher.disableDispatching(RESOURCE_FILTER, mClient));
        assertFalse(mDispatcher.getLinkedClients(RESOURCE_URI).contains(mClient));
    }

    @Test
    public void testDisableDispatchingAlreadyDisabled() {
        setLogLevel(Log.INFO);
        testDisableDispatching();
        assertStatus(UCode.OK, mDispatcher.disableDispatching(RESOURCE_FILTER, mClient));
        assertFalse(mDispatcher.getLinkedClients(RESOURCE_URI).contains(mClient));
    }

    @Test
    public void testDisableDispatchingUnauthenticated() {
        assertStatus(UCode.UNAUTHENTICATED, mDispatcher.disableDispatching(new UriFilter(RESOURCE_URI, CLIENT2_URI), mClient));
        assertStatus(UCode.UNAUTHENTICATED, mDispatcher.disableDispatching(new UriFilter(METHOD_URI, CLIENT2_URI), mClient));
    }

    @Test
    public void testDisableDispatchingUnsupported() {
        final UUri uri = UUri.newBuilder(RESOURCE_URI).setResourceId(WILDCARD_RESOURCE_ID).build();
        assertStatus(UCode.INVALID_ARGUMENT, mDispatcher.disableDispatching(new UriFilter(uri, ANY), mClient));
    }

    @Test
    public void testDisableDispatchingForServer() {
        mDispatcher.disableDispatching(METHOD_FILTER, mServer);
        verify(mRpcHandler, times(1)).unregisterServer(METHOD_URI, mServer);
    }

    @Test
    public void testDisableDispatchingForCaller() {
        assertStatus(UCode.OK, mDispatcher.disableDispatching(new UriFilter(METHOD_URI, CLIENT_URI), mClient));
    }

    @Test
    public void testDisableDispatchingForStreamer() {
        Client server = registerRemoteServer(new Binder());
        assertStatus(UCode.OK, mDispatcher.disableDispatching(new UriFilter(ANY, ANY), server));
        assertStatus(UCode.OK, mDispatcher.disableDispatching(new UriFilter(ANY, REMOTE_CLIENT_URI), server));
        assertStatus(UCode.OK, mDispatcher.disableDispatching(new UriFilter(RESOURCE_URI, ANY), server));
    }

    @Test
    public void testDispatchFromUnknownMessage() {
        final UMessage message = UMessage.getDefaultInstance();
        assertStatus(UCode.UNIMPLEMENTED, mDispatcher.dispatchFrom(message, mServer));
        verifyMessageNotCached(message);
    }

    @Test
    public void testDispatchFromPublishMessageUnauthenticated() {
        final UMessage message = UMessageBuilder.publish(RESOURCE_URI).build();
        assertStatus(UCode.UNAUTHENTICATED, mDispatcher.dispatchFrom(message, mClient));
        verifyMessageNotCached(message);
    }

    @Test
    public void testDispatchFromPublishMessage() {
        registerReceiver(RESOURCE_URI, mClient, true);
        final UMessage message = UMessageBuilder.publish(RESOURCE_URI).build();
        assertStatus(UCode.OK, mDispatcher.dispatchFrom(message, mServer));
        verifyMessageCached(message);
        verifyMessageReceived(message, 1, mClient);
    }

    @Test
    public void testDispatchFromPublishMessageSequence() {
        registerReceiver(RESOURCE_URI, mClient, true);
        final UMessage message1 = UMessageBuilder.publish(RESOURCE_URI).build();
        assertStatus(UCode.OK, mDispatcher.dispatchFrom(message1, mServer));
        verifyMessageCached(message1);
        verifyMessageReceived(message1, 1, mClient);

        final UMessage message2 = UMessageBuilder.publish(RESOURCE_URI).build();
        assertStatus(UCode.OK, mDispatcher.dispatchFrom(message2, mServer));
        verifyMessageCached(message2);
        verifyMessageReceived(message2, 1, mClient);
    }

    @Test
    public void testDispatchFromPublishMessageDuplicated() {
        registerReceiver(RESOURCE_URI, mClient, true);
        final UMessage message = UMessageBuilder.publish(RESOURCE_URI).build();
        assertStatus(UCode.OK, mDispatcher.dispatchFrom(message, mServer));
        assertStatus(UCode.OK, mDispatcher.dispatchFrom(message, mServer));
        verifyMessageCached(message);
        verifyMessageReceived(message, 1, mClient);
    }

    @Test
    public void testDispatchFromPublishMessageLinkedButNotSubscribed() {
        registerReceiver(RESOURCE_URI, mClient, false);
        final UMessage message = UMessageBuilder.publish(RESOURCE_URI).build();
        assertStatus(UCode.OK, mDispatcher.dispatchFrom(message, mServer));
        verifyMessageCached(message);
        verifyMessageNotReceived(message, mClient);
    }

    @Test
    public void testDispatchFromPublishMessageSubscribedButNotLinked() {
        injectSubscription(new SubscriptionData(RESOURCE_URI, mClient.getUri()));
        final UMessage message = UMessageBuilder.publish(RESOURCE_URI).build();
        assertStatus(UCode.OK, mDispatcher.dispatchFrom(message, mServer));
        verifyMessageCached(message);
        verifyMessageNotReceived(message, mClient);
    }

    @Test
    public void testDispatchFromPublishMessageSubscribedRemotely() {
        injectSubscription(new SubscriptionData(RESOURCE_URI, SERVICE_URI_REMOTE));
        final Client client = registerRemoteServer(new Binder());
        final UMessage message = UMessageBuilder.publish(RESOURCE_URI).build();
        assertStatus(UCode.OK, mDispatcher.dispatchFrom(message, mServer));
        verifyMessageCached(message);
        verifyMessageReceived(message, 1, client);
    }

    @Test
    public void testDispatchFromPublishMessageSubscribedRemotelyNoStreamer() {
        injectSubscription(new SubscriptionData(RESOURCE_URI, SERVICE_URI_REMOTE));
        final UMessage message = UMessageBuilder.publish(RESOURCE_URI).build();
        assertStatus(UCode.OK, mDispatcher.dispatchFrom(message, mServer));
        verifyMessageCached(message);
    }

    @Test
    public void testDispatchFromPublishEventSubscribedRemotelyThrottled() {
        final int period = 100;
        injectSubscription(new SubscriptionData(RESOURCE_URI, SERVICE_URI_REMOTE, 0, period));
        final Client client = registerRemoteServer(new Binder());
        UMessage message = UMessageBuilder.publish(RESOURCE_URI).build();
        assertStatus(UCode.OK, mDispatcher.dispatchFrom(message, mServer));
        verifyMessageCached(message);
        verifyMessageReceived(message, 1, client);

        message = UMessageBuilder.publish(RESOURCE_URI).build();
        assertStatus(UCode.OK, mDispatcher.dispatchFrom(message, mServer));
        verifyMessageCached(message);
        verifyMessageReceived(message, 0, client);

        setLogLevel(Log.INFO);
        message = UMessageBuilder.publish(RESOURCE_URI).build();
        assertStatus(UCode.OK, mDispatcher.dispatchFrom(message, mServer));
        verifyMessageCached(message);
        verifyMessageReceived(message, 0, client);

        sleep(period + 10);
        message = UMessageBuilder.publish(RESOURCE_URI).build();
        assertStatus(UCode.OK, mDispatcher.dispatchFrom(message, mServer));
        verifyMessageCached(message);
        verifyMessageReceived(message, 1, client);
    }

    @Test
    public void testDispatchFromRemotePublishMessage() {
        final Client server = registerRemoteServer(new Binder());
        registerReceiver(RESOURCE_URI_REMOTE, mClient, true);
        final UMessage message = UMessageBuilder.publish(RESOURCE_URI_REMOTE).build();
        assertStatus(UCode.OK, mDispatcher.dispatchFrom(message, server));
        verifyMessageCached(message);
        verifyMessageReceived(message, 1, mClient);
    }

    @Test
    public void testDispatchFromNotificationMessageUnauthenticated() {
        final UMessage message = UMessageBuilder.notification(RESOURCE_URI, CLIENT2_URI).build();
        assertStatus(UCode.UNAUTHENTICATED, mDispatcher.dispatchFrom(message, mClient));
        verifyMessageNotCached(message);
    }

    @Test
    public void testDispatchFromNotificationMessage() {
        setLogLevel(Log.INFO);
        registerReceiver(RESOURCE_URI, mClient, false);
        final UMessage message = UMessageBuilder.notification(RESOURCE_URI, mClient.getUri()).build();
        assertStatus(UCode.OK, mDispatcher.dispatchFrom(message, mServer));
        verifyMessageNotCached(message);
        verifyMessageReceived(message, 1, mClient);
    }

    @Test
    public void testDispatchFromRemoteNotificationMessage() {
        final Client server = registerRemoteServer(new Binder());
        registerReceiver(RESOURCE_URI_REMOTE, mClient, false);
        final UMessage message = UMessageBuilder.notification(RESOURCE_URI_REMOTE, mClient.getUri()).build();
        assertStatus(UCode.OK, mDispatcher.dispatchFrom(message, server));
        verifyMessageNotCached(message);
        verifyMessageReceived(message, 1, mClient);
    }

    @Test
    public void testDispatchFromStreamerNotificationEvent() {
        final Client server = registerRemoteServer(new Binder());
        final UUri topic = UUri.newBuilder(REMOTE_CLIENT_URI).setResourceId(RESOURCE_ID).build();
        registerReceiver(topic, mClient, false);
        final UMessage message = UMessageBuilder.notification(topic, mClient.getUri()).build();
        assertStatus(UCode.OK, mDispatcher.dispatchFrom(message, server));
        verifyMessageNotCached(message);
        verifyMessageReceived(message, 1, mClient);
    }

    @Test
    public void testDispatchFromNotificationMessageRegisteredRemotely() {
        final Client client = registerRemoteServer(new Binder());
        final UMessage message = UMessageBuilder.notification(RESOURCE_URI, CLIENT_URI_REMOTE).build();
        assertStatus(UCode.OK, mDispatcher.dispatchFrom(message, mServer));
        verifyMessageNotCached(message);
        verifyMessageReceived(message, 1, client);
    }

    @Test
    public void testDispatchFromNotificationMessageRegisteredRemotelyNoStreamer() {
        final UMessage message = UMessageBuilder.notification(RESOURCE_URI, CLIENT_URI_REMOTE).build();
        assertStatus(UCode.OK, mDispatcher.dispatchFrom(message, mServer));
        verifyMessageNotCached(message);
    }

    @Test
    public void testDispatchFromRequestMessage() {
        final UMessage requestMessage = UMessageBuilder.request(CLIENT_URI, METHOD_URI, TTL).build();
        mDispatcher.dispatchFrom(requestMessage, mClient);
        verify(mRpcHandler, times(1)).handleRequestMessage(requestMessage, mClient);
    }

    @Test
    public void testDispatchFromResponseMessage() {
        final UMessage responseMessage = UMessageBuilder.response(METHOD_URI, CLIENT_URI, ID).build();
        mDispatcher.dispatchFrom(responseMessage, mServer);
        verify(mRpcHandler, times(1)).handleResponseMessage(responseMessage, mServer);
    }

    @Test
    public void testDispatchTo() {
        setLogLevel(Log.INFO);
        assertTrue(mDispatcher.dispatchTo(UMessageBuilder.publish(RESOURCE_URI).build(), mClient));
    }

    @Test
    public void testDispatchToFailure() {
        doThrow(new RuntimeException()).when((UListener) mClient.getListener()).onReceive(any());
        assertFalse(mDispatcher.dispatchTo(UMessageBuilder.publish(RESOURCE_URI).build(), mClient));
    }

    @Test
    public void testDispatchToRetried() {
        doThrow(new RuntimeException()).doNothing().when((UListener) mClient.getListener()).onReceive(any());
        assertTrue(mDispatcher.dispatchTo(UMessageBuilder.publish(RESOURCE_URI).build(), mClient));
    }

    @Test
    public void testDispatchToRetryInterrupted() {
        doThrow(new RuntimeException()).doNothing().when((UListener) mClient.getListener()).onReceive(any());
        Thread.currentThread().interrupt();
        assertFalse(mDispatcher.dispatchTo(UMessageBuilder.publish(RESOURCE_URI).build(), mClient));
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testDispatchToNegative() {
        assertFalse(mDispatcher.dispatchTo(null, mClient));
        assertFalse(mDispatcher.dispatchTo(UMessageBuilder.publish(RESOURCE_URI).build(), null));
    }

    @Test
    public void testDispatchNullMessage() {
        registerReceiver(RESOURCE_URI, mClient, false);
        mDispatcher.dispatch(null);
        verifyNoMessagesReceived(mClient);
    }

    @Test
    public void testGetCaller() {
        mDispatcher.getCaller(ID);
        verify(mRpcHandler, times(1)).getCaller(ID);
    }

    @Test
    public void getCachedMessage() {
        final UMessage message = UMessageBuilder.publish(RESOURCE_URI).build();
        assertTrue(mUTwin.addMessage(message));
        verifyMessageCached(message);
    }

    @Test
    public void getCachedMessageExpired() {
        final UMessage message = UMessageBuilder.publish(RESOURCE_URI).withTtl(100).build();
        assertTrue(mUTwin.addMessage(message));
        verifyMessageCached(message);
        sleep(200);
        assertNull(mUTwin.getMessage(RESOURCE_URI));
    }

    private String dump(@NonNull String... args) {
        final StringWriter out = new StringWriter();
        final PrintWriter writer = new PrintWriter(out);
        mDispatcher.dump(writer, args);
        writer.flush();
        return out.toString();
    }

    @Test
    public void testDump() {
        final UUri topic = RESOURCE_URI;
        mSubscriptionCache.addSubscription(new SubscriptionData(topic, mClient.getUri()));
        mUTwin.addMessage(UMessageBuilder.publish(topic).build());
        mDispatcher.enableDispatching(new UriFilter(topic, ANY), mClient);

        final String output = dump();
        assertTrue(output.contains(stringify(topic)));
    }

    @Test
    public void testDumpTopic() {
        final UUri topic1 = RESOURCE_URI;
        final UUri topic2 = RESOURCE2_URI;
        final Client client1 = registerNewClient(CLIENT_URI);
        final Client client2 = registerNewClient(CLIENT2_URI);
        mSubscriptionCache.addSubscription(new SubscriptionData(topic1, client1.getUri()));

        mDispatcher.enableDispatching(new UriFilter(topic1, ANY), client1);
        mDispatcher.enableDispatching(new UriFilter(topic1, ANY), client2);
        mDispatcher.enableDispatching(new UriFilter(topic2, ANY), client2);

        final String output = dump("-t", stringify(topic1));
        assertTrue(output.contains(stringify(topic1)));
        assertFalse(output.contains(stringify(topic2)));
    }

    @Test
    public void testDumpTopics() {
        final UUri topic1 = RESOURCE_URI;
        final UUri topic2 = RESOURCE2_URI;
        final Client client1 = registerNewClient(CLIENT_URI);
        final Client client2 = registerNewClient(CLIENT2_URI);
        mSubscriptionCache.addSubscription(new SubscriptionData(topic1, client1.getUri()));

        mDispatcher.enableDispatching(new UriFilter(topic1, ANY), client1);
        mDispatcher.enableDispatching(new UriFilter(topic1, ANY), client2);
        mDispatcher.enableDispatching(new UriFilter(topic2, ANY), client2);

        final String output = dump("-t");
        assertTrue(output.contains(stringify(topic1)));
        assertTrue(output.contains(stringify(topic2)));
    }

    @Test
    public void testDumpUnknownArg() {
        final UUri topic = RESOURCE_URI;
        mSubscriptionCache.addSubscription(new SubscriptionData(topic, mClient.getUri()));
        mUTwin.addMessage(UMessageBuilder.publish(topic).build());
        mDispatcher.enableDispatching(new UriFilter(topic, ANY), mClient);

        final String output = dump("-s");
        assertFalse(output.contains(stringify(topic)));
    }
}
