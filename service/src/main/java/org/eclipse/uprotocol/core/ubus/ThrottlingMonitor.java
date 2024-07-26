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

import static org.eclipse.uprotocol.uuid.factory.UuidUtils.getTime;

import android.os.SystemClock;

import androidx.annotation.NonNull;

import org.eclipse.uprotocol.core.usubscription.SubscriptionData;
import org.eclipse.uprotocol.v1.UMessage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ThrottlingMonitor extends UBus.Component {
    private final Map<SubscriptionData, Tracker> mTrackers = new ConcurrentHashMap<>();

    private static class Tracker {
        private final long period;
        private long lastRealtime;
        private long lastTimestamp;

        public Tracker(long period) {
            this.period = period;
        }

        public boolean canProceed(long timestamp) {
            final boolean realtimeChecked = canProceedRealtime();
            if (timestamp < lastTimestamp) { // Clock reset?
                lastTimestamp = timestamp;
                return realtimeChecked;
            }
            if (timestamp >= (lastTimestamp + period)) {
                lastTimestamp = timestamp;
                return true;
            }
            return false;
        }

        private boolean canProceedRealtime() {
            final long realtime = SystemClock.elapsedRealtime();
            if (realtime < lastRealtime /* Shouldn't happen */ || realtime >= (lastRealtime + period)) {
                lastRealtime = realtime;
                return true;
            }
            return false;
        }
    }

    public boolean canProceed(@NonNull UMessage message, @NonNull SubscriptionData subscription) {
        if (subscription.samplingPeriod() <= 0) {
            return true;
        }
        final long timestamp = getTime(message.getAttributes().getId())
                .filter(it -> it > 0)
                .orElse(System.currentTimeMillis());
        final var wrapper = new Object() {
            boolean result = true;
        };
        mTrackers.compute(subscription, (it, tracker) -> {
            if (tracker == null || tracker.period != subscription.samplingPeriod()) {
                tracker = new Tracker(subscription.samplingPeriod());
            }
            wrapper.result = tracker.canProceed(timestamp);
            return tracker;
        });
        return wrapper.result;
    }

    public boolean removeTracker(@NonNull SubscriptionData subscription) {
        return mTrackers.remove(subscription) != null;
    }

    public boolean isTracking(@NonNull SubscriptionData subscription) {
        return mTrackers.containsKey(subscription);
    }

    @Override
    public void clearCache() {
        mTrackers.clear();
    }
}
