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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.protobuf.Timestamp;

import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.core.usubscription.v3.SubscribeAttributes;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriberInfo;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus.State;
import org.eclipse.uprotocol.core.usubscription.v3.Update;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.stubbing.Answer;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class ExpiryMonitorTest extends TestBase {
    private static final long EXPIRY_TIMEOUT_MS = 500;

    private Context mContext;
    private ScheduledExecutorService mExecutor;
    private USubscription mUSubscription;
    private ExpiryMonitor mExpiryMonitor;
    private SubscriptionData mSubscription;
    private SubscriptionData mSubscription2;

    @Before
    public void setUp() {
        setLogLevel(Log.DEBUG);
        mContext = mock(Context.class);
        mUSubscription = mock(USubscription.class);
        mExecutor = spy(Executors.newSingleThreadScheduledExecutor());

        doNothing().when(mUSubscription).registerListener(any());
        doNothing().when(mUSubscription).unregisterListener(any());
        doNothing().when(mUSubscription).unsubscribe(any(), any());
        doReturn(new Intent(Intent.ACTION_TIME_CHANGED)).when(mContext).registerReceiver(any(), any(), anyInt());

        mExpiryMonitor = new ExpiryMonitor(mContext, mExecutor);
        mExpiryMonitor.init(mUSubscription);

        mSubscription = new SubscriptionData(RESOURCE_URI, CLIENT_URI, fromNow(EXPIRY_TIMEOUT_MS), 0);
        mSubscription2 = new SubscriptionData(RESOURCE2_URI, CLIENT_URI, 0, 0);
    }

    @After
    public void tearDown() {
        mExpiryMonitor.shutdown();
    }

    private ScheduledFuture<?> assertScheduled(SubscriptionData subscription, long delay) {
        final ScheduledFuture<?> future = mExpiryMonitor.getExpiryFuture(subscription);
        assertNotNull(future);
        assertFalse(future.isDone());
        assertTrue(future.getDelay(TimeUnit.MILLISECONDS) <= delay);
        return future;
    }

    private void assertNotScheduled(SubscriptionData subscription) {
        assertNull(mExpiryMonitor.getExpiryFuture(subscription));
    }

    private void prepareExecuteOnSameThread() {
        doAnswer((Answer<Object>) it -> {
            ((Runnable) it.getArgument(0)).run();
            return null;
        }).when(mExecutor).execute(any(Runnable.class));

        doAnswer((Answer<Object>) it -> {
            ((Runnable) it.getArgument(0)).run();
            return mock(ScheduledFuture.class);
        }).when(mExecutor).schedule(any(Runnable.class), anyLong(), any());
    }

    private static void setLogLevel(int level) {
        ExpiryMonitor.DEBUG = (level <= Log.DEBUG);
    }

    private static Update buildUpdate(State state, long expiryTime) {
        return Update.newBuilder()
                .setTopic(RESOURCE_URI)
                .setSubscriber(SubscriberInfo.newBuilder().setUri(CLIENT_URI))
                .setAttributes(SubscribeAttributes.newBuilder()
                        .setExpire(expiryTime > 0 ? fromMillis(expiryTime) : Timestamp.getDefaultInstance()))
                .setStatus(SubscriptionStatus.newBuilder().setState(state))
                .build();
    }

    private static long fromNow(long delay) {
        return System.currentTimeMillis() + delay;
    }

    @SuppressWarnings("SameParameterValue")
    private static long beforeNow(long delay) {
        return System.currentTimeMillis() - delay;
    }

    @Test
    public void testInit() {
        verify(mUSubscription).registerListener(any());
    }

    @Test
    public void testStartup() {
        mExpiryMonitor.startup();
        verify(mContext).registerReceiver(any(), argThat(it -> it.matchAction(Intent.ACTION_TIME_CHANGED)), anyInt());
        verify(mUSubscription, timeout(DELAY_MS).times(1)).getSubscriptionsWithExpiryTime();
    }

    @Test
    public void testShutdown() {
        mExpiryMonitor.shutdown();
        verify(mUSubscription).unregisterListener(any());
        verify(mContext).unregisterReceiver(any());
    }

    @Test
    public void testRegisterReceiverFailure() {
        doThrow(new RuntimeException()).when(mContext).registerReceiver(any(), any(), anyInt());
        mExpiryMonitor.startup();
        verify(mUSubscription, timeout(DELAY_MS).times(1)).getSubscriptionsWithExpiryTime();
    }

    @Test
    public void testUnregisterReceiverFailure() {
        doThrow(new RuntimeException()).when(mContext).unregisterReceiver(any());
        mExpiryMonitor.shutdown();
        verify(mUSubscription).unregisterListener(any());
        verify(mContext).unregisterReceiver(any());
    }

    @Test
    public void testSubscriptionExpiryScheduledAfterStartup() {
        doReturn(Set.of(mSubscription, mSubscription2)).when(mUSubscription).getSubscriptionsWithExpiryTime();
        mExpiryMonitor.startup();
        verify(mUSubscription, timeout(DELAY_MS).times(1)).getSubscriptionsWithExpiryTime();
        verify(mExecutor, timeout(DELAY_MS).times(1)).schedule(any(Runnable.class), anyLong(), any());
        assertScheduled(mSubscription, EXPIRY_TIMEOUT_MS);
        assertNotScheduled(mSubscription2);
    }

    @Test
    public void testSubscriptionExpiry() {
        testSubscriptionExpiryScheduledAfterStartup();
        final ScheduledFuture<?> future = assertScheduled(mSubscription, EXPIRY_TIMEOUT_MS);
        verify(mUSubscription, timeout(EXPIRY_TIMEOUT_MS + DELAY_MS).times(1)).unsubscribe(any(), any());
        assertTrue(future.isDone());
        assertNotScheduled(mSubscription);
    }

    @Test
    public void testSubscriptionExpiryImmediately() {
        final Update update = buildUpdate(State.SUBSCRIBED, beforeNow(EXPIRY_TIMEOUT_MS));
        mSubscription = new SubscriptionData(update);
        mExpiryMonitor.inject(update);
        verify(mUSubscription, timeout(EXPIRY_TIMEOUT_MS + DELAY_MS).times(1)).unsubscribe(any(), any());
        assertNotScheduled(mSubscription);
    }

    private void testSubscriptionExpiryRescheduled(State newState, long newExpiryTimeout) {
        testSubscriptionExpiryScheduledAfterStartup();
        final ScheduledFuture<?> future = assertScheduled(mSubscription, EXPIRY_TIMEOUT_MS);

        final Update update = buildUpdate(newState, fromNow(newExpiryTimeout));
        mSubscription = new SubscriptionData(update);
        mExpiryMonitor.inject(update);
        verify(mExecutor, timeout(DELAY_MS).times(2)).schedule(any(Runnable.class), anyLong(), any());
        final ScheduledFuture<?> newFuture = assertScheduled(mSubscription, newExpiryTimeout);
        assertTrue(future.isCancelled());

        verify(mUSubscription, timeout(newExpiryTimeout + DELAY_MS).times(1)).unsubscribe(any(), any());
        assertTrue(newFuture.isDone());
        assertNotScheduled(mSubscription);
    }

    @Test
    public void testSubscriptionExpiryRescheduledWhenResubscribed() {
        testSubscriptionExpiryRescheduled(State.SUBSCRIBED, EXPIRY_TIMEOUT_MS + DELAY_LONG_MS);
    }

    @Test
    public void testSubscriptionExpiryRescheduledWhenSubscribePending() {
        testSubscriptionExpiryRescheduled(State.SUBSCRIBE_PENDING, EXPIRY_TIMEOUT_MS);
    }

    private void testSubscriptionExpiryCancelled(State newState, long newExpiryTimeout) {
        setLogLevel(Log.INFO);
        testSubscriptionExpiryScheduledAfterStartup();
        final ScheduledFuture<?> future = assertScheduled(mSubscription, EXPIRY_TIMEOUT_MS);

        final Update update = buildUpdate(newState, newExpiryTimeout > 0 ? fromNow(newExpiryTimeout) : 0);
        mSubscription = new SubscriptionData(update);
        mExpiryMonitor.inject(update);
        sleep(DELAY_MS);
        assertNotScheduled(mSubscription);
        assertTrue(future.isCancelled());
    }

    @Test
    public void testSubscriptionExpiryCancelledWhenResubscribedWithoutExpiry() {
        testSubscriptionExpiryCancelled(State.SUBSCRIBED, 0);
    }

    @Test
    public void testSubscriptionExpiryCancelledWhenSubscribePendingWithoutExpiry() {
        testSubscriptionExpiryCancelled(State.SUBSCRIBE_PENDING, 0);
    }

    @Test
    public void testSubscriptionExpiryCancelledWhenUnsubscribed() {
        testSubscriptionExpiryCancelled(State.UNSUBSCRIBED, EXPIRY_TIMEOUT_MS);
    }

    @Test
    public void testTimeChanged() {
        testSubscriptionExpiryScheduledAfterStartup();
        final ScheduledFuture<?> future = assertScheduled(mSubscription, EXPIRY_TIMEOUT_MS);

        prepareExecuteOnSameThread();
        mExpiryMonitor.inject(new Intent(Intent.ACTION_TIME_CHANGED));
        verify(mUSubscription, timeout(DELAY_MS).times(2)).getSubscriptionsWithExpiryTime();
        assertTrue(future.isCancelled());
    }

    @Test
    public void testUnknownAction() {
        testSubscriptionExpiryScheduledAfterStartup();
        final ScheduledFuture<?> future = assertScheduled(mSubscription, EXPIRY_TIMEOUT_MS);

        prepareExecuteOnSameThread();
        mExpiryMonitor.inject(new Intent(Intent.ACTION_DATE_CHANGED));
        verify(mUSubscription, timeout(DELAY_MS).times(1)).getSubscriptionsWithExpiryTime();
        assertFalse(future.isCancelled());
    }

    @Test
    public void testCancelFuture() {
        final ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(true).when(future).cancel(anyBoolean());
        assertTrue(ExpiryMonitor.cancelFuture(future));
        doReturn(false).when(future).cancel(anyBoolean());
        assertFalse(ExpiryMonitor.cancelFuture(future));
        assertFalse(ExpiryMonitor.cancelFuture(null));
    }
}
