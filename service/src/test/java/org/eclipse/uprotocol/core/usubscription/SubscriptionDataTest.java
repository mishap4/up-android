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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.core.usubscription.database.SubscriberRecord;
import org.eclipse.uprotocol.core.usubscription.v3.SubscribeAttributes;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriberInfo;
import org.eclipse.uprotocol.core.usubscription.v3.Update;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SubscriptionDataTest extends TestBase {
    private static final SubscriptionData SUBSCRIPTION = new SubscriptionData(RESOURCE_URI, CLIENT_URI, EXPIRY_TIME, PERIOD);
    private static final SubscriptionData SUBSCRIPTION2 = new SubscriptionData(RESOURCE2_URI, CLIENT2_URI);

    @Test
    public void testConstruct() {
        assertEquals(RESOURCE_URI, SUBSCRIPTION.topic());
        assertEquals(CLIENT_URI, SUBSCRIPTION.subscriber());
        assertEquals(EXPIRY_TIME, SUBSCRIPTION.expiryTime());
        assertEquals(PERIOD, SUBSCRIPTION.samplingPeriod());
    }

    @Test
    public void testConstructWithSubscriberRecord() {
        final SubscriberRecord subscriber = new SubscriberRecord(RESOURCE_URI, CLIENT_URI, EXPIRY_TIME, PERIOD);
        assertEquals(SUBSCRIPTION, new SubscriptionData(subscriber));
    }

    @Test
    public void testConstructWithUpdate() {
        final Update update = Update.newBuilder()
                .setTopic(RESOURCE_URI)
                .setSubscriber(SubscriberInfo.newBuilder().setUri(CLIENT_URI))
                .setAttributes(SubscribeAttributes.newBuilder()
                        .setExpire(fromMillis(EXPIRY_TIME))
                        .setSamplePeriodMs(PERIOD))
                .build();
        assertEquals(SUBSCRIPTION, new SubscriptionData(update));
    }

    @Test
    public void testIsValid() {
        assertTrue(SUBSCRIPTION.isValid());
        assertFalse(new SubscriptionData(EMPTY_URI, CLIENT2_URI).isValid());
        assertFalse(new SubscriptionData(RESOURCE_URI, EMPTY_URI).isValid());
    }

    @Test
    public void testHasExpiryTime() {
        assertTrue(new SubscriptionData(RESOURCE_URI, CLIENT_URI, EXPIRY_TIME, 0).hasExpiryTime());
        assertFalse(new SubscriptionData(RESOURCE_URI, CLIENT_URI, 0, 0).hasExpiryTime());
        assertFalse(new SubscriptionData(RESOURCE_URI, CLIENT_URI, -1, 0).hasExpiryTime());
    }

    @Test
    public void testHasSamplingPeriod() {
        assertTrue(new SubscriptionData(RESOURCE_URI, CLIENT_URI, 0, PERIOD).hasSamplingPeriod());
        assertFalse(new SubscriptionData(RESOURCE_URI, CLIENT_URI, 0, 0).hasSamplingPeriod());
        assertFalse(new SubscriptionData(RESOURCE_URI, CLIENT_URI, 0, -1).hasSamplingPeriod());
    }

    @SuppressWarnings("EqualsWithItself")
    @Test
    public void testEquals() {
        assertEquals(SUBSCRIPTION, new SubscriptionData(RESOURCE_URI, CLIENT_URI));
        assertEquals(SUBSCRIPTION, SUBSCRIPTION);
        assertNotEquals(SUBSCRIPTION, SUBSCRIPTION2);
        assertNotEquals(SUBSCRIPTION, new Object());
        assertNotEquals(SUBSCRIPTION, null);
    }

    @Test
    public void testHashCode() {
        assertEquals(SUBSCRIPTION.hashCode(), new SubscriptionData(RESOURCE_URI, CLIENT_URI).hashCode());
        assertEquals(SUBSCRIPTION.hashCode(), SUBSCRIPTION.hashCode());
        assertNotEquals(SUBSCRIPTION.hashCode(), SUBSCRIPTION2.hashCode());
        assertNotEquals(SUBSCRIPTION.hashCode(), new Object().hashCode());
    }

    @Test
    public void testToString() {
        assertEquals("[topic: /50/1/8000, subscriber: /52/1/0, expiry: 5000, period: 100]", SUBSCRIPTION.toString());
    }
}
