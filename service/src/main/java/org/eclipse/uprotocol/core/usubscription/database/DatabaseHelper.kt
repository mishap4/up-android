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
package org.eclipse.uprotocol.core.usubscription.database

import android.content.Context
import androidx.annotation.GuardedBy
import androidx.annotation.VisibleForTesting
import org.eclipse.uprotocol.core.usubscription.database.DatabaseConverters.toUri
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus.State
import org.eclipse.uprotocol.v1.UUID
import org.eclipse.uprotocol.v1.UUri

class DatabaseHelper {
    private val mLock = Any()

    @get:VisibleForTesting
    @GuardedBy("mLock")
    var database: SubscriptionDatabase? = null
        private set

    fun init(context: Context) {
        database = createDbExtension(context)
    }

    @VisibleForTesting
    fun init(database: SubscriptionDatabase) {
        this.database = database
    }

    private fun getSubscribersDao(): SubscribersDao {
        return database!!.getSubscribersDao()
    }

    private fun getSubscriptionsDao(): SubscriptionsDao {
        return database!!.getSubscriptionsDao()
    }

    private fun getObserversDao(): ObserversDao {
        return database!!.getObserversDao()
    }

    fun addSubscription(subscriptionRecord: SubscriptionRecord): Long {
        synchronized(mLock) {
            return getSubscriptionsDao().addSubscription(subscriptionRecord)
        }
    }

    fun deleteSubscription(topic: UUri): Int {
        synchronized(mLock) {
            return getSubscriptionsDao().deleteSubscription(topic)
        }
    }

    fun getSubscribedTopics(): List<UUri> {
        synchronized(mLock) {
            return getSubscriptionsDao().getSubscribedTopics().map { toUri(it) }
        }
    }

    fun getPendingSubscriptions(): List<SubscriptionRecord> {
        synchronized(mLock) {
            return getSubscriptionsDao().getPendingSubscriptions()
        }
    }

    fun getSubscriptionState(topic: UUri): State {
        synchronized(mLock) {
            return getSubscriptionsDao().getState(topic) ?: State.UNSUBSCRIBED
        }
    }

    fun updateSubscriptionState(topic: UUri, state: State): Int {
        synchronized(mLock) {
            return getSubscriptionsDao().updateState(topic, state)
        }
    }

    fun getTopic(requestId: UUID): UUri? {
        synchronized(mLock) {
            return getSubscriptionsDao().getTopic(requestId)?.let { toUri(it) }
        }
    }

    fun addSubscriber(subscriberRecord: SubscriberRecord): Long {
        synchronized(mLock) {
            return getSubscribersDao().addSubscriber(subscriberRecord)
        }
    }

    fun updateSubscriber(subscriberRecord: SubscriberRecord): Int {
        synchronized(mLock) {
            return getSubscribersDao().updateSubscriber(subscriberRecord)
        }
    }

    fun deleteSubscriber(topic: UUri, subscriber: UUri): Int {
        synchronized(mLock) {
            return getSubscribersDao().deleteSubscriber(topic, subscriber)
        }
    }

    fun deleteSubscribers(topic: UUri): Int {
        synchronized(mLock) {
            return getSubscribersDao().deleteSubscribers(topic)
        }
    }

    fun getSubscriber(topic: UUri, subscriber: UUri): SubscriberRecord? {
        synchronized(mLock) {
            return getSubscribersDao().getSubscriber(topic, subscriber)
        }
    }

    fun getFirstSubscriberForTopic(topic: UUri): SubscriberRecord? {
        synchronized(mLock) {
            return getSubscribersDao().getFirstSubscriberForTopic(topic)
        }
    }

    fun getSubscribersCount(topic: UUri): Int {
        synchronized(mLock) {
            return getSubscribersDao().getSubscribersCount(topic)
        }
    }

    fun getAllSubscribers(): List<SubscriberRecord> {
        synchronized(mLock) {
            return getSubscribersDao().getAllSubscribers()
        }
    }

    fun getSubscribersWithExpiryTime(): List<SubscriberRecord> {
        synchronized(mLock) {
            return getSubscribersDao().getSubscribersWithExpiryTime()
        }
    }

    fun getSubscribersFromPackage(packageName: String): List<SubscriberRecord> {
        synchronized(mLock) {
            return getSubscribersDao().getSubscribersFromPackage(packageName)
        }
    }

    fun getSubscribersByTopic(topic: UUri): List<SubscriberRecord> {
        synchronized(mLock) {
            return getSubscribersDao().getSubscribersByTopic(topic)
        }
    }

    fun getSubscribersByUri(subscriber: UUri): List<SubscriberRecord> {
        synchronized(mLock) {
            return getSubscribersDao().getSubscribersByUri(subscriber)
        }
    }

    fun getSubscribedPackages(): List<String> {
        synchronized(mLock) {
            return getSubscribersDao().getSubscribedPackages()
        }
    }

    fun addObserver(observerRecord: ObserverRecord): Long {
        synchronized(mLock) {
            return getObserversDao().addObserver(observerRecord)
        }
    }

    fun addObserver(topic: UUri): Long {
        return addObserver(ObserverRecord(topic))
    }

    fun deleteObserver(topic: UUri): Int {
        synchronized(mLock) {
            return getObserversDao().deleteObserver(topic)
        }
    }

    fun isObserved(topic: UUri): Boolean {
        synchronized(mLock) {
            return getObserversDao().isObserved(topic)
        }
    }

    fun shutdown() {
        if (database!!.isOpen) {
            database!!.close()
        }
    }
}
