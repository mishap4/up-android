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

import static com.google.protobuf.util.Timestamps.fromMillis;
import static com.google.protobuf.util.Timestamps.toMillis;

import static org.eclipse.uprotocol.communication.UPayload.pack;
import static org.eclipse.uprotocol.communication.UPayload.packToAny;
import static org.eclipse.uprotocol.core.usubscription.USubscription.METHOD_FETCH_SUBSCRIBERS;
import static org.eclipse.uprotocol.core.usubscription.USubscription.METHOD_FETCH_SUBSCRIPTIONS;
import static org.eclipse.uprotocol.core.usubscription.USubscription.METHOD_REGISTER_FOR_NOTIFICATIONS;
import static org.eclipse.uprotocol.core.usubscription.USubscription.METHOD_SUBSCRIBE;
import static org.eclipse.uprotocol.core.usubscription.USubscription.METHOD_UNREGISTER_FOR_NOTIFICATIONS;
import static org.eclipse.uprotocol.core.usubscription.USubscription.METHOD_UNSUBSCRIBE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.core.ubus.client.Credentials;
import org.eclipse.uprotocol.core.usubscription.database.DatabaseHelper;
import org.eclipse.uprotocol.core.usubscription.database.SubscriberRecord;
import org.eclipse.uprotocol.core.usubscription.v3.FetchSubscribersRequest;
import org.eclipse.uprotocol.core.usubscription.v3.FetchSubscribersResponse;
import org.eclipse.uprotocol.core.usubscription.v3.FetchSubscriptionsRequest;
import org.eclipse.uprotocol.core.usubscription.v3.FetchSubscriptionsResponse;
import org.eclipse.uprotocol.core.usubscription.v3.NotificationsRequest;
import org.eclipse.uprotocol.core.usubscription.v3.NotificationsResponse;
import org.eclipse.uprotocol.core.usubscription.v3.SubscribeAttributes;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriberInfo;
import org.eclipse.uprotocol.core.usubscription.v3.Subscription;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionRequest;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionResponse;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus.State;
import org.eclipse.uprotocol.core.usubscription.v3.UnsubscribeRequest;
import org.eclipse.uprotocol.core.usubscription.v3.UnsubscribeResponse;
import org.eclipse.uprotocol.transport.builder.UMessageBuilder;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UUri;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.stubbing.Answer;
import org.robolectric.RuntimeEnvironment;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class SubscriptionHandlerTest extends TestBase {
    private static final Credentials CLIENT_CREDENTIALS = new Credentials(PACKAGE_NAME, 0, 0, CLIENT_URI);

    private final ScheduledExecutorService mExecutor = mock(ScheduledExecutorService.class);
    private Context mContext;
    private USubscription mUSubscription;
    private DatabaseHelper mDatabaseHelper;
    private SubscriptionHandler mSubscriptionHandler;

    private static void setLogLevel(int level) {
        USubscription.DEBUG = (level <= Log.DEBUG);
        USubscription.VERBOSE = (level <= Log.VERBOSE);
    }

    @Before
    public void setUp() {
        setLogLevel(Log.VERBOSE);
        mContext = RuntimeEnvironment.getApplication();
        mUSubscription = mock(USubscription.class);
        mDatabaseHelper = mock(DatabaseHelper.class);
        mSubscriptionHandler = new SubscriptionHandler(mContext, mDatabaseHelper);
        doReturn(CLIENT_CREDENTIALS).when(mUSubscription).getCallerCredentials(any());
        doReturn(1L).when(mDatabaseHelper).addSubscription(any());
        doReturn(1L).when(mDatabaseHelper).addSubscriber(any());
        doReturn(1).when(mDatabaseHelper).updateSubscriber(any());
        doReturn(1).when(mDatabaseHelper).deleteSubscription(any());
        doReturn(1).when(mDatabaseHelper).deleteSubscriber(any(), any());
        prepareExecuteOnSameThread();
        mSubscriptionHandler.init(mUSubscription);
    }

    @SuppressWarnings({"ResultOfMethodCallIgnored","unused"})
    private void prepareExecuteOnOtherThread(CountDownLatch latch) {
        final Executor executor = Executors.newSingleThreadExecutor();
        doAnswer((Answer<Object>) it -> {
            final Runnable task = it.getArgument(0);
            executor.execute(task);
            return null;
        }).when(mExecutor).execute(any(Runnable.class));

        doAnswer((Answer<Object>) it -> {
            final Runnable task = it.getArgument(0);
            executor.execute(() -> {
                try {
                    latch.await(DELAY_LONG_MS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                task.run();
            });
            return mock(ScheduledFuture.class);
        }).when(mExecutor).schedule(any(Runnable.class), anyLong(), any());
    }

    private void prepareExecuteOnSameThread() {
        doAnswer((Answer<Object>) it -> {
            ((Runnable) it.getArgument(0)).run();
            return null;
        }).when(mExecutor).execute(any(Runnable.class));

        doAnswer((Answer<Object>) it -> {
            ((Runnable) it.getArgument(0)).run();
            return null;
        }).when(mExecutor).schedule(any(Runnable.class), anyLong(), any());
    }

    private static UMessage buildSubscriptionRequestMessage(@NonNull UUri topic, @NonNull UUri subscriber, int expiry, int period) {
        final SubscriptionRequest request = SubscriptionRequest.newBuilder()
                .setTopic(topic)
                .setSubscriber(SubscriberInfo.newBuilder().setUri(subscriber))
                .setAttributes(SubscribeAttributes.newBuilder()
                        .setExpire(fromMillis(expiry))
                        .setSamplePeriodMs(period))
                .build();
        return UMessageBuilder.request(CLIENT_URI, METHOD_SUBSCRIBE, TTL).build(pack(request));
    }

    private static UMessage buildUnsubscribeRequestMessage(@NonNull UUri topic, @NonNull UUri subscriber) {
        final UnsubscribeRequest request = UnsubscribeRequest.newBuilder()
                .setTopic(topic)
                .setSubscriber(SubscriberInfo.newBuilder().setUri(subscriber))
                .build();
        return UMessageBuilder.request(CLIENT_URI, METHOD_SUBSCRIBE, TTL).build(pack(request));
    }

    @Test
    public void testInit() {
        verify(mDatabaseHelper, times(1)).init(mContext);
    }

    @Test
    public void testStartup() {
        reset(mDatabaseHelper);
        mSubscriptionHandler.startup();
        verifyNoMoreInteractions(mDatabaseHelper);
    }

    @Test
    public void testShutdown() {
        mSubscriptionHandler.shutdown();
        verify(mDatabaseHelper, times(1)).shutdown();
    }

    @Test
    public void testShouldNotify() {
        assertFalse(SubscriptionHandler.shouldNotify(
                new SubscriberRecord(RESOURCE_URI, CLIENT_URI),
                new SubscriberRecord(RESOURCE_URI, CLIENT_URI)));
        assertTrue(SubscriptionHandler.shouldNotify(
                new SubscriberRecord(RESOURCE_URI, CLIENT_URI, 0, 0),
                new SubscriberRecord(RESOURCE_URI, CLIENT_URI, EXPIRY_TIME, 0)));
        assertTrue(SubscriptionHandler.shouldNotify(
                new SubscriberRecord(RESOURCE_URI, CLIENT_URI, 0, 0),
                new SubscriberRecord(RESOURCE_URI, CLIENT_URI, 0, PERIOD)));
    }

    @Test
    public void testSubscribe() {
        doReturn(State.UNSUBSCRIBED).doReturn(State.SUBSCRIBED)
                .when(mDatabaseHelper).getSubscriptionState(RESOURCE_URI);
        doReturn(true).when(mDatabaseHelper).isObserved(RESOURCE_URI);
        final UMessage requestMessage = buildSubscriptionRequestMessage(RESOURCE_URI, CLIENT_URI, EXPIRY_TIME, PERIOD);
        final SubscriptionResponse response = mSubscriptionHandler.subscribe(requestMessage);
        assertEquals(State.SUBSCRIBED, response.getStatus().getState());
        assertEquals(RESOURCE_URI, response.getTopic());
        verify(mDatabaseHelper, times(0)).updateSubscriber(any());
        verify(mDatabaseHelper, times(1)).addSubscription(any());
        verify(mDatabaseHelper, times(1)).addSubscriber(any());
        verify(mUSubscription, times(1)).sendSubscriptionUpdate(eq(SERVICE_URI), any());
        verify(mUSubscription, times(1)).notifySubscriptionChanged(argThat(update -> {
            assertEquals(RESOURCE_URI, update.getTopic());
            assertEquals(CLIENT_URI, update.getSubscriber().getUri());
            assertEquals(EXPIRY_TIME, toMillis(update.getAttributes().getExpire()));
            assertEquals(PERIOD, update.getAttributes().getSamplePeriodMs());
            assertEquals(State.SUBSCRIBED, update.getStatus().getState());
            return true;
        }));
    }

    @Test
    public void testSubscribeFromRemote() {
        setLogLevel(Log.INFO);
        doReturn(State.UNSUBSCRIBED).doReturn(State.SUBSCRIBED)
                .when(mDatabaseHelper).getSubscriptionState(RESOURCE_URI);
        final UMessage requestMessage = buildSubscriptionRequestMessage(RESOURCE_URI, CLIENT_URI_REMOTE, EXPIRY_TIME, PERIOD);
        final SubscriptionResponse response = mSubscriptionHandler.subscribe(requestMessage);
        assertEquals(State.SUBSCRIBED, response.getStatus().getState());
        assertEquals(RESOURCE_URI, response.getTopic());
        verify(mDatabaseHelper, times(0)).updateSubscriber(any());
        verify(mDatabaseHelper, times(1)).addSubscription(any());
        verify(mDatabaseHelper, times(1)).addSubscriber(any());
        verify(mUSubscription, times(0)).sendSubscriptionUpdate(any(), any());
        verify(mUSubscription, times(1)).notifySubscriptionChanged(argThat(update -> {
            assertEquals(RESOURCE_URI, update.getTopic());
            assertEquals(CLIENT_URI_REMOTE, update.getSubscriber().getUri());
            assertEquals(EXPIRY_TIME, toMillis(update.getAttributes().getExpire()));
            assertEquals(PERIOD, update.getAttributes().getSamplePeriodMs());
            assertEquals(State.SUBSCRIBED, update.getStatus().getState());
            return true;
        }));
    }

    @Test
    public void testSubscribeUpdate() {
        doReturn(State.SUBSCRIBED).when(mDatabaseHelper).getSubscriptionState(RESOURCE_URI);
        doReturn(new SubscriberRecord(RESOURCE_URI, CLIENT_URI))
                .when(mDatabaseHelper).getSubscriber(RESOURCE_URI, CLIENT_URI);
        final UMessage requestMessage = buildSubscriptionRequestMessage(RESOURCE_URI, CLIENT_URI, EXPIRY_TIME, PERIOD);
        final SubscriptionResponse response = mSubscriptionHandler.subscribe(requestMessage);
        assertEquals(State.SUBSCRIBED, response.getStatus().getState());
        assertEquals(RESOURCE_URI, response.getTopic());
        verify(mDatabaseHelper, times(1)).updateSubscriber(any());
        verify(mDatabaseHelper, times(0)).addSubscriber(any());
        verify(mUSubscription, times(0)).sendSubscriptionUpdate(any(), any());
        verify(mUSubscription, times(1)).notifySubscriptionChanged(argThat(update -> {
            assertEquals(RESOURCE_URI, update.getTopic());
            assertEquals(CLIENT_URI, update.getSubscriber().getUri());
            assertEquals(EXPIRY_TIME, toMillis(update.getAttributes().getExpire()));
            assertEquals(PERIOD, update.getAttributes().getSamplePeriodMs());
            assertEquals(State.SUBSCRIBED, update.getStatus().getState());
            return true;
        }));
    }

    @Test
    public void testSubscribeDuplicate() {
        setLogLevel(Log.INFO);
        doReturn(State.SUBSCRIBED).when(mDatabaseHelper).getSubscriptionState(RESOURCE_URI);
        doReturn(new SubscriberRecord(RESOURCE_URI, CLIENT_URI))
                .when(mDatabaseHelper).getSubscriber(RESOURCE_URI, CLIENT_URI);
        doReturn(1L).when(mDatabaseHelper).addSubscriber(any());
        final UMessage requestMessage = buildSubscriptionRequestMessage(RESOURCE_URI, CLIENT_URI, 0, 0);
        final SubscriptionResponse response = mSubscriptionHandler.subscribe(requestMessage);
        assertEquals(State.SUBSCRIBED, response.getStatus().getState());
        assertEquals(RESOURCE_URI, response.getTopic());
        verify(mDatabaseHelper, times(1)).updateSubscriber(any());
        verify(mDatabaseHelper, times(0)).addSubscriber(any());
        verify(mUSubscription, times(0)).sendSubscriptionUpdate(any(), any());
        verify(mUSubscription, times(0)).notifySubscriptionChanged(any());
    }

    @Test
    public void testSubscribeInvalidRequest() {
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () ->
                mSubscriptionHandler.subscribe(UMessageBuilder.request(CLIENT_URI, METHOD_SUBSCRIBE, TTL).build()));
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () ->
                mSubscriptionHandler.subscribe(buildSubscriptionRequestMessage(EMPTY_URI, CLIENT_URI, 0, 0)));
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () ->
                mSubscriptionHandler.subscribe(buildSubscriptionRequestMessage(RESOURCE_URI, EMPTY_URI, 0, 0)));
    }

    @Test
    public void testSubscribeAddSubscriptionFailure() {
        doReturn(-1L).when(mDatabaseHelper).addSubscription(any());
        assertThrowsStatusException(UCode.INTERNAL, () ->
                mSubscriptionHandler.subscribe(buildSubscriptionRequestMessage(RESOURCE_URI, CLIENT_URI, 0, 0)));
    }

    @Test
    public void testSubscribeAddSubscriberFailure() {
        doReturn(-1L).when(mDatabaseHelper).addSubscriber(any());
        assertThrowsStatusException(UCode.INTERNAL, () ->
                mSubscriptionHandler.subscribe(buildSubscriptionRequestMessage(RESOURCE_URI, CLIENT_URI, 0, 0)));
    }

    @Test
    public void testSubscribeUpdateSubscriberFailure() {
        doReturn(State.SUBSCRIBED).when(mDatabaseHelper).getSubscriptionState(RESOURCE_URI);
        doReturn(new SubscriberRecord(RESOURCE_URI, CLIENT_URI)).when(mDatabaseHelper).getSubscriber(RESOURCE_URI, CLIENT_URI);
        doReturn(-1).when(mDatabaseHelper).updateSubscriber(any());
        assertThrowsStatusException(UCode.INTERNAL, () ->
                mSubscriptionHandler.subscribe(buildSubscriptionRequestMessage(RESOURCE_URI, CLIENT_URI, 0, 0)));
    }

    @Test
    public void testSubscribeRemoteUnimplemented() {
        assertThrowsStatusException(UCode.UNIMPLEMENTED, () ->
                mSubscriptionHandler.subscribe(buildSubscriptionRequestMessage(RESOURCE_URI_REMOTE, CLIENT_URI, 0, 0)));
    }

    @Test
    public void testUnsubscribe() {
        doReturn(State.SUBSCRIBED).when(mDatabaseHelper).getSubscriptionState(RESOURCE_URI);
        doReturn(new SubscriberRecord(RESOURCE_URI, CLIENT_URI, EXPIRY_TIME, PERIOD))
                .when(mDatabaseHelper).getSubscriber(RESOURCE_URI, CLIENT_URI);
        doReturn(0).when(mDatabaseHelper).getSubscribersCount(RESOURCE_URI);
        doReturn(true).when(mDatabaseHelper).isObserved(RESOURCE_URI);
        final UMessage requestMessage = buildUnsubscribeRequestMessage(RESOURCE_URI, CLIENT_URI);
        final UnsubscribeResponse response = mSubscriptionHandler.unsubscribe(requestMessage);
        assertNotNull(response);
        verify(mDatabaseHelper, times(1)).deleteSubscriber(RESOURCE_URI, CLIENT_URI);
        verify(mDatabaseHelper, times(1)).deleteSubscription(RESOURCE_URI);
        verify(mUSubscription, times(1)).sendSubscriptionUpdate(eq(CLIENT_URI), any());
        verify(mUSubscription, times(1)).sendSubscriptionUpdate(eq(SERVICE_URI), any());
        verify(mUSubscription, times(1)).notifySubscriptionChanged(argThat(update -> {
            assertEquals(RESOURCE_URI, update.getTopic());
            assertEquals(CLIENT_URI, update.getSubscriber().getUri());
            assertEquals(EXPIRY_TIME, toMillis(update.getAttributes().getExpire()));
            assertEquals(PERIOD, update.getAttributes().getSamplePeriodMs());
            assertEquals(State.UNSUBSCRIBED, update.getStatus().getState());
            return true;
        }));
    }

    @Test
    public void testUnsubscribeFromRemote() {
        setLogLevel(Log.INFO);
        doReturn(State.SUBSCRIBED).when(mDatabaseHelper).getSubscriptionState(RESOURCE_URI);
        doReturn(new SubscriberRecord(RESOURCE_URI, CLIENT_URI_REMOTE, EXPIRY_TIME, PERIOD))
                .when(mDatabaseHelper).getSubscriber(RESOURCE_URI, CLIENT_URI_REMOTE);
        doReturn(0).when(mDatabaseHelper).getSubscribersCount(RESOURCE_URI);
        final UMessage requestMessage = buildUnsubscribeRequestMessage(RESOURCE_URI, CLIENT_URI_REMOTE);
        final UnsubscribeResponse response = mSubscriptionHandler.unsubscribe(requestMessage);
        assertNotNull(response);
        verify(mDatabaseHelper, times(1)).deleteSubscriber(RESOURCE_URI, CLIENT_URI_REMOTE);
        verify(mDatabaseHelper, times(1)).deleteSubscription(RESOURCE_URI);
        verify(mUSubscription, times(1)).sendSubscriptionUpdate(eq(CLIENT_URI_REMOTE), any());
        verify(mUSubscription, times(1)).notifySubscriptionChanged(argThat(update -> {
            assertEquals(RESOURCE_URI, update.getTopic());
            assertEquals(CLIENT_URI_REMOTE, update.getSubscriber().getUri());
            assertEquals(EXPIRY_TIME, toMillis(update.getAttributes().getExpire()));
            assertEquals(PERIOD, update.getAttributes().getSamplePeriodMs());
            assertEquals(State.UNSUBSCRIBED, update.getStatus().getState());
            return true;
        }));
    }

    @Test
    public void testUnsubscribeNotLastSubscriber() {
        doReturn(State.SUBSCRIBED).when(mDatabaseHelper).getSubscriptionState(RESOURCE_URI);
        doReturn(new SubscriberRecord(RESOURCE_URI, CLIENT_URI, EXPIRY_TIME, PERIOD))
                .when(mDatabaseHelper).getSubscriber(RESOURCE_URI, CLIENT_URI);
        doReturn(1).when(mDatabaseHelper).getSubscribersCount(RESOURCE_URI);
        final UMessage requestMessage = buildUnsubscribeRequestMessage(RESOURCE_URI, CLIENT_URI);
        final UnsubscribeResponse response = mSubscriptionHandler.unsubscribe(requestMessage);
        assertNotNull(response);
        verify(mDatabaseHelper, times(1)).deleteSubscriber(RESOURCE_URI, CLIENT_URI);
        verify(mDatabaseHelper, times(0)).deleteSubscription(RESOURCE_URI);
        verify(mUSubscription, times(1)).sendSubscriptionUpdate(eq(CLIENT_URI), any());
        verify(mUSubscription, times(1)).notifySubscriptionChanged(argThat(update -> {
            assertEquals(RESOURCE_URI, update.getTopic());
            assertEquals(CLIENT_URI, update.getSubscriber().getUri());
            assertEquals(EXPIRY_TIME, toMillis(update.getAttributes().getExpire()));
            assertEquals(PERIOD, update.getAttributes().getSamplePeriodMs());
            assertEquals(State.UNSUBSCRIBED, update.getStatus().getState());
            return true;
        }));
    }

    @Test
    public void testUnsubscribeSubscriptionNotFound() {
        doReturn(State.UNSUBSCRIBED).when(mDatabaseHelper).getSubscriptionState(RESOURCE_URI);
        final UMessage requestMessage = buildUnsubscribeRequestMessage(RESOURCE_URI, CLIENT_URI);
        final UnsubscribeResponse response = mSubscriptionHandler.unsubscribe(requestMessage);
        assertNotNull(response);
    }

    @Test
    public void testUnsubscribeSubscriberNotFound() {
        doReturn(State.SUBSCRIBED).when(mDatabaseHelper).getSubscriptionState(RESOURCE2_URI);
        final UMessage requestMessage = buildUnsubscribeRequestMessage(RESOURCE2_URI, CLIENT_URI);
        final UnsubscribeResponse response = mSubscriptionHandler.unsubscribe(requestMessage);
        assertNotNull(response);
    }

    @Test
    public void testUnsubscribeInvalidRequest() {
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () ->
                mSubscriptionHandler.unsubscribe(UMessageBuilder.request(CLIENT_URI, METHOD_UNSUBSCRIBE, TTL).build()));
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () ->
                mSubscriptionHandler.unsubscribe(buildUnsubscribeRequestMessage(EMPTY_URI, CLIENT_URI)));
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () ->
                mSubscriptionHandler.unsubscribe(buildUnsubscribeRequestMessage(RESOURCE_URI, EMPTY_URI)));
    }

    @Test
    public void testUnsubscribeDeleteSubscriberFailure() {
        doReturn(State.SUBSCRIBED).when(mDatabaseHelper).getSubscriptionState(RESOURCE_URI);
        doReturn(new SubscriberRecord(RESOURCE_URI, CLIENT_URI))
                .when(mDatabaseHelper).getSubscriber(RESOURCE_URI, CLIENT_URI);
        doReturn(0).when(mDatabaseHelper).getSubscribersCount(RESOURCE_URI);
        doReturn(-1).when(mDatabaseHelper).deleteSubscriber(RESOURCE_URI, CLIENT_URI);
        assertThrowsStatusException(UCode.INTERNAL, () ->
                mSubscriptionHandler.unsubscribe(buildUnsubscribeRequestMessage(RESOURCE_URI, CLIENT_URI)));
    }

    @Test
    public void testUnsubscribeDeleteSubscriptionFailure() {
        doReturn(State.SUBSCRIBED).when(mDatabaseHelper).getSubscriptionState(RESOURCE_URI);
        doReturn(new SubscriberRecord(RESOURCE_URI, CLIENT_URI)).when(mDatabaseHelper).getSubscriber(RESOURCE_URI, CLIENT_URI);
        doReturn(0).when(mDatabaseHelper).getSubscribersCount(RESOURCE_URI);
        doReturn(-1).when(mDatabaseHelper).deleteSubscription(RESOURCE_URI);
        assertThrowsStatusException(UCode.INTERNAL, () ->
                mSubscriptionHandler.unsubscribe(buildUnsubscribeRequestMessage(RESOURCE_URI, CLIENT_URI)));
    }

    @Test
    public void testUnsubscribeInternal() {
        doReturn(State.SUBSCRIBED).when(mDatabaseHelper).getSubscriptionState(RESOURCE_URI);
        doReturn(new SubscriberRecord(RESOURCE_URI, CLIENT_URI))
                .when(mDatabaseHelper).getSubscriber(RESOURCE_URI, CLIENT_URI);
        mSubscriptionHandler.unsubscribe(RESOURCE_URI, CLIENT_URI);
        verify(mDatabaseHelper, times(1)).deleteSubscriber(RESOURCE_URI, CLIENT_URI);
        verify(mUSubscription, times(1)).sendSubscriptionUpdate(eq(CLIENT_URI), any());
        verify(mUSubscription, times(1)).notifySubscriptionChanged(argThat(update -> {
            assertEquals(RESOURCE_URI, update.getTopic());
            assertEquals(CLIENT_URI, update.getSubscriber().getUri());
            assertEquals(State.UNSUBSCRIBED, update.getStatus().getState());
            return true;
        }));
    }

    @Test
    public void testUnsubscribeInternalFailure() {
        doReturn(new SubscriberRecord(RESOURCE_URI, CLIENT_URI))
                .when(mDatabaseHelper).getSubscriber(RESOURCE_URI, CLIENT_URI);
        doReturn(-1).when(mDatabaseHelper).deleteSubscriber(RESOURCE_URI, CLIENT_URI);
        mSubscriptionHandler.unsubscribe(RESOURCE_URI, CLIENT_URI);
        verify(mUSubscription, times(0)).sendSubscriptionUpdate(any(), any());
        verify(mUSubscription, times(0)).notifySubscriptionChanged(any());
    }

    @Test
    public void testUnsubscribeAllFromPackage() {
        doReturn(List.of(new SubscriberRecord(RESOURCE_URI, CLIENT_URI)))
                .when(mDatabaseHelper).getSubscribersFromPackage(PACKAGE_NAME);
        doReturn(State.SUBSCRIBED).when(mDatabaseHelper).getSubscriptionState(RESOURCE_URI);
        doReturn(0).when(mDatabaseHelper).getSubscribersCount(RESOURCE_URI);
        mSubscriptionHandler.unsubscribeAllFromPackage(PACKAGE_NAME);
        verify(mDatabaseHelper, times(1)).deleteSubscriber(RESOURCE_URI, CLIENT_URI);
        verify(mDatabaseHelper, times(1)).deleteSubscription(RESOURCE_URI);
        verify(mUSubscription, times(1)).sendSubscriptionUpdate(eq(CLIENT_URI), any());
        verify(mUSubscription, times(1)).notifySubscriptionChanged(argThat(update -> {
            assertEquals(RESOURCE_URI, update.getTopic());
            assertEquals(CLIENT_URI, update.getSubscriber().getUri());
            assertEquals(State.UNSUBSCRIBED, update.getStatus().getState());
            return true;
        }));
    }

    @Test
    public void testUnsubscribeAllFromPackageFailure() {
        doThrow(new RuntimeException()).when(mDatabaseHelper).getSubscribersFromPackage(PACKAGE_NAME);
        mSubscriptionHandler.unsubscribeAllFromPackage(PACKAGE_NAME);
        verify(mUSubscription, times(0)).sendSubscriptionUpdate(any(), any());
        verify(mUSubscription, times(0)).notifySubscriptionChanged(any());
    }

    @Test
    public void testFetchSubscriptionsByTopic() {
        doReturn(List.of(new SubscriberRecord(RESOURCE_URI, CLIENT_URI, EXPIRY_TIME, PERIOD)))
                .when(mDatabaseHelper).getSubscribersByTopic(RESOURCE_URI);
        doReturn(State.SUBSCRIBED).when(mDatabaseHelper).getSubscriptionState(RESOURCE_URI);
        final UMessage requestMessage = UMessageBuilder.request(CLIENT_URI, METHOD_FETCH_SUBSCRIPTIONS, TTL)
                .build(pack(FetchSubscriptionsRequest.newBuilder()
                        .setTopic(RESOURCE_URI)
                        .build()));
        final FetchSubscriptionsResponse response = mSubscriptionHandler.fetchSubscriptions(requestMessage);
        final Subscription subscription = response.getSubscriptions(0);
        assertEquals(RESOURCE_URI, subscription.getTopic());
        assertEquals(CLIENT_URI, subscription.getSubscriber().getUri());
        assertEquals(EXPIRY_TIME, toMillis(subscription.getAttributes().getExpire()));
        assertEquals(PERIOD, subscription.getAttributes().getSamplePeriodMs());
        assertEquals(State.SUBSCRIBED, subscription.getStatus().getState());
    }

    @Test
    public void testFetchSubscriptionsBySubscriber() {
        doReturn(List.of(new SubscriberRecord(RESOURCE_URI, CLIENT_URI, EXPIRY_TIME, PERIOD)))
                .when(mDatabaseHelper).getSubscribersByUri(CLIENT_URI);
        doReturn(State.SUBSCRIBED).when(mDatabaseHelper).getSubscriptionState(RESOURCE_URI);
        final UMessage requestMessage = UMessageBuilder.request(CLIENT_URI, METHOD_FETCH_SUBSCRIPTIONS, TTL)
                .build(pack(FetchSubscriptionsRequest.newBuilder()
                        .setSubscriber(SubscriberInfo.newBuilder().setUri(CLIENT_URI))
                        .build()));
        final FetchSubscriptionsResponse response = mSubscriptionHandler.fetchSubscriptions(requestMessage);
        final Subscription subscription = response.getSubscriptions(0);
        assertEquals(RESOURCE_URI, subscription.getTopic());
        assertEquals(CLIENT_URI, subscription.getSubscriber().getUri());
        assertEquals(EXPIRY_TIME, toMillis(subscription.getAttributes().getExpire()));
        assertEquals(PERIOD, subscription.getAttributes().getSamplePeriodMs());
        assertEquals(State.SUBSCRIBED, subscription.getStatus().getState());
    }

    @Test
    public void testFetchSubscriptionsInvalidRequest() {
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () ->
                mSubscriptionHandler.fetchSubscriptions(
                        UMessageBuilder.request(CLIENT_URI, METHOD_FETCH_SUBSCRIPTIONS, TTL).build()));
    }

    @Test
    public void testFetchSubscriptionsUnknownRequestCase() {
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () ->
                mSubscriptionHandler.fetchSubscriptions(
                        UMessageBuilder.request(CLIENT_URI, METHOD_FETCH_SUBSCRIPTIONS, TTL)
                                .build(packToAny(FetchSubscriptionsRequest.getDefaultInstance()))));
    }

    @Test
    public void testFetchSubscribers() {
        doReturn(List.of(new SubscriberRecord(RESOURCE_URI, CLIENT_URI, EXPIRY_TIME, PERIOD)))
                .when(mDatabaseHelper).getSubscribersByTopic(RESOURCE_URI);
        final UMessage requestMessage = UMessageBuilder.request(CLIENT_URI, METHOD_FETCH_SUBSCRIPTIONS, TTL)
                .build(pack(FetchSubscribersRequest.newBuilder()
                        .setTopic(RESOURCE_URI)
                        .build()));
        final FetchSubscribersResponse response = mSubscriptionHandler.fetchSubscribers(requestMessage);
        final SubscriberInfo subscriber = response.getSubscribers(0);
        assertEquals(CLIENT_URI, subscriber.getUri());
    }

    @Test
    public void testFetchSubscribersInvalidRequest() {
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () ->
                mSubscriptionHandler.fetchSubscribers(
                        UMessageBuilder.request(CLIENT_URI, METHOD_FETCH_SUBSCRIBERS, TTL).build()));
    }

    @Test
    public void testRegisterForNotifications() {
        doReturn(1L).when(mDatabaseHelper).addObserver(RESOURCE_URI);
        final UMessage requestMessage = UMessageBuilder.request(SERVICE_URI, METHOD_REGISTER_FOR_NOTIFICATIONS, TTL)
                .build(pack(NotificationsRequest.newBuilder()
                        .setTopic(RESOURCE_URI)
                        .build()));
        final NotificationsResponse response = mSubscriptionHandler.registerForNotifications(requestMessage);
        assertNotNull(response);
    }

    @Test
    public void testRegisterForNotificationsDuplicate() {
        setLogLevel(Log.INFO);
        testRegisterForNotifications();
        testRegisterForNotifications();
    }

    @Test
    public void testRegisterForNotificationsInvalidRequest() {
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () ->
                mSubscriptionHandler.registerForNotifications(
                        UMessageBuilder.request(SERVICE_URI, METHOD_REGISTER_FOR_NOTIFICATIONS, TTL).build()));
    }

    @Test
    public void testRegisterForNotificationsAddObserverFailure() {
        doReturn(-1L).when(mDatabaseHelper).addObserver(RESOURCE_URI);
        final UMessage requestMessage = UMessageBuilder.request(SERVICE_URI, METHOD_REGISTER_FOR_NOTIFICATIONS, TTL)
                .build(pack(NotificationsRequest.newBuilder()
                        .setTopic(RESOURCE_URI)
                        .build()));
        assertThrowsStatusException(UCode.INTERNAL, () -> mSubscriptionHandler.registerForNotifications(requestMessage));
    }

    @Test
    public void testUnregisterForNotifications() {
        doReturn(1).when(mDatabaseHelper).deleteObserver(RESOURCE_URI);
        final UMessage requestMessage = UMessageBuilder.request(SERVICE_URI, METHOD_UNREGISTER_FOR_NOTIFICATIONS, TTL)
                .build(pack(NotificationsRequest.newBuilder()
                        .setTopic(RESOURCE_URI)
                        .build()));
        final NotificationsResponse response = mSubscriptionHandler.unregisterForNotifications(requestMessage);
        assertNotNull(response);
    }

    @Test
    public void testUnregisterForNotificationsDuplicate() {
        setLogLevel(Log.INFO);
        testUnregisterForNotifications();
        testUnregisterForNotifications();
    }

    @Test
    public void testUnregisterForNotificationsInvalidRequest() {
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () ->
                mSubscriptionHandler.unregisterForNotifications(
                        UMessageBuilder.request(SERVICE_URI, METHOD_UNREGISTER_FOR_NOTIFICATIONS, TTL).build()));
    }

    @Test
    public void testUnregisterForNotificationsDeleteObserverFailure() {
        doReturn(-1).when(mDatabaseHelper).deleteObserver(RESOURCE_URI);
        final UMessage requestMessage = UMessageBuilder.request(SERVICE_URI, METHOD_UNREGISTER_FOR_NOTIFICATIONS, TTL)
                .build(pack(NotificationsRequest.newBuilder()
                        .setTopic(RESOURCE_URI)
                        .build()));
        assertThrowsStatusException(UCode.INTERNAL, () -> mSubscriptionHandler.unregisterForNotifications(requestMessage));
    }

    @Test
    public void testIsTopicSubscribed() {
        doReturn(State.SUBSCRIBED).when(mDatabaseHelper).getSubscriptionState(RESOURCE_URI);
        assertTrue(mSubscriptionHandler.isTopicSubscribed(RESOURCE_URI));
        doReturn(State.SUBSCRIBE_PENDING).when(mDatabaseHelper).getSubscriptionState(RESOURCE_URI);
        assertTrue(mSubscriptionHandler.isTopicSubscribed(RESOURCE_URI));
        doReturn(State.UNSUBSCRIBED).when(mDatabaseHelper).getSubscriptionState(RESOURCE_URI);
        assertFalse(mSubscriptionHandler.isTopicSubscribed(RESOURCE_URI));
    }

    @Test
    public void testGetSubscriptionState() {
        doReturn(State.SUBSCRIBED).when(mDatabaseHelper).getSubscriptionState(RESOURCE_URI);
        assertEquals(State.SUBSCRIBED, mSubscriptionHandler.getSubscriptionState(RESOURCE_URI));
    }

    @Test
    public void testGetSubscriptionStateFailure() {
        doThrow(new RuntimeException()).when(mDatabaseHelper).getSubscriptionState(RESOURCE_URI);
        assertEquals(State.UNSUBSCRIBED, mSubscriptionHandler.getSubscriptionState(RESOURCE_URI));
    }

    @Test
    public void testGetSubscriptions() {
        doReturn(List.of(new SubscriberRecord(RESOURCE_URI, CLIENT_URI, EXPIRY_TIME, PERIOD)))
                .when(mDatabaseHelper).getSubscribersByTopic(RESOURCE_URI);
        final Set<SubscriptionData> subscriptions = mSubscriptionHandler.getSubscriptions(RESOURCE_URI);
        assertEquals(1, subscriptions.size());
        final SubscriptionData subscription = subscriptions.iterator().next();
        assertEquals(RESOURCE_URI, subscription.topic());
        assertEquals(CLIENT_URI, subscription.subscriber());
        assertEquals(EXPIRY_TIME, subscription.expiryTime());
        assertEquals(PERIOD, subscription.samplingPeriod());
    }

    @Test
    public void testGetSubscriptionsFailure() {
        doThrow(new RuntimeException()).when(mDatabaseHelper).getSubscribersByTopic(RESOURCE_URI);
        assertTrue(mSubscriptionHandler.getSubscriptions(RESOURCE_URI).isEmpty());
    }

    @Test
    public void testGetSubscriptionsWithExpiryTime() {
        doReturn(List.of(new SubscriberRecord(RESOURCE_URI, CLIENT_URI, EXPIRY_TIME, PERIOD)))
                .when(mDatabaseHelper).getSubscribersWithExpiryTime();
        final Set<SubscriptionData> subscriptions = mSubscriptionHandler.getSubscriptionsWithExpiryTime();
        assertEquals(1, subscriptions.size());
        final SubscriptionData subscription = subscriptions.iterator().next();
        assertEquals(RESOURCE_URI, subscription.topic());
        assertEquals(CLIENT_URI, subscription.subscriber());
        assertEquals(EXPIRY_TIME, subscription.expiryTime());
        assertEquals(PERIOD, subscription.samplingPeriod());
    }

    @Test
    public void testGetSubscriptionsWithExpiryTimeFailure() {
        doThrow(new RuntimeException()).when(mDatabaseHelper).getSubscribersWithExpiryTime();
        assertTrue(mSubscriptionHandler.getSubscriptionsWithExpiryTime().isEmpty());
    }

    @Test
    public void testGetSubscribedPackages() {
        List<String> packages = List.of(PACKAGE_NAME, PACKAGE2_NAME);
        doReturn(packages).when(mDatabaseHelper).getSubscribedPackages();
        assertEquals(packages, mSubscriptionHandler.getSubscribedPackages());
    }

    @Test
    public void testGetSubscribedPackagesFailure() {
        doThrow(new RuntimeException()).when(mDatabaseHelper).getSubscribedPackages();
        assertTrue(mSubscriptionHandler.getSubscribedPackages().isEmpty());
    }
}
