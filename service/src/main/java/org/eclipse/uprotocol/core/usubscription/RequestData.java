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

import static org.eclipse.uprotocol.core.usubscription.SubscriptionUtils.buildSubscribeAttributes;

import androidx.annotation.NonNull;

import org.eclipse.uprotocol.core.usubscription.database.SubscriberRecord;
import org.eclipse.uprotocol.core.usubscription.v3.SubscribeAttributes;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionRequest;
import org.eclipse.uprotocol.core.usubscription.v3.UnsubscribeRequest;
import org.eclipse.uprotocol.v1.UUri;

public record RequestData(@NonNull UUri topic, @NonNull UUri subscriber, @NonNull SubscribeAttributes attributes) {
    public RequestData(@NonNull UUri topic, @NonNull UUri subscriber) {
        this(topic, subscriber, SubscribeAttributes.getDefaultInstance());
    }

    public RequestData(@NonNull SubscriptionRequest request) {
        this(request.getTopic(), request.getSubscriber().getUri(), request.getAttributes());
    }

    public RequestData(@NonNull UnsubscribeRequest request) {
        this(request.getTopic(), request.getSubscriber().getUri(), SubscribeAttributes.getDefaultInstance());
    }

    public RequestData(@NonNull SubscriberRecord subscriberRecord) {
        this(subscriberRecord.getTopic(), subscriberRecord.getSubscriber(), buildSubscribeAttributes(subscriberRecord));
    }
}
