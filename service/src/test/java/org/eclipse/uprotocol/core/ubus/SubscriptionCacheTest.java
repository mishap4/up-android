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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.core.usubscription.SubscriptionData;
import org.eclipse.uprotocol.core.usubscription.USubscription;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class SubscriptionCacheTest extends TestBase {
    private final USubscription mUSubscription = mock(USubscription.class);
    private SubscriptionCache mSubscriptionCache;

    @Before
    public void setUp() {
        mSubscriptionCache = new SubscriptionCache();
        mSubscriptionCache.setService(mUSubscription);
    }

    private void clear() {
        mSubscriptionCache.clear();
        assertTrue(mSubscriptionCache.isEmpty());
    }

    @Test
    public void testGetSubscriptions() {
        final Set<SubscriptionData> subscriptions = Set.of(
                new SubscriptionData(RESOURCE_URI, CLIENT_URI),
                new SubscriptionData(RESOURCE_URI, CLIENT2_URI));
        doReturn(subscriptions).when(mUSubscription).getSubscriptions(RESOURCE_URI);
        assertEquals(subscriptions, mSubscriptionCache.getSubscriptions(RESOURCE_URI));
    }

    @Test
    public void testGetSubscribersNoService() {
        mSubscriptionCache.setService(null);
        assertTrue(mSubscriptionCache.getSubscriptions(RESOURCE_URI).isEmpty());
    }

    @Test
    public void testAddSubscription() {
        final SubscriptionData subscription = new SubscriptionData(RESOURCE_URI, CLIENT_URI);
        assertTrue(mSubscriptionCache.addSubscription(subscription));
        assertTrue(mSubscriptionCache.getSubscriptions(RESOURCE_URI).contains(subscription));
    }

    @Test
    public void testAddSubscriptionReplace() {
        testAddSubscription();
        final SubscriptionData subscription = new SubscriptionData(RESOURCE_URI, CLIENT_URI, 0, 100);
        assertTrue(mSubscriptionCache.addSubscription(subscription));
        final Set<SubscriptionData> subscriptions = mSubscriptionCache.getSubscriptions(RESOURCE_URI);
        assertEquals(1, subscriptions.size());
        assertSame(subscription, subscriptions.iterator().next());
    }

    @Test
    public void testRemoveSubscription() {
        final SubscriptionData subscription = new SubscriptionData(RESOURCE_URI, CLIENT_URI);
        assertFalse(mSubscriptionCache.removeSubscription(subscription));
        assertFalse(mSubscriptionCache.getSubscriptions(RESOURCE_URI).contains(subscription));
    }

    @Test
    public void testRemoveSubscriptionNotEmpty() {
        testAddSubscription();
        final SubscriptionData subscription = new SubscriptionData(RESOURCE_URI, CLIENT_URI);
        assertTrue(mSubscriptionCache.removeSubscription(subscription));
        assertFalse(mSubscriptionCache.getSubscriptions(RESOURCE_URI).contains(subscription));
    }

    @Test
    public void testRemoveSubscriptionAlreadyRemoved() {
        testRemoveSubscriptionNotEmpty();
        final SubscriptionData subscription = new SubscriptionData(RESOURCE_URI, CLIENT_URI);
        assertFalse(mSubscriptionCache.removeSubscription(subscription));
    }

    @Test
    public void testIsTopicSubscribed() {
        final SubscriptionData subscription = new SubscriptionData(RESOURCE_URI, CLIENT_URI);
        assertTrue(mSubscriptionCache.addSubscription(subscription));
        assertTrue(mSubscriptionCache.isTopicSubscribed(RESOURCE_URI, CLIENT_URI));

        assertTrue(mSubscriptionCache.removeSubscription(subscription));
        assertFalse(mSubscriptionCache.isTopicSubscribed(RESOURCE_URI, CLIENT_URI));
    }

    @Test
    public void testGetSubscribedTopics() {
        final SubscriptionData subscription1 = new SubscriptionData(RESOURCE_URI, CLIENT_URI);
        final SubscriptionData subscription2 = new SubscriptionData(RESOURCE2_URI, CLIENT_URI);
        assertTrue(mSubscriptionCache.addSubscription(subscription1));
        assertTrue(mSubscriptionCache.addSubscription(subscription2));
        assertEquals(Set.of(RESOURCE_URI, RESOURCE2_URI), mSubscriptionCache.getSubscribedTopics());

        assertTrue(mSubscriptionCache.removeSubscription(subscription1));
        assertEquals(Set.of(RESOURCE2_URI), mSubscriptionCache.getSubscribedTopics());
    }

    @Test
    public void testIsEmpty() {
        assertTrue(mSubscriptionCache.isEmpty());
        testAddSubscription();
        assertFalse(mSubscriptionCache.isEmpty());
    }

    @Test
    public void testClear() {
        clear();
        assertTrue(mSubscriptionCache.isEmpty());

        testAddSubscription();
        assertFalse(mSubscriptionCache.isEmpty());

        clear();
        assertTrue(mSubscriptionCache.isEmpty());
    }
}
