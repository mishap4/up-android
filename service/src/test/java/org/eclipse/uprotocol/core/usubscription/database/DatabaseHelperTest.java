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
package org.eclipse.uprotocol.core.usubscription.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.common.collect.Sets;

import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus.State;
import org.eclipse.uprotocol.v1.UUri;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class DatabaseHelperTest extends TestBase {
    private DatabaseHelper mHelper;
    private SubscribersDao mSubscribersDao;
    private SubscriptionsDao mSubscriptionsDao;
    private ObserversDao mObserversDao;
    private Context mContext;

    @Before
    public void setUp() {
        mContext = spy(Context.class);
        mHelper = new DatabaseHelper();
        final SubscriptionDatabase database = Room.inMemoryDatabaseBuilder(mContext, SubscriptionDatabase.class)
                .allowMainThreadQueries()
                .build();
        when(SubscriptionDatabaseKt.createDbExtension(mContext)).thenReturn(database);
        mHelper.init(database);
        mSubscribersDao = database.getSubscribersDao();
        mSubscriptionsDao = database.getSubscriptionsDao();
        mObserversDao = database.getObserversDao();
    }


    @After
    public void tearDown() {
        mHelper.shutdown();
    }

    @Test
    public void testInit() {
        mHelper.init(mContext);
        assertNotNull(mHelper.getDatabase());
    }

    @Test
    public void testAddSubscription() {
        assertEquals(1, mHelper.addSubscription(new SubscriptionRecord(RESOURCE_URI, State.SUBSCRIBED, ID)));
        assertEquals(State.SUBSCRIBED, mSubscriptionsDao.getState(RESOURCE_URI));
    }

    @Test
    public void testDeleteSubscription() {
        mHelper.addSubscription(new SubscriptionRecord(RESOURCE_URI, State.SUBSCRIBED, ID));
        assertEquals(1, mHelper.deleteSubscription(RESOURCE_URI));
        assertNull(mSubscriptionsDao.getState(RESOURCE_URI));
    }

    @Test
    public void testGetSubscribedTopics() {
        final Set<SubscriptionRecord> subscriptions = Set.of(
                new SubscriptionRecord(RESOURCE_URI_REMOTE, State.SUBSCRIBE_PENDING, createId()),
                new SubscriptionRecord(RESOURCE2_URI_REMOTE, State.UNSUBSCRIBE_PENDING, createId()),
                new SubscriptionRecord(RESOURCE_URI, State.SUBSCRIBED, createId()),
                new SubscriptionRecord(RESOURCE2_URI, State.UNSUBSCRIBED, createId())
        );
        final Set<UUri> subscribedTopics = subscriptions.stream()
                .filter(it -> it.getState() == State.SUBSCRIBED || it.getState() == State.SUBSCRIBE_PENDING)
                .map(SubscriptionRecord::getTopic)
                .collect(Collectors.toSet());
        subscriptions.forEach(mSubscriptionsDao::addSubscription);
        assertEquals(subscribedTopics, Set.copyOf(mHelper.getSubscribedTopics()));
    }

    @Test
    public void testGetPendingTopics() {
        final Set<SubscriptionRecord> subscriptions = Set.of(
                new SubscriptionRecord(RESOURCE_URI_REMOTE, State.SUBSCRIBE_PENDING, createId()),
                new SubscriptionRecord(RESOURCE2_URI_REMOTE, State.UNSUBSCRIBE_PENDING, createId()),
                new SubscriptionRecord(RESOURCE_URI, State.SUBSCRIBED, createId()),
                new SubscriptionRecord(RESOURCE2_URI, State.UNSUBSCRIBED, createId())
        );
        final Set<SubscriptionRecord> pendingSubscriptions = subscriptions.stream()
                .filter(it -> it.getState() == State.SUBSCRIBE_PENDING || it.getState() == State.UNSUBSCRIBE_PENDING)
                .collect(Collectors.toSet());
        subscriptions.forEach(mSubscriptionsDao::addSubscription);
        assertEquals(pendingSubscriptions, Set.copyOf(mHelper.getPendingSubscriptions()));
    }

    @Test
    public void testGetSubscriptionState() {
        mHelper.addSubscription(new SubscriptionRecord(RESOURCE_URI, State.SUBSCRIBED, ID));
        assertEquals(State.SUBSCRIBED, mHelper.getSubscriptionState(RESOURCE_URI));
    }

    @Test
    public void testGetSubscriptionStateNoRecord() {
        assertEquals(State.UNSUBSCRIBED, mHelper.getSubscriptionState(RESOURCE2_URI));
    }


    @Test
    public void testUpdateState() {
        mHelper.addSubscription(new SubscriptionRecord(RESOURCE_URI, State.SUBSCRIBED, ID));
        assertEquals(1, mHelper.updateSubscriptionState(RESOURCE_URI, State.UNSUBSCRIBE_PENDING));
        assertEquals(State.UNSUBSCRIBE_PENDING, mHelper.getSubscriptionState(RESOURCE_URI));
    }

    @Test
    public void testGetTopic() {
        mHelper.addSubscription(new SubscriptionRecord(RESOURCE_URI, State.SUBSCRIBED, ID));
        assertEquals(RESOURCE_URI, mHelper.getTopic(ID));
    }

    @Test
    public void testGetTopicNoRecord() {
        assertNull(mHelper.getTopic(createId()));
    }
    
    @Test
    public void testAddSubscriber() {
        final SubscriberRecord subscriber = new SubscriberRecord(RESOURCE_URI, CLIENT_URI, 1000, 100, PACKAGE_NAME, ID);
        assertEquals(1, mHelper.addSubscriber(subscriber));
        assertEquals(subscriber, mSubscribersDao.getSubscriber(RESOURCE_URI, CLIENT_URI));
    }

    @Test
    public void testUpdateSubscriber() {
        mHelper.addSubscriber(new SubscriberRecord(RESOURCE_URI, CLIENT_URI, 1000, 100, PACKAGE_NAME, ID));
        final SubscriberRecord subscriber = new SubscriberRecord(RESOURCE_URI, CLIENT_URI, 0, 10, PACKAGE_NAME, ID);
        assertEquals(1, mHelper.updateSubscriber(subscriber));
        assertEquals(subscriber, mSubscribersDao.getSubscriber(RESOURCE_URI, CLIENT_URI));
    }

    @Test
    public void testDeleteSubscriber() {
        mHelper.addSubscriber(new SubscriberRecord(RESOURCE_URI, CLIENT_URI));
        assertEquals(1, mHelper.deleteSubscriber(RESOURCE_URI, CLIENT_URI));
        assertNull(mSubscribersDao.getSubscriber(RESOURCE_URI, CLIENT_URI));
    }

    @Test
    public void testDeleteSubscribers() {
        mHelper.addSubscriber(new SubscriberRecord(RESOURCE_URI, CLIENT_URI));
        mHelper.addSubscriber(new SubscriberRecord(RESOURCE_URI, CLIENT2_URI));
        assertEquals(2, mHelper.deleteSubscribers(RESOURCE_URI));
        assertTrue(mSubscribersDao.getSubscribersByTopic(RESOURCE_URI).isEmpty());
    }

    @Test
    public void testGetSubscriber() {
        final SubscriberRecord subscriber = new SubscriberRecord(RESOURCE_URI, CLIENT_URI);
        mHelper.addSubscriber(subscriber);
        assertEquals(subscriber, mHelper.getSubscriber(RESOURCE_URI, CLIENT_URI));
    }

    @Test
    public void testGetFirstSubscriberForTopic() {
        final SubscriberRecord subscriber1 = new SubscriberRecord(RESOURCE_URI, CLIENT_URI);
        final SubscriberRecord subscriber2 = new SubscriberRecord(RESOURCE_URI, CLIENT2_URI);
        mHelper.addSubscriber(subscriber1);
        mHelper.addSubscriber(subscriber2);
        assertEquals(subscriber1, mHelper.getFirstSubscriberForTopic(RESOURCE_URI));
    }

    @Test
    public void testGetSubscribersCount() {
        mHelper.addSubscriber(new SubscriberRecord(RESOURCE_URI, CLIENT_URI));
        mHelper.addSubscriber(new SubscriberRecord(RESOURCE_URI, CLIENT2_URI));
        assertEquals(2, mHelper.getSubscribersCount(RESOURCE_URI));
    }

    @Test
    public void testGetAllSubscribers() {
        final Set<SubscriberRecord> subscribers = Set.of(
                new SubscriberRecord(RESOURCE_URI, CLIENT_URI),
                new SubscriberRecord(RESOURCE_URI, CLIENT2_URI)
        );
        subscribers.forEach(mHelper::addSubscriber);
        assertEquals(subscribers, Set.copyOf(mHelper.getAllSubscribers()));
    }

    @Test
    public void testGetSubscribersWithExpiryTime() {
        final Set<SubscriberRecord> subscribers1 = Set.of(
                new SubscriberRecord(RESOURCE_URI, CLIENT_URI, 1000),
                new SubscriberRecord(RESOURCE2_URI, CLIENT2_URI, 2000)
        );
        final Set<SubscriberRecord> subscribers2 = Set.of(
                new SubscriberRecord(RESOURCE2_URI, CLIENT_URI)
        );
        Sets.union(subscribers1, subscribers2).forEach(subscriber -> mSubscribersDao.addSubscriber(subscriber));
        assertEquals(subscribers1, Set.copyOf(mHelper.getSubscribersWithExpiryTime()));
    }

    @Test
    public void testGetSubscribersFromPackage() {
        final Set<SubscriberRecord> subscribers1 = Set.of(
                new SubscriberRecord(RESOURCE_URI, CLIENT_URI, 0, 0, PACKAGE_NAME),
                new SubscriberRecord(RESOURCE2_URI, CLIENT_URI, 0, 0, PACKAGE_NAME)
        );
        final Set<SubscriberRecord> subscribers2 = Set.of(
                new SubscriberRecord(RESOURCE_URI, CLIENT2_URI)
        );
        Sets.union(subscribers1, subscribers2).forEach(subscriber -> mSubscribersDao.addSubscriber(subscriber));
        assertEquals(subscribers1, Set.copyOf(mHelper.getSubscribersFromPackage(PACKAGE_NAME)));
    }

    @Test
    public void testGetSubscribersByTopic() {
        final Set<SubscriberRecord> subscribers1 = Set.of(
                new SubscriberRecord(RESOURCE_URI, CLIENT_URI),
                new SubscriberRecord(RESOURCE_URI, CLIENT2_URI)
        );
        final Set<SubscriberRecord> subscribers2 = Set.of(
                new SubscriberRecord(RESOURCE2_URI, CLIENT_URI),
                new SubscriberRecord(RESOURCE2_URI, CLIENT2_URI)
        );
        Sets.union(subscribers1, subscribers2).forEach(subscriber -> mSubscribersDao.addSubscriber(subscriber));
        assertEquals(subscribers1, Set.copyOf(mHelper.getSubscribersByTopic(RESOURCE_URI)));
    }

    @Test
    public void testGetSubscribersByUri() {
        final Set<SubscriberRecord> subscribers1 = Set.of(
                new SubscriberRecord(RESOURCE_URI, CLIENT_URI),
                new SubscriberRecord(RESOURCE2_URI, CLIENT_URI)
        );
        final Set<SubscriberRecord> subscribers2 = Set.of(
                new SubscriberRecord(RESOURCE_URI, CLIENT2_URI),
                new SubscriberRecord(RESOURCE2_URI, CLIENT2_URI)
        );
        Sets.union(subscribers1, subscribers2).forEach(subscriber -> mSubscribersDao.addSubscriber(subscriber));
        assertEquals(subscribers1, Set.copyOf(mHelper.getSubscribersByUri(CLIENT_URI)));
    }

    @Test
    public void testGetSubscribedPackages() {
        final Set<SubscriberRecord> subscribers = Set.of(
                new SubscriberRecord(RESOURCE_URI, CLIENT_URI, 0, 0, PACKAGE_NAME),
                new SubscriberRecord(RESOURCE_URI, CLIENT2_URI, 0, 0, PACKAGE2_NAME)
        );
        final Set<String> packages = subscribers.stream()
                .map(SubscriberRecord::getPackageName)
                .collect(Collectors.toSet());
        subscribers.forEach(subscriber -> mSubscribersDao.addSubscriber(subscriber));
        assertEquals(packages, Set.copyOf(mHelper.getSubscribedPackages()));
    }

    @Test
    public void testAddObserver() {
        assertEquals(1, mHelper.addObserver(RESOURCE_URI));
        assertTrue(mObserversDao.isObserved(RESOURCE_URI));
    }

    @Test
    public void testDeleteObserver() {
        mHelper.addObserver(new ObserverRecord(RESOURCE_URI));
        assertEquals(1, mHelper.deleteObserver(RESOURCE_URI));
        assertFalse(mObserversDao.isObserved(RESOURCE_URI));
    }

    @Test
    public void testIsObserved() {
        assertFalse(mHelper.isObserved(RESOURCE_URI));
        mHelper.addObserver(new ObserverRecord(RESOURCE_URI));
        assertTrue(mHelper.isObserved(RESOURCE_URI));
    }
}
