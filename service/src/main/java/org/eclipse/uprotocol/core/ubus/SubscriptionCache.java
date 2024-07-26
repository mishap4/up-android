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

import static org.eclipse.uprotocol.core.internal.util.CommonUtils.emptyIfNull;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import org.eclipse.uprotocol.core.usubscription.SubscriptionData;
import org.eclipse.uprotocol.core.usubscription.USubscription;
import org.eclipse.uprotocol.v1.UUri;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

class SubscriptionCache {
    private final Map<UUri, Set<SubscriptionData>> mSubscriptionsByTopic = new ConcurrentHashMap<>();
    private USubscription mService;

    public void setService(USubscription service) {
        mService = service;
    }

    protected @NonNull Set<SubscriptionData> getSubscriptions(@NonNull UUri topic) {
        return mSubscriptionsByTopic.computeIfAbsent(topic, key -> {
            final USubscription service = mService;
            return (service != null) ?
                    emptyIfNull(service.getSubscriptions(topic)).stream()
                            .collect(Collectors.toCollection(ConcurrentHashMap::newKeySet)) :
                    ConcurrentHashMap.newKeySet();
        });
    }

    private static <E> boolean addOrReplace(@NonNull Set<E> set, @NonNull E element) {
        set.remove(element);
        return set.add(element);
    }

    public boolean addSubscription(@NonNull SubscriptionData subscription) {
        return addOrReplace(getSubscriptions(subscription.topic()), subscription);
    }

    public boolean removeSubscription(@NonNull SubscriptionData subscription) {
        return getSubscriptions(subscription.topic()).remove(subscription);
    }

    public boolean isTopicSubscribed(@NonNull UUri topic, @NonNull UUri subscriber) {
        return getSubscriptions(topic).contains(new SubscriptionData(topic, subscriber));
    }

    @VisibleForTesting
    @NonNull Set<UUri> getSubscribedTopics() {
        return mSubscriptionsByTopic.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public void clear() {
        mSubscriptionsByTopic.clear();
    }

    public boolean isEmpty() {
        return mSubscriptionsByTopic.isEmpty();
    }
}
