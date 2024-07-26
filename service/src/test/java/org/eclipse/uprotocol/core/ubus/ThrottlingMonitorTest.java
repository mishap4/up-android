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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.core.usubscription.SubscriptionData;
import org.eclipse.uprotocol.transport.builder.UMessageBuilder;
import org.eclipse.uprotocol.uuid.factory.UuidUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;

import java.util.Optional;

@RunWith(AndroidJUnit4.class)
public class ThrottlingMonitorTest extends TestBase {
    private static final int PERIOD = 100;
    private static final SubscriptionData SUBSCRIPTION =
            new SubscriptionData(RESOURCE_URI, CLIENT_URI_REMOTE, 0, PERIOD);

    private ThrottlingMonitor mThrottlingMonitor;

    @Before
    public void setUp() {
        mThrottlingMonitor = new ThrottlingMonitor();
    }

    @SuppressWarnings("SameParameterValue")
    private static SubscriptionData newSubscription(SubscriptionData subscription, int period) {
        return new SubscriptionData(subscription.topic(), subscription.subscriber(), 0, period);
    }

    @Test
    public void testCanProceedNoThrottling() {
        final SubscriptionData subscription = newSubscription(SUBSCRIPTION, 0);
        assertTrue(mThrottlingMonitor.canProceed(UMessageBuilder.publish(RESOURCE_URI).build(), subscription));
    }

    @Test
    public void testCanProceed() {
        assertTrue(mThrottlingMonitor.canProceed(UMessageBuilder.publish(RESOURCE_URI).build(), SUBSCRIPTION));
    }

    @Test
    public void testCanProceedThrottled() {
        testCanProceed();
        assertFalse(mThrottlingMonitor.canProceed(UMessageBuilder.publish(RESOURCE_URI).build(), SUBSCRIPTION));
    }

    @Test
    public void testCanProceedAfterPeriod() {
        testCanProceedThrottled();
        sleep(PERIOD);
        testCanProceed();
    }

    @Test
    public void testCanProceedFrequencyChanged() {
        testCanProceedThrottled();
        SubscriptionData subscription = newSubscription(SUBSCRIPTION, PERIOD / 2);
        assertTrue(mThrottlingMonitor.canProceed(UMessageBuilder.publish(RESOURCE_URI).build(), subscription));
    }

    @Test
    public void testCanProceedCreationTimestampInvalid() {
        try (MockedStatic<UuidUtils> util = mockStatic(UuidUtils.class)) {
            util.when(() -> UuidUtils.getTime(any())).thenReturn(Optional.of(-1L));
            assertTrue(mThrottlingMonitor.canProceed(UMessageBuilder.publish(RESOURCE_URI).build(), SUBSCRIPTION));
            assertFalse(mThrottlingMonitor.canProceed(UMessageBuilder.publish(RESOURCE_URI).build(), SUBSCRIPTION));
        }
    }

    @Test
    public void testCanProceedRealtimeReset() {
        testCanProceedThrottled();
        try (MockedStatic<SystemClock> clock = mockStatic(SystemClock.class)) {
            clock.when(SystemClock::elapsedRealtime).thenReturn(0L);
            try (MockedStatic<UuidUtils> util = mockStatic(UuidUtils.class)) {
                util.when(() -> UuidUtils.getTime(any())).thenReturn(Optional.of(1L));
                testCanProceed();
            }
        }
    }

    @Test
    public void testRemoveTracker() {
        testCanProceed();
        assertTrue(mThrottlingMonitor.isTracking(SUBSCRIPTION));
        assertTrue(mThrottlingMonitor.removeTracker(SUBSCRIPTION));
        assertFalse(mThrottlingMonitor.isTracking(SUBSCRIPTION));
        assertFalse(mThrottlingMonitor.removeTracker(SUBSCRIPTION));
    }

    @Test
    public void testClearCache() {
        testCanProceed();
        mThrottlingMonitor.clearCache();
        assertFalse(mThrottlingMonitor.isTracking(SUBSCRIPTION));
    }
}
