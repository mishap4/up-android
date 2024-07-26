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
import static com.google.protobuf.util.Timestamps.toMillis;

import static org.eclipse.uprotocol.common.util.UStatusUtils.checkArgument;
import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.common.util.log.Formatter.status;
import static org.eclipse.uprotocol.common.util.log.Formatter.stringify;
import static org.eclipse.uprotocol.core.internal.util.UUriUtils.removeAuthority;
import static org.eclipse.uprotocol.core.usubscription.USubscription.TAG;

import android.util.Log;

import androidx.annotation.NonNull;

import org.eclipse.uprotocol.core.usubscription.database.SubscriberRecord;
import org.eclipse.uprotocol.core.usubscription.v3.SubscribeAttributes;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriberInfo;
import org.eclipse.uprotocol.core.usubscription.v3.Subscription;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionRequest;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus.State;
import org.eclipse.uprotocol.core.usubscription.v3.UnsubscribeRequest;
import org.eclipse.uprotocol.core.usubscription.v3.Update;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UUri;

public class SubscriptionUtils {
    private SubscriptionUtils() {}

    public static void checkSameEntity(@NonNull UUri uri1, @NonNull UUri uri2) {
        checkArgument(uri1.getUeId() == uri2.getUeId(), UCode.PERMISSION_DENIED,
                stringify(uri1) + " doesn't match to " + stringify(uri2));
    }

    public static @NonNull SubscriberInfo buildSubscriber(@NonNull UUri subscriber) {
        return SubscriberInfo.newBuilder()
                .setUri(subscriber)
                .build();
    }

    public static @NonNull Update buildUpdate(@NonNull UUri topic, @NonNull UUri subscriber,
            @NonNull SubscribeAttributes attributes, @NonNull SubscriptionStatus status) {
        return Update.newBuilder()
                .setTopic(topic)
                .setSubscriber(buildSubscriber(subscriber))
                .setAttributes(attributes)
                .setStatus(status)
                .build();
    }

    public static @NonNull SubscriptionStatus buildSubscriptionStatus(@NonNull State state) {
        return SubscriptionStatus.newBuilder()
                .setState(state)
                .build();
    }

    public static @NonNull SubscribeAttributes buildSubscribeAttributes(@NonNull SubscriberRecord subscriberRecord) {
        final SubscribeAttributes.Builder builder = SubscribeAttributes.newBuilder();
        if (subscriberRecord.getExpiryTime() > 0) {
            builder.setExpire(fromMillis(subscriberRecord.getExpiryTime()));
        }
        if (subscriberRecord.getSamplingPeriod() > 0) {
            builder.setSamplePeriodMs(subscriberRecord.getSamplingPeriod());
        }
        return builder.build();
    }

    public static @NonNull Subscription buildSubscription(@NonNull SubscriberRecord subscriberRecord, @NonNull State state) {
        final Subscription.Builder builder = Subscription.newBuilder()
                .setTopic(subscriberRecord.getTopic())
                .setSubscriber(buildSubscriber(subscriberRecord.getSubscriber()))
                .setStatus(buildSubscriptionStatus(state));
        final SubscribeAttributes attributes = buildSubscribeAttributes(subscriberRecord);
        if (attributes.hasExpire() || attributes.hasSamplePeriodMs()) {
            builder.setAttributes(attributes);
        }
        return builder.build();
    }

    public static @NonNull RequestData buildRequestData(@NonNull SubscriptionRequest request, boolean removeTopicAuthority) {
        final UUri topic = request.getTopic();
        final UUri subscriber = request.getSubscriber().getUri();
        if (removeTopicAuthority) {
            return new RequestData(removeAuthority(topic), subscriber, request.getAttributes());
        } else {
            return new RequestData(request);
        }
    }

    public static @NonNull RequestData buildRequestData(@NonNull UnsubscribeRequest request, boolean removeTopicAuthority) {
        final UUri topic = request.getTopic();
        final UUri subscriber = request.getSubscriber().getUri();
        if (removeTopicAuthority) {
            return new RequestData(removeAuthority(topic), subscriber, SubscribeAttributes.getDefaultInstance());
        } else {
            return new RequestData(request);
        }
    }

    public static long getExpiryTime(@NonNull SubscribeAttributes attributes) { // in milliseconds
        try {
            return attributes.hasExpire() ? toMillis(attributes.getExpire()) : 0;
        } catch (Exception e) {
            Log.e(TAG, status("getExpiryTime", toStatus(e)));
            return 0;
        }
    }
}
