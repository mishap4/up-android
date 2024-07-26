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

import static com.google.common.collect.Sets.newHashSet;
import static com.google.protobuf.util.Timestamps.toMillis;

import static org.eclipse.uprotocol.common.util.UStatusUtils.checkArgument;
import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.common.util.log.Formatter.stringify;
import static org.eclipse.uprotocol.communication.UPayload.unpack;
import static org.eclipse.uprotocol.core.internal.util.UUriUtils.isLocalUri;
import static org.eclipse.uprotocol.core.internal.util.UUriUtils.isRemoteUri;
import static org.eclipse.uprotocol.core.internal.util.UUriUtils.removeResource;
import static org.eclipse.uprotocol.core.internal.util.log.FormatterExt.stringify;
import static org.eclipse.uprotocol.core.usubscription.SubscriptionUtils.buildRequestData;
import static org.eclipse.uprotocol.core.usubscription.SubscriptionUtils.buildSubscriber;
import static org.eclipse.uprotocol.core.usubscription.SubscriptionUtils.buildSubscription;
import static org.eclipse.uprotocol.core.usubscription.SubscriptionUtils.buildSubscriptionStatus;
import static org.eclipse.uprotocol.core.usubscription.SubscriptionUtils.buildUpdate;
import static org.eclipse.uprotocol.core.usubscription.SubscriptionUtils.checkSameEntity;
import static org.eclipse.uprotocol.core.usubscription.USubscription.DEBUG;
import static org.eclipse.uprotocol.core.usubscription.USubscription.TAG;
import static org.eclipse.uprotocol.core.usubscription.USubscription.VERBOSE;
import static org.eclipse.uprotocol.core.usubscription.USubscription.logStatus;
import static org.eclipse.uprotocol.uri.validator.UriValidator.isEmpty;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.communication.UStatusException;
import org.eclipse.uprotocol.core.ubus.client.Credentials;
import org.eclipse.uprotocol.core.usubscription.database.DatabaseHelper;
import org.eclipse.uprotocol.core.usubscription.database.SubscriberRecord;
import org.eclipse.uprotocol.core.usubscription.database.SubscriptionRecord;
import org.eclipse.uprotocol.core.usubscription.v3.FetchSubscribersRequest;
import org.eclipse.uprotocol.core.usubscription.v3.FetchSubscribersResponse;
import org.eclipse.uprotocol.core.usubscription.v3.FetchSubscriptionsRequest;
import org.eclipse.uprotocol.core.usubscription.v3.FetchSubscriptionsRequest.RequestCase;
import org.eclipse.uprotocol.core.usubscription.v3.FetchSubscriptionsResponse;
import org.eclipse.uprotocol.core.usubscription.v3.NotificationsRequest;
import org.eclipse.uprotocol.core.usubscription.v3.NotificationsResponse;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriberInfo;
import org.eclipse.uprotocol.core.usubscription.v3.Subscription;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionRequest;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionResponse;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus.State;
import org.eclipse.uprotocol.core.usubscription.v3.UnsubscribeRequest;
import org.eclipse.uprotocol.core.usubscription.v3.UnsubscribeResponse;
import org.eclipse.uprotocol.core.usubscription.v3.Update;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UUID;
import org.eclipse.uprotocol.v1.UUri;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("java:S1200")
public class SubscriptionHandler {
    public static final String MESSAGE_INVALID_PAYLOAD = "Invalid payload";
    public static final String MESSAGE_REMOTE_UNSUPPORTED = "Remote requests are not supported";
    private final Context mContext;
    private final DatabaseHelper mDatabaseHelper;
    private USubscription mUSubscription;

    public SubscriptionHandler(@NonNull Context context) {
        this(context, new DatabaseHelper());
    }

    @VisibleForTesting
    public SubscriptionHandler(@NonNull Context context, @NonNull DatabaseHelper databaseHelper) {
        mContext = context;
        mDatabaseHelper = databaseHelper;
    }

    public void init(USubscription usubscription) {
        mUSubscription = usubscription;
        mDatabaseHelper.init(mContext);
    }

    public void startup() {
        // Nothing to do
    }

    public void shutdown() {
        mDatabaseHelper.shutdown();
    }

    public @NonNull SubscriptionResponse subscribe(@NonNull UMessage message) throws UStatusException {
        final UUID requestId = message.getAttributes().getId();
        final UUri source = message.getAttributes().getSource();
        final SubscriptionRequest request = unpack(message, SubscriptionRequest.class)
                .orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, MESSAGE_INVALID_PAYLOAD));
        final RequestData data = buildRequestData(request, isRemoteUri(source));
        final Credentials credentials = mUSubscription.getCallerCredentials(requestId);
        checkArgument(!isEmpty(data.topic()), UCode.INVALID_ARGUMENT, "Topic is empty");
        checkArgument(!isEmpty(data.subscriber()), UCode.INVALID_ARGUMENT, "Subscriber is empty");
        checkSameEntity(source, data.subscriber());

        final SubscriptionStatus status;
        if (isLocalUri(data.topic())) {
            status = subscribeLocal(data, requestId, credentials);
        } else {
            throw new UStatusException(UCode.UNIMPLEMENTED, MESSAGE_REMOTE_UNSUPPORTED);
        }
        return SubscriptionResponse.newBuilder()
                .setTopic(data.topic())
                .setStatus(status)
                .build();
    }

    private @NonNull SubscriptionStatus subscribeLocal(@NonNull RequestData data, @NonNull UUID requestId,
            @NonNull Credentials credentials) {
        if (!isTopicSubscribed(data.topic())) {
            addSubscription(data, requestId, State.SUBSCRIBED);
        }
        return addOrUpdateSubscriber(data, requestId, credentials);
    }

    @SuppressWarnings("SameParameterValue")
    private void addSubscription(@NonNull RequestData data, @NonNull UUID requestId, @NonNull State state) {
        final SubscriptionRecord subscriptionRecord = new SubscriptionRecord(data.topic(), state, requestId);
        if (mDatabaseHelper.addSubscription(subscriptionRecord) < 0) {
            throw new UStatusException(UCode.INTERNAL, "Failed to add subscription: " + subscriptionRecord);
        }
        if (VERBOSE) {
            Log.v(TAG, join(Key.MESSAGE, "Subscription added", Key.SUBSCRIPTION, subscriptionRecord));
        }
    }

    private @NonNull SubscriptionStatus addOrUpdateSubscriber(@NonNull RequestData data, @NonNull UUID requestId,
            @NonNull Credentials credentials) {
        final String packageName = isLocalUri(data.subscriber()) ? credentials.packageName() : "";
        final SubscriberRecord newRecord = new SubscriberRecord(data.topic(), data.subscriber(),
                toMillis(data.attributes().getExpire()), data.attributes().getSamplePeriodMs(), packageName, requestId);
        final SubscriberRecord oldRecord = mDatabaseHelper.getSubscriber(data.topic(), data.subscriber());
        if (oldRecord != null) {
            return updateSubscriber(data, newRecord, shouldNotify(oldRecord, newRecord));
        } else {
            return addSubscriber(data, newRecord);
        }
    }

    @VisibleForTesting
    static boolean shouldNotify(@NonNull SubscriberRecord oldRecord, @NonNull SubscriberRecord newRecord) {
        return (oldRecord.getExpiryTime() != newRecord.getExpiryTime()) ||
               (oldRecord.getSamplingPeriod() != newRecord.getSamplingPeriod());
    }

    private @NonNull SubscriptionStatus addSubscriber(@NonNull RequestData data,
            @NonNull SubscriberRecord subscriberRecord) {
        if (mDatabaseHelper.addSubscriber(subscriberRecord) < 0) {
            throw new UStatusException(UCode.INTERNAL, "Failed to add subscriber: " + subscriberRecord);
        }
        if (VERBOSE) {
            Log.v(TAG, join(Key.MESSAGE, "Subscriber added", Key.SUBSCRIBER, subscriberRecord));
        }
        final Set<UUri> notifiers = getObserver(data.topic()).map(Collections::singleton).orElse(emptySet());
        final SubscriptionStatus status = buildSubscriptionStatus(getSubscriptionState(data.topic()));
        notifySubscriptionUpdate(notifiers, buildUpdate(data.topic(), data.subscriber(), data.attributes(), status));
        return status;
    }

    private @NonNull SubscriptionStatus updateSubscriber(@NonNull RequestData data,
            @NonNull SubscriberRecord subscriberRecord, boolean shouldNotify) {
        if (mDatabaseHelper.updateSubscriber(subscriberRecord) < 0) {
            throw new UStatusException(UCode.INTERNAL, "Failed to update subscriber: " + subscriberRecord);
        }
        if (VERBOSE) {
            Log.v(TAG, join(Key.MESSAGE, "Subscriber updated", Key.SUBSCRIBER, subscriberRecord));
        }
        final SubscriptionStatus status = buildSubscriptionStatus(getSubscriptionState(data.topic()));
        if (shouldNotify) {
            final Set<UUri> notifiers = emptySet(); // Should observer be notified if subscription attributes changed?
            notifySubscriptionUpdate(notifiers, buildUpdate(data.topic(), data.subscriber(), data.attributes(), status));
        }
        return status;
    }

    public @NonNull UnsubscribeResponse unsubscribe(@NonNull UMessage message) throws UStatusException {
        final UUri source = message.getAttributes().getSource();
        final UnsubscribeRequest request = unpack(message, UnsubscribeRequest.class)
                .orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, MESSAGE_INVALID_PAYLOAD));
        final RequestData data = buildRequestData(request, isRemoteUri(source));
        checkArgument(!isEmpty(data.topic()), UCode.INVALID_ARGUMENT, "Topic is empty");
        checkArgument(!isEmpty(data.subscriber()), UCode.INVALID_ARGUMENT, "Subscriber is empty");
        checkSameEntity(source, data.subscriber());

        if (isTopicSubscribed(data.topic())) {
            deleteSubscriber(data);
        }
        return UnsubscribeResponse.getDefaultInstance();
    }

    private void deleteSubscriber(@NonNull RequestData data) {
        final SubscriberRecord subscriberRecord = mDatabaseHelper.getSubscriber(data.topic(), data.subscriber());
        if (subscriberRecord != null) {
            deleteSubscriber(subscriberRecord);
        }
    }

    private void deleteSubscriber(@NonNull SubscriberRecord subscriberRecord) {
        final RequestData data = new RequestData(subscriberRecord);
        if (mDatabaseHelper.deleteSubscriber(data.topic(), data.subscriber()) < 0) {
            throw new UStatusException(UCode.INTERNAL, "Failed to delete subscriber: " + subscriberRecord);
        }
        if (VERBOSE) {
            Log.v(TAG, join(Key.MESSAGE, "Subscriber deleted", Key.TOPIC, stringify(data.topic()),
                    Key.SUBSCRIBER, stringify(data.subscriber())));
        }
        if (mDatabaseHelper.getSubscribersCount(data.topic()) <= 0) {
            deleteSubscription(data);
        }
        final SubscriptionStatus status = buildSubscriptionStatus(State.UNSUBSCRIBED);
        final Set<UUri> notifiers = newHashSet(data.subscriber());
        getObserver(data.topic()).ifPresent(notifiers::add);
        notifySubscriptionUpdate(notifiers, buildUpdate(data.topic(), data.subscriber(), data.attributes(), status));
    }

    private void deleteSubscription(@NonNull RequestData data) {
        if (mDatabaseHelper.deleteSubscription(data.topic()) < 0) {
            throw new UStatusException(UCode.INTERNAL, "Failed to delete subscription: " + stringify(data.topic()));
        }
        if (VERBOSE) {
            Log.v(TAG, join(Key.MESSAGE, "Subscription deleted", Key.TOPIC, stringify(data.topic())));
        }
    }

    public void unsubscribe(@NonNull UUri topic, @NonNull UUri subscriber) {
        try {
            deleteSubscriber(new RequestData(topic, subscriber));
        } catch (Exception e) {
            logStatus(Log.ERROR, "unsubscribe", toStatus(e), Key.TOPIC, stringify(topic), Key.SUBSCRIBER, stringify(subscriber));
        }
    }

    public void unsubscribeAllFromPackage(@NonNull String packageName) {
        try {
            mDatabaseHelper.getSubscribersFromPackage(packageName).forEach(this::deleteSubscriber);
        } catch (Exception e) {
            logStatus(Log.ERROR, "unsubscribeAllFromPackage", toStatus(e), Key.PACKAGE, packageName);
        }
    }

    public @NonNull FetchSubscriptionsResponse fetchSubscriptions(@NonNull UMessage message) throws UStatusException {
        final FetchSubscriptionsRequest request = unpack(message, FetchSubscriptionsRequest.class)
                .orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, MESSAGE_INVALID_PAYLOAD));
        final FetchSubscriptionsRequest.RequestCase requestCase = request.getRequestCase();
        final List<Subscription> subscriptions;
        if (requestCase == RequestCase.TOPIC) {
            final UUri topic = request.getTopic();
            final State state = mDatabaseHelper.getSubscriptionState(topic);
            subscriptions = mDatabaseHelper.getSubscribersByTopic(topic).stream()
                    .map(it -> buildSubscription(it, state))
                    .toList();
        } else if (requestCase == RequestCase.SUBSCRIBER) {
            subscriptions = mDatabaseHelper.getSubscribersByUri(request.getSubscriber().getUri()).stream()
                    .map(it -> buildSubscription(it, mDatabaseHelper.getSubscriptionState(it.getTopic())))
                    .toList();
        } else {
            throw new UStatusException(UCode.INVALID_ARGUMENT, "Request case not set or unsupported");
        }
        return FetchSubscriptionsResponse.newBuilder()
                .addAllSubscriptions(subscriptions)
                .setHasMoreRecords(false)
                .build();
    }

    public @NonNull FetchSubscribersResponse fetchSubscribers(@NonNull UMessage message) throws UStatusException {
        final FetchSubscribersRequest request = unpack(message, FetchSubscribersRequest.class)
                .orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, MESSAGE_INVALID_PAYLOAD));
        final List<SubscriberInfo> subscribers = mDatabaseHelper.getSubscribersByTopic(request.getTopic()).stream()
                .map(it -> buildSubscriber(it.getSubscriber()))
                .toList();
        return FetchSubscribersResponse.newBuilder()
                .addAllSubscribers(subscribers)
                .setHasMoreRecords(false)
                .build();
    }

    public @NonNull NotificationsResponse registerForNotifications(@NonNull UMessage message) {
        final UUri source = message.getAttributes().getSource();
        final NotificationsRequest request = unpack(message, NotificationsRequest.class)
                .orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, MESSAGE_INVALID_PAYLOAD));
        final UUri topic = request.getTopic();
        checkSameEntity(source, topic);
        if (mDatabaseHelper.addObserver(topic) < 0) {
            throw new UStatusException(UCode.INTERNAL, "Failed to add observer for " + stringify(topic));
        }
        if (VERBOSE) {
            Log.v(TAG, join(Key.MESSAGE, "Observer added", Key.TOPIC, stringify(topic)));
        }
        return NotificationsResponse.getDefaultInstance();
    }

    public @NonNull NotificationsResponse unregisterForNotifications(@NonNull UMessage message) {
        final UUri source = message.getAttributes().getSource();
        final NotificationsRequest request = unpack(message, NotificationsRequest.class)
                .orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, MESSAGE_INVALID_PAYLOAD));
        final UUri topic = request.getTopic();
        checkSameEntity(source, topic);
        if (mDatabaseHelper.deleteObserver(topic) < 0) {
            throw new UStatusException(UCode.INTERNAL, "Failed to delete observer for " + stringify(topic));
        }
        if (VERBOSE) {
            Log.v(TAG, join(Key.MESSAGE, "Observer removed", Key.TOPIC, stringify(topic)));
        }
        return NotificationsResponse.getDefaultInstance();
    }

    private void notifySubscriptionUpdate(@NonNull Set<UUri> sinks, @NonNull Update update) {
        if (DEBUG) {
            Log.d(TAG, join(Key.EVENT, "Notify update", Key.SUBSCRIPTION, stringify(update)));
        }
        sinks.forEach(sink -> mUSubscription.sendSubscriptionUpdate(sink, update));
        mUSubscription.notifySubscriptionChanged(update);
    }

    private @NonNull Optional<UUri> getObserver(@NonNull UUri topic) {
        return mDatabaseHelper.isObserved(topic) ? Optional.of(removeResource(topic)) : Optional.empty();
    }

    public boolean isTopicSubscribed(@NonNull UUri topic) {
        final State state = getSubscriptionState(topic);
        return (state == State.SUBSCRIBED) || (state == State.SUBSCRIBE_PENDING);
    }

    public @NonNull State getSubscriptionState(@NonNull UUri topic) {
        try {
            return mDatabaseHelper.getSubscriptionState(topic);
        } catch (Exception e) {
            logStatus(Log.ERROR, "getSubscriptionState", toStatus(e), Key.TOPIC, stringify(topic));
            return State.UNSUBSCRIBED;
        }
    }

    public @NonNull Set<SubscriptionData> getSubscriptions(@NonNull UUri topic) {
        try {
            return mDatabaseHelper.getSubscribersByTopic(topic).stream()
                    .map(SubscriptionData::new)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            logStatus(Log.ERROR, "getSubscriptions", toStatus(e), Key.TOPIC, stringify(topic));
            return emptySet();
        }
    }

    public @NonNull Set<SubscriptionData> getSubscriptionsWithExpiryTime() {
        try {
            return mDatabaseHelper.getSubscribersWithExpiryTime().stream()
                    .map(SubscriptionData::new)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            logStatus(Log.ERROR, "getSubscriptionsWithExpiryTime", toStatus(e));
            return emptySet();
        }
    }

    public @NonNull List<String> getSubscribedPackages() {
        try {
            return mDatabaseHelper.getSubscribedPackages();
        } catch (Exception e) {
            logStatus(Log.ERROR, "getSubscribedPackages", toStatus(e));
            return emptyList();
        }
    }
}
