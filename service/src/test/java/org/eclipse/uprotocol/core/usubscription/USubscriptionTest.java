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

import static org.eclipse.uprotocol.common.util.UStatusUtils.STATUS_OK;
import static org.eclipse.uprotocol.communication.UPayload.packToAny;
import static org.eclipse.uprotocol.communication.UPayload.unpack;
import static org.eclipse.uprotocol.core.usubscription.USubscription.METHOD_FETCH_SUBSCRIBERS;
import static org.eclipse.uprotocol.core.usubscription.USubscription.METHOD_FETCH_SUBSCRIPTIONS;
import static org.eclipse.uprotocol.core.usubscription.USubscription.METHOD_REGISTER_FOR_NOTIFICATIONS;
import static org.eclipse.uprotocol.core.usubscription.USubscription.METHOD_SUBSCRIBE;
import static org.eclipse.uprotocol.core.usubscription.USubscription.METHOD_UNREGISTER_FOR_NOTIFICATIONS;
import static org.eclipse.uprotocol.core.usubscription.USubscription.METHOD_UNSUBSCRIBE;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.communication.UStatusException;
import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.core.UCore;
import org.eclipse.uprotocol.core.ubus.UBus;
import org.eclipse.uprotocol.core.ubus.client.Credentials;
import org.eclipse.uprotocol.core.usubscription.v3.FetchSubscribersRequest;
import org.eclipse.uprotocol.core.usubscription.v3.FetchSubscribersResponse;
import org.eclipse.uprotocol.core.usubscription.v3.FetchSubscriptionsRequest;
import org.eclipse.uprotocol.core.usubscription.v3.FetchSubscriptionsResponse;
import org.eclipse.uprotocol.core.usubscription.v3.NotificationsRequest;
import org.eclipse.uprotocol.core.usubscription.v3.NotificationsResponse;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriberInfo;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionRequest;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionResponse;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus.State;
import org.eclipse.uprotocol.core.usubscription.v3.UnsubscribeRequest;
import org.eclipse.uprotocol.core.usubscription.v3.UnsubscribeResponse;
import org.eclipse.uprotocol.core.usubscription.v3.Update;
import org.eclipse.uprotocol.transport.builder.UMessageBuilder;
import org.eclipse.uprotocol.v1.UAttributes;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UMessageType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;

import java.util.List;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class USubscriptionTest extends TestBase {
    private final SubscriptionHandler mSubscriptionHandler = mock(SubscriptionHandler.class);
    private final SubscriptionListener mSubscriptionListener = mock(SubscriptionListener.class);
    private final ExpiryMonitor mExpiryMonitor = mock(ExpiryMonitor.class);
    private final UBus mUBus = mock(UBus.class);
    private Context mContext;
    private USubscription mUSubscription;
    private UCore mUCore;

    @Before
    public void setUp() {
        mContext = spy(RuntimeEnvironment.getApplication());
        mUSubscription = new USubscription(mContext, mSubscriptionHandler, mExpiryMonitor);
        mUCore = newMockUCoreBuilder(mContext)
                .setUBus(mUBus)
                .setUSubscription(mUSubscription)
                .build();
        mUSubscription.registerListener(mSubscriptionListener);
        doReturn(STATUS_OK).when(mUBus).registerClient(any(), any(), any());
        doReturn(STATUS_OK).when(mUBus).unregisterClient(any());
        doReturn(STATUS_OK).when(mUBus).enableDispatching(any(), any());
        doReturn(STATUS_OK).when(mUBus).disableDispatching(any(), any());
        mUSubscription.init(mUCore);
    }

    private void verifyResponseSent(@NonNull UMessage requestMessage, @NonNull UCode code) {
        final UAttributes requestAttributes = requestMessage.getAttributes();
        verify(mUBus, timeout(DELAY_MS).times(1)).send(argThat(responseMessage -> {
            final UAttributes responseAttributes = responseMessage.getAttributes();
            return responseAttributes.getCommstatus() == code &&
                   responseAttributes.getReqid().equals(requestAttributes.getId()) &&
                   responseAttributes.getSource().equals(requestAttributes.getSink()) &&
                   responseAttributes.getSink().equals(requestAttributes.getSource());
        }), any());
    }

    @Test
    public void testInit() {
        verify(mSubscriptionHandler, times(1)).init(any());
        verify(mExpiryMonitor, times(1)).init(any());
        verify(mUBus, times(1)).registerClient(eq(USubscription.SERVICE), any(), any());
        verify(mUBus, times(1)).enableDispatching(argThat(it -> METHOD_SUBSCRIBE.equals(it.sink())), any());
        verify(mUBus, times(1)).enableDispatching(argThat(it -> METHOD_UNSUBSCRIBE.equals(it.sink())), any());
        verify(mUBus, times(1)).enableDispatching(argThat(it -> METHOD_FETCH_SUBSCRIPTIONS.equals(it.sink())), any());
        verify(mUBus, times(1)).enableDispatching(argThat(it -> METHOD_REGISTER_FOR_NOTIFICATIONS.equals(it.sink())), any());
        verify(mUBus, times(1)).enableDispatching(argThat(it -> METHOD_UNREGISTER_FOR_NOTIFICATIONS.equals(it.sink())), any());
        verify(mUBus, times(1)).enableDispatching(argThat(it -> METHOD_FETCH_SUBSCRIBERS.equals(it.sink())), any());
        verify(mContext, times(1)).registerReceiver(any(),
                argThat(it -> it.hasAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)), anyInt());
        mUSubscription.init(mUCore);

    }

    @Test
    public void testInitReceiverRegistrationFailure() {
        reset(mContext);
        doThrow(new RuntimeException()).when(mContext).registerReceiver(any(), any(), anyInt());
        mUSubscription.init(mUCore);
        verify(mContext, times(1)).registerReceiver(any(),
                argThat(it -> it.hasAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)), anyInt());
    }

    @Test
    public void testStartup() {
        mUSubscription.startup();
        verify(mSubscriptionHandler, times(1)).startup();
        verify(mExpiryMonitor, times(1)).startup();
    }

    @Test
    public void testShutdown() {
        mUSubscription.shutdown();
        verify(mUBus, times(1)).disableDispatching(argThat(it -> METHOD_SUBSCRIBE.equals(it.sink())), any());
        verify(mUBus, times(1)).disableDispatching(argThat(it -> METHOD_UNSUBSCRIBE.equals(it.sink())), any());
        verify(mUBus, times(1)).disableDispatching(argThat(it -> METHOD_FETCH_SUBSCRIPTIONS.equals(it.sink())), any());
        verify(mUBus, times(1)).disableDispatching(argThat(it -> METHOD_REGISTER_FOR_NOTIFICATIONS.equals(it.sink())), any());
        verify(mUBus, times(1)).disableDispatching(argThat(it -> METHOD_UNREGISTER_FOR_NOTIFICATIONS.equals(it.sink())), any());
        verify(mUBus, times(1)).disableDispatching(argThat(it -> METHOD_FETCH_SUBSCRIBERS.equals(it.sink())), any());
        verify(mUBus, times(1)).unregisterClient(any());
        verify(mSubscriptionHandler, times(1)).shutdown();
        verify(mExpiryMonitor, times(1)).shutdown();
        verify(mContext, times(1)).unregisterReceiver(any());
    }

    @Test
    public void testShutdownReceiverUnregistrationFailure() {
        reset(mContext);
        doThrow(new RuntimeException()).when(mContext).unregisterReceiver(any());
        mUSubscription.shutdown();
        verify(mContext, times(1)).unregisterReceiver(any());
    }

    @Test
    public void testShutdownTimeout() {
        mUSubscription.getExecutor().execute(() -> sleep(200));
        mUSubscription.shutdown();
        verify(mUBus, times(1)).unregisterClient(any());
    }

    @Test
    public void testShutdownInterrupted() {
        mUSubscription.getExecutor().execute(() -> sleep(200));
        final Thread thread = new Thread(() -> mUSubscription.shutdown());
        thread.start();
        thread.interrupt();
        verify(mUBus, timeout(DELAY_LONG_MS).times(1)).unregisterClient(any());
    }

    @Test
    public void testHandlePackageRemoved() {
        mUSubscription.inject(new Intent(Intent.ACTION_PACKAGE_FULLY_REMOVED)
                .setData(new Uri.Builder().path(PACKAGE_NAME).build()));
        verify(mSubscriptionHandler, timeout(DELAY_MS).times(1)).unsubscribeAllFromPackage(PACKAGE_NAME);
    }

    @Test
    public void testHandlePackageRemovedWhenStarted() {
        final PackageManager manager = mock(PackageManager.class);
        doReturn(manager).when(mContext).getPackageManager();
        doReturn(List.of(buildPackageInfo(PACKAGE2_NAME))).when(manager).getPackagesHoldingPermissions(any(), anyInt());
        doReturn(List.of(PACKAGE_NAME, PACKAGE2_NAME)).when(mSubscriptionHandler).getSubscribedPackages();
        mUSubscription.startup();
        verify(mSubscriptionHandler, timeout(DELAY_LONG_MS).times(1)).unsubscribeAllFromPackage(PACKAGE_NAME);
        verify(mSubscriptionHandler, times(0)).unsubscribeAllFromPackage(PACKAGE2_NAME);
    }

    @Test
    public void testHandlePackageRemovedNoDetails() {
        mUSubscription.inject(new Intent(Intent.ACTION_PACKAGE_FULLY_REMOVED));
        verify(mSubscriptionHandler, timeout(DELAY_MS).times(0)).unsubscribeAllFromPackage(PACKAGE_NAME);
    }

    @Test
    public void testHandleUnknownIntent() {
        mUSubscription.inject(new Intent(Intent.ACTION_PACKAGE_REMOVED));
        verify(mSubscriptionHandler, timeout(DELAY_MS).times(0)).unsubscribeAllFromPackage(any());
    }

    @Test
    public void testHandleSubscribeRequestMessage() {
        final UMessage requestMessage = UMessageBuilder.request(CLIENT_URI, METHOD_SUBSCRIBE, TTL)
                .build(packToAny(SubscriptionRequest.getDefaultInstance()));
        doReturn(SubscriptionResponse.getDefaultInstance()).when(mSubscriptionHandler).subscribe(requestMessage);
        mUSubscription.inject(requestMessage);
        verify(mSubscriptionHandler, timeout(DELAY_LONG_MS).times(1)).subscribe(requestMessage);
        verifyResponseSent(requestMessage, UCode.OK);
    }

    @Test
    public void testHandleSubscribeRequestFailure() {
        final UMessage requestMessage = UMessageBuilder.request(CLIENT_URI, METHOD_SUBSCRIBE, TTL)
                .build(packToAny(SubscriptionRequest.getDefaultInstance()));
        doThrow(new UStatusException(STATUS_UNKNOWN)).when(mSubscriptionHandler).subscribe(requestMessage);
        mUSubscription.inject(requestMessage);
        verify(mSubscriptionHandler, timeout(DELAY_LONG_MS).times(1)).subscribe(requestMessage);
        verifyResponseSent(requestMessage, UCode.UNKNOWN);
    }

    @Test
    public void testHandleUnsubscribeRequestMessage() {
        final UMessage requestMessage = UMessageBuilder.request(CLIENT_URI, METHOD_UNSUBSCRIBE, TTL)
                .build(packToAny(UnsubscribeRequest.getDefaultInstance()));
        doReturn(UnsubscribeResponse.getDefaultInstance()).when(mSubscriptionHandler).unsubscribe(requestMessage);
        mUSubscription.inject(requestMessage);
        verify(mSubscriptionHandler, timeout(DELAY_MS).times(1)).unsubscribe(requestMessage);
        verifyResponseSent(requestMessage, UCode.OK);
    }

    @Test
    public void testHandleUnsubscribeRequestFailure() {
        final UMessage requestMessage = UMessageBuilder.request(CLIENT_URI, METHOD_UNSUBSCRIBE, TTL)
                .build(packToAny(UnsubscribeRequest.getDefaultInstance()));
        doThrow(new UStatusException(STATUS_UNKNOWN)).when(mSubscriptionHandler).unsubscribe(requestMessage);
        mUSubscription.inject(requestMessage);
        verify(mSubscriptionHandler, timeout(DELAY_MS).times(1)).unsubscribe(requestMessage);
        verifyResponseSent(requestMessage, UCode.UNKNOWN);
    }

    @Test
    public void testHandleRegisterForNotificationsRequestMessage() {
        final UMessage requestMessage = UMessageBuilder.request(CLIENT_URI, METHOD_REGISTER_FOR_NOTIFICATIONS, TTL)
                .build(packToAny(NotificationsRequest.getDefaultInstance()));
        doReturn(NotificationsResponse.getDefaultInstance()).when(mSubscriptionHandler).registerForNotifications(requestMessage);
        mUSubscription.inject(requestMessage);
        verify(mSubscriptionHandler, timeout(DELAY_MS).times(1)).registerForNotifications(requestMessage);
        verifyResponseSent(requestMessage, UCode.OK);
    }

    @Test
    public void testHandleRegisterForNotificationsRequestFailure() {
        final UMessage requestMessage = UMessageBuilder.request(CLIENT_URI, METHOD_REGISTER_FOR_NOTIFICATIONS, TTL)
                .build(packToAny(NotificationsRequest.getDefaultInstance()));
        doThrow(new UStatusException(STATUS_UNKNOWN)).when(mSubscriptionHandler).registerForNotifications(requestMessage);
        mUSubscription.inject(requestMessage);
        verify(mSubscriptionHandler, timeout(DELAY_MS).times(1)).registerForNotifications(requestMessage);
        verifyResponseSent(requestMessage, UCode.UNKNOWN);
    }

    @Test
    public void testHandleUnregisterForNotificationsRequestMessage() {
        final UMessage requestMessage = UMessageBuilder.request(CLIENT_URI, METHOD_UNREGISTER_FOR_NOTIFICATIONS, TTL)
                .build(packToAny(NotificationsRequest.getDefaultInstance()));
        doReturn(NotificationsResponse.getDefaultInstance()).when(mSubscriptionHandler).unregisterForNotifications(requestMessage);
        mUSubscription.inject(requestMessage);
        verify(mSubscriptionHandler, timeout(DELAY_MS).times(1)).unregisterForNotifications(requestMessage);
        verifyResponseSent(requestMessage, UCode.OK);
    }

    @Test
    public void testHandleUnregisterForNotificationsRequestFailure() {
        final UMessage requestMessage = UMessageBuilder.request(CLIENT_URI, METHOD_UNREGISTER_FOR_NOTIFICATIONS, TTL)
                .build(packToAny(NotificationsRequest.getDefaultInstance()));
        doThrow(new UStatusException(STATUS_UNKNOWN)).when(mSubscriptionHandler).unregisterForNotifications(requestMessage);
        mUSubscription.inject(requestMessage);
        verify(mSubscriptionHandler, timeout(DELAY_MS).times(1)).unregisterForNotifications(requestMessage);
        verifyResponseSent(requestMessage, UCode.UNKNOWN);
    }

    @Test
    public void testHandleFetchSubscriptionsRequestMessage() {
        final UMessage requestMessage = UMessageBuilder.request(CLIENT_URI, METHOD_FETCH_SUBSCRIPTIONS, TTL)
                .build(packToAny(FetchSubscriptionsRequest.getDefaultInstance()));
        doReturn(FetchSubscriptionsResponse.getDefaultInstance()).when(mSubscriptionHandler).fetchSubscriptions(requestMessage);
        mUSubscription.inject(requestMessage);
        verify(mSubscriptionHandler, timeout(DELAY_MS).times(1)).fetchSubscriptions(requestMessage);
        verifyResponseSent(requestMessage, UCode.OK);
    }

    @Test
    public void testHandleFetchSubscriptionsRequestFailure() {
        final UMessage requestMessage = UMessageBuilder.request(CLIENT_URI, METHOD_FETCH_SUBSCRIPTIONS, TTL)
                .build(packToAny(FetchSubscriptionsRequest.getDefaultInstance()));
        doThrow(new UStatusException(STATUS_UNKNOWN)).when(mSubscriptionHandler).fetchSubscriptions(requestMessage);
        mUSubscription.inject(requestMessage);
        verify(mSubscriptionHandler, timeout(DELAY_MS).times(1)).fetchSubscriptions(requestMessage);
        verifyResponseSent(requestMessage, UCode.UNKNOWN);
    }

    @Test
    public void testHandleFetchSubscribersRequestMessage() {
        final UMessage requestMessage = UMessageBuilder.request(CLIENT_URI, METHOD_FETCH_SUBSCRIBERS, TTL)
                .build(packToAny(FetchSubscribersRequest.getDefaultInstance()));
        doReturn(FetchSubscribersResponse.getDefaultInstance()).when(mSubscriptionHandler).fetchSubscribers(requestMessage);
        mUSubscription.inject(requestMessage);
        verify(mSubscriptionHandler, timeout(DELAY_MS).times(1)).fetchSubscribers(requestMessage);
        verifyResponseSent(requestMessage, UCode.OK);
    }

    @Test
    public void testHandleFetchSubscribersRequestFailure() {
        final UMessage requestMessage = UMessageBuilder.request(CLIENT_URI, METHOD_FETCH_SUBSCRIBERS, TTL)
                .build(packToAny(FetchSubscribersRequest.getDefaultInstance()));
        doThrow(new UStatusException(STATUS_UNKNOWN)).when(mSubscriptionHandler).fetchSubscribers(requestMessage);
        mUSubscription.inject(requestMessage);
        verify(mSubscriptionHandler, timeout(DELAY_MS).times(1)).fetchSubscribers(requestMessage);
        verifyResponseSent(requestMessage,UCode.UNKNOWN);
    }

    @Test
    public void testSendSubscriptionUpdate() {
        final Update update = Update.newBuilder()
                .setTopic(RESOURCE_URI)
                .setSubscriber(SubscriberInfo.newBuilder().setUri(CLIENT_URI))
                .setStatus(SubscriptionStatus.newBuilder().setState(State.SUBSCRIBED))
                .build();
        mUSubscription.sendSubscriptionUpdate(CLIENT_URI, update);
        verify(mUBus, times(1)).send(argThat(message -> {
            final UAttributes attributes = message.getAttributes();
            return attributes.getSink().equals(CLIENT_URI) &&
                   attributes.getSource().equals(USubscription.TOPIC_SUBSCRIPTION_UPDATE) &&
                   attributes.getType().equals(UMessageType.UMESSAGE_TYPE_NOTIFICATION) &&
                   update.equals(unpack(message, Update.class).orElse(null));
        }), any());
    }

    @Test
    public void testNotifySubscriptionChanged() {
        final Update update = Update.newBuilder()
                .setTopic(RESOURCE_URI)
                .setSubscriber(SubscriberInfo.newBuilder().setUri(CLIENT_URI))
                .setStatus(SubscriptionStatus.newBuilder().setState(State.SUBSCRIBED))
                .build();
        mUSubscription.registerListener(mSubscriptionListener);
        mUSubscription.notifySubscriptionChanged(update);
        mUSubscription.unregisterListener(mSubscriptionListener);
        verify(mSubscriptionListener, times(1)).onSubscriptionChanged(update);
    }

    @Test
    public void testGetSubscriptions() {
        final Set<SubscriptionData> subscriptions = Set.of(
                new SubscriptionData(RESOURCE_URI, CLIENT_URI),
                new SubscriptionData(RESOURCE_URI, CLIENT2_URI)
        );
        doReturn(subscriptions).when(mSubscriptionHandler).getSubscriptions(RESOURCE_URI);
        assertEquals(subscriptions, mUSubscription.getSubscriptions(RESOURCE_URI));
    }

    @Test
    public void testGetSubscriptionsWithExpiryTime() {
        final Set<SubscriptionData> subscriptions = Set.of(
                new SubscriptionData(RESOURCE_URI, CLIENT_URI, EXPIRY_TIME, 0),
                new SubscriptionData(RESOURCE2_URI, CLIENT_URI, EXPIRY_TIME, 0));
        doReturn(subscriptions).when(mSubscriptionHandler).getSubscriptionsWithExpiryTime();
        assertEquals(subscriptions, mUSubscription.getSubscriptionsWithExpiryTime());
    }

    @Test
    public void testUnsubscribe() {
        mUSubscription.unsubscribe(RESOURCE_URI, CLIENT_URI);
        verify(mSubscriptionHandler, times(1)).unsubscribe(RESOURCE_URI, CLIENT_URI);
    }

    @Test
    public void testGetCallerCredentials() {
        final Credentials credentials = new Credentials(PACKAGE_NAME, 0, 0, CLIENT_URI);
        doReturn(credentials).when(mUBus).getCallerCredentials(ID);
        assertEquals(credentials, mUSubscription.getCallerCredentials(ID));
    }
}
