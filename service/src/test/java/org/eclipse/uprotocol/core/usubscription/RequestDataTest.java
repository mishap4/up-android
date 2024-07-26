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

import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.core.usubscription.database.SubscriberRecord;
import org.eclipse.uprotocol.core.usubscription.v3.SubscribeAttributes;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriberInfo;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionRequest;
import org.eclipse.uprotocol.core.usubscription.v3.UnsubscribeRequest;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RequestDataTest  extends TestBase {
    private static final SubscribeAttributes ATTRIBUTES = SubscribeAttributes.newBuilder()
            .setExpire(fromMillis(EXPIRY_TIME))
            .setSamplePeriodMs(PERIOD)
            .build();
    private static final RequestData DATA = new RequestData(RESOURCE_URI, CLIENT_URI, ATTRIBUTES);
    private static final RequestData DATA2 = new RequestData(RESOURCE_URI, CLIENT_URI);

    @Test
    public void testConstruct() {
        assertEquals(RESOURCE_URI, DATA.topic());
        assertEquals(CLIENT_URI, DATA.subscriber());
        assertEquals(ATTRIBUTES, DATA.attributes());
    }

    @Test
    public void testConstructWithSubscriptionRequest() {
        final SubscriptionRequest request = SubscriptionRequest.newBuilder()
                .setTopic(RESOURCE_URI)
                .setSubscriber(SubscriberInfo.newBuilder().setUri(CLIENT_URI))
                .setAttributes(ATTRIBUTES)
                .build();
        assertEquals(DATA, new RequestData(request));
    }

    @Test
    public void testConstructWithUnsubscribeRequest() {
        final UnsubscribeRequest request = UnsubscribeRequest.newBuilder()
                .setTopic(RESOURCE_URI)
                .setSubscriber(SubscriberInfo.newBuilder().setUri(CLIENT_URI))
                .build();
        assertEquals(DATA2, new RequestData(request));
    }

    @Test
    public void testConstructWithSubscriberRecord() {
        final SubscriberRecord subscriber = new SubscriberRecord(RESOURCE_URI, CLIENT_URI, EXPIRY_TIME, PERIOD);
        assertEquals(DATA, new RequestData(subscriber));
    }
}
