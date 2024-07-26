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

import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.common.util.log.Formatter.tag;
import static org.eclipse.uprotocol.common.util.log.Formatter.toPrettyDuration;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus.State;
import org.eclipse.uprotocol.core.usubscription.v3.Update;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("java:S3008")
public class ExpiryMonitor {
    protected static final String TAG = tag(USubscription.NAME, "ExpiryMonitor");
    protected static boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static final int RESCHEDULE_DELAY_MS = 5_000;

    private final Context mContext;
    private final ScheduledExecutorService mExecutor;
    private final Map<SubscriptionData, ScheduledFuture<?>> mExpiryMap = new ConcurrentHashMap<>();
    private USubscription mUSubscription;
    private ScheduledFuture<?> mTimeChangedFuture;

    private final SubscriptionListener mSubscriptionListener = update ->
            getExecutor().execute(() -> handleSubscriptionChange(update));

    private final BroadcastReceiver mTimeChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_TIME_CHANGED.equals(intent.getAction())) {
                cancelFuture(mTimeChangedFuture);
                mTimeChangedFuture = mExecutor.schedule(ExpiryMonitor.this::handleTimeChange,
                        RESCHEDULE_DELAY_MS, TimeUnit.MILLISECONDS);
            }
        }
    };

    public ExpiryMonitor(@NonNull Context context, @NonNull ScheduledExecutorService executor) {
        mContext = context;
        mExecutor = executor;
    }

    public void init(@NonNull USubscription usubscription) {
        mUSubscription = usubscription;
        mUSubscription.registerListener(mSubscriptionListener);
    }

    public void startup() {
        try {
            final IntentFilter filter = new IntentFilter(Intent.ACTION_TIME_CHANGED);
            mContext.registerReceiver(mTimeChangedReceiver, filter, Context.RECEIVER_EXPORTED);
        } catch (Exception e) {
            Log.e(TAG, join(Key.MESSAGE, "Failed to register receiver", Key.REASON, e.getMessage()));
        }
        mExecutor.execute(this::scheduleTimers);
    }

    public void shutdown() {
        mUSubscription.unregisterListener(mSubscriptionListener);
        try {
            mContext.unregisterReceiver(mTimeChangedReceiver);
        } catch (Exception e) {
            Log.e(TAG, join(Key.MESSAGE, "Failed to unregister receiver", Key.REASON, e.getMessage()));
        }
        cancelTimers();
    }

    private void scheduleTimers() {
        cancelTimers();
        mUSubscription.getSubscriptionsWithExpiryTime().forEach(this::scheduleTimer);
    }

    private void cancelTimers() {
        mExpiryMap.values().forEach(future -> future.cancel(false));
        mExpiryMap.clear();
    }

    private void scheduleTimer(@NonNull SubscriptionData subscription) {
        cancelTimer(subscription);
        if (subscription.hasExpiryTime()) {
            final long now = System.currentTimeMillis();
            if (subscription.expiryTime() > now) {
                final long timeout = subscription.expiryTime() - now;
                final ScheduledFuture<?> future = mExecutor.schedule(() -> {
                    mExpiryMap.remove(subscription);
                    handleExpiry(subscription);
                }, timeout, TimeUnit.MILLISECONDS);
                mExpiryMap.put(subscription, future);
                if (DEBUG) {
                    Log.d(TAG, join(Key.MESSAGE, "Timer scheduled", Key.SUBSCRIPTION, subscription,
                            Key.TIMEOUT, toPrettyDuration(timeout)));
                }
            } else { // Subscription has already expired
                handleExpiry(subscription);
            }
        }
    }

    private void cancelTimer(@NonNull SubscriptionData subscription) {
        if (cancelFuture(mExpiryMap.remove(subscription)) && DEBUG) {
            Log.d(TAG, join(Key.MESSAGE, "Timer cancelled", Key.SUBSCRIPTION, subscription));
        }
    }

    @VisibleForTesting
    static boolean cancelFuture(ScheduledFuture<?> future) {
        return (future != null) && future.cancel(false);
    }

    private void handleTimeChange() {
        Log.i(TAG, join(Key.EVENT, "System time changed"));
        scheduleTimers();
        mTimeChangedFuture = null;
    }

    @SuppressWarnings("java:S3398")
    private void handleSubscriptionChange(@NonNull Update update) {
        final SubscriptionData subscription = new SubscriptionData(update);
        final State state = update.getStatus().getState();
        Log.i(TAG, join(Key.EVENT, "Subscription changed", Key.SUBSCRIPTION, subscription, Key.STATE, state));
        if (subscription.hasExpiryTime() && ((state == State.SUBSCRIBED) || (state == State.SUBSCRIBE_PENDING))) {
            scheduleTimer(subscription);
        } else {
            cancelTimer(subscription);
        }
    }

    private void handleExpiry(@NonNull SubscriptionData subscription) {
        Log.i(TAG, join(Key.EVENT, "Subscription expired", Key.SUBSCRIPTION, subscription));
        mUSubscription.unsubscribe(subscription.topic(), subscription.subscriber());
    }

    private @NonNull ScheduledExecutorService getExecutor() {
        return mExecutor;
    }

    @SuppressWarnings("java:S1452")
    @VisibleForTesting
    ScheduledFuture<?> getExpiryFuture(SubscriptionData subscription) {
        return mExpiryMap.get(subscription);
    }

    @VisibleForTesting
    void inject(@NonNull Update update) {
        mSubscriptionListener.onSubscriptionChanged(update);
    }

    @VisibleForTesting
    void inject(@NonNull Intent intent) {
        mTimeChangedReceiver.onReceive(mContext, intent);
    }
}

