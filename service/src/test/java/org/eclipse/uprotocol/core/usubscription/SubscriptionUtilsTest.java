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

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.protobuf.Timestamp;

import org.eclipse.uprotocol.core.TestBase;
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
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SubscriptionUtilsTest extends TestBase {
    @Test
    public void testCheckSameEntity() {
        SubscriptionUtils.checkSameEntity(RESOURCE_URI, SERVICE_URI);
        assertThrowsStatusException(UCode.PERMISSION_DENIED, () ->
                SubscriptionUtils.checkSameEntity(RESOURCE_URI, SERVICE2_URI));
    }

    @Test
    public void testBuildSubscriber() {
        assertEquals(SubscriberInfo.newBuilder()
                .setUri(CLIENT_URI)
                .build(), SubscriptionUtils.buildSubscriber(CLIENT_URI));
    }

    @Test
    public void testBuildUpdate() {
        final SubscribeAttributes attributes = SubscribeAttributes.newBuilder()
                .setExpire(fromMillis(EXPIRY_TIME))
                .setSamplePeriodMs(PERIOD)
                .build();
        final SubscriptionStatus status = SubscriptionStatus.newBuilder()
                .setState(SubscriptionStatus.State.SUBSCRIBED)
                .setMessage(MESSAGE)
                .build();
        assertEquals(Update.newBuilder()
                .setTopic(RESOURCE_URI)
                .setSubscriber(SubscriberInfo.newBuilder().setUri(CLIENT_URI))
                .setAttributes(attributes)
                .setStatus(status)
                .build(), SubscriptionUtils.buildUpdate(RESOURCE_URI, CLIENT_URI, attributes, status));
    }

    @Test
    public void testBuildSubscriptionStatus() {
        assertEquals(SubscriptionStatus.newBuilder()
                .setState(State.SUBSCRIBED)
                .build(), SubscriptionUtils.buildSubscriptionStatus(State.SUBSCRIBED));
    }

    @Test
    public void testBuildSubscribeAttributes() {
        assertEquals(SubscribeAttributes.getDefaultInstance(),
                SubscriptionUtils.buildSubscribeAttributes(new SubscriberRecord(RESOURCE_URI, CLIENT_URI)));
        assertEquals(SubscribeAttributes.newBuilder()
                .setExpire(fromMillis(EXPIRY_TIME))
                .build(), SubscriptionUtils.buildSubscribeAttributes(
                        new SubscriberRecord(RESOURCE_URI, CLIENT_URI, EXPIRY_TIME)));
        assertEquals(SubscribeAttributes.newBuilder()
                .setSamplePeriodMs(PERIOD)
                .build(), SubscriptionUtils.buildSubscribeAttributes(
                        new SubscriberRecord(RESOURCE_URI, CLIENT_URI, 0, PERIOD)));
        assertEquals(SubscribeAttributes.newBuilder()
                .setExpire(fromMillis(EXPIRY_TIME))
                .setSamplePeriodMs(PERIOD)
                .build(), SubscriptionUtils.buildSubscribeAttributes(
                        new SubscriberRecord(RESOURCE_URI, CLIENT_URI, EXPIRY_TIME, PERIOD)));
    }

    @Test
    public void testBuildSubscription() {
        final Subscription.Builder builder = Subscription.newBuilder()
                .setTopic(RESOURCE_URI)
                .setSubscriber(SubscriberInfo.newBuilder().setUri(CLIENT_URI))
                .setStatus(SubscriptionStatus.newBuilder().setState(State.SUBSCRIBED));
        assertEquals(builder.build(), SubscriptionUtils.buildSubscription(
                        new SubscriberRecord(RESOURCE_URI, CLIENT_URI), State.SUBSCRIBED));
        assertEquals(builder.setAttributes(SubscribeAttributes.newBuilder()
                        .setExpire(fromMillis(EXPIRY_TIME)))
                .build(), SubscriptionUtils.buildSubscription(
                        new SubscriberRecord(RESOURCE_URI, CLIENT_URI, EXPIRY_TIME), State.SUBSCRIBED));
        assertEquals(builder.setAttributes(SubscribeAttributes.newBuilder()
                        .setSamplePeriodMs(PERIOD))
                .build(), SubscriptionUtils.buildSubscription(
                        new SubscriberRecord(RESOURCE_URI, CLIENT_URI, 0, PERIOD), State.SUBSCRIBED));
        assertEquals(builder.setAttributes(SubscribeAttributes.newBuilder()
                        .setExpire(fromMillis(EXPIRY_TIME))
                        .setSamplePeriodMs(PERIOD))
                .build(), SubscriptionUtils.buildSubscription(
                        new SubscriberRecord(RESOURCE_URI, CLIENT_URI, EXPIRY_TIME, PERIOD), State.SUBSCRIBED));
    }

    @Test
    public void testBuildRequestDataWithSubscribeRequest() {
        final SubscriptionRequest request = SubscriptionRequest.newBuilder()
                .setTopic(RESOURCE_URI_REMOTE)
                .setSubscriber(SubscriberInfo.newBuilder().setUri(CLIENT_URI))
                .setAttributes(SubscribeAttributes.newBuilder()
                        .setExpire(fromMillis(EXPIRY_TIME))
                        .setSamplePeriodMs(PERIOD))
                .build();
        assertEquals(new RequestData(RESOURCE_URI_REMOTE, CLIENT_URI, request.getAttributes()),
                SubscriptionUtils.buildRequestData(request, false));
        assertEquals(new RequestData(RESOURCE_URI, CLIENT_URI, request.getAttributes()),
                SubscriptionUtils.buildRequestData(request, true));
    }

    @Test
    public void testBuildRequestDataWithUnsubscribeRequest() {
        final UnsubscribeRequest request = UnsubscribeRequest.newBuilder()
                .setTopic(RESOURCE_URI_REMOTE)
                .setSubscriber(SubscriberInfo.newBuilder().setUri(CLIENT_URI))
                .build();
        assertEquals(new RequestData(RESOURCE_URI_REMOTE, CLIENT_URI, SubscribeAttributes.getDefaultInstance()),
                SubscriptionUtils.buildRequestData(request, false));
        assertEquals(new RequestData(RESOURCE_URI, CLIENT_URI, SubscribeAttributes.getDefaultInstance()),
                SubscriptionUtils.buildRequestData(request, true));
    }

    @Test
    public void testGetExpiryTime() {
        assertEquals(0, SubscriptionUtils.getExpiryTime(SubscribeAttributes.getDefaultInstance()));
        assertEquals(EXPIRY_TIME, SubscriptionUtils.getExpiryTime(SubscribeAttributes.newBuilder()
                .setExpire(fromMillis(EXPIRY_TIME))
                .build()));
    }

    @Test
    public void testGetExpiryTimeFailure() {
        assertEquals(0, SubscriptionUtils.getExpiryTime(
                SubscribeAttributes.newBuilder().setExpire(Timestamp.newBuilder().setNanos(-1)).build()));
    }
}
