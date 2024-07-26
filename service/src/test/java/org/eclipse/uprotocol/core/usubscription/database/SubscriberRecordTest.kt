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

import org.eclipse.uprotocol.common.util.log.Formatter.stringify
import org.eclipse.uprotocol.core.TestBase
import org.eclipse.uprotocol.v1.UUID
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SubscriberRecordTest : TestBase() {
    @Test
    fun testDefault() {
        val subscriber = SubscriberRecord(topic = RESOURCE_URI, subscriber = CLIENT_URI)
        assertEquals(RESOURCE_URI, subscriber.topic)
        assertEquals(CLIENT_URI, subscriber.subscriber)
        assertEquals(0, subscriber.expiryTime)
        assertEquals(0, subscriber.samplingPeriod)
        assertEquals("", subscriber.packageName)
        assertEquals(UUID.getDefaultInstance(), subscriber.requestId)
    }

    @Test
    fun testToString() {
        assertEquals("[topic: /50/1/8000, subscriber: /52/1/0, expiry: 1000, period: 10, package: org.eclipse.uprotocol.core.test, requestId: " +
                stringify(ID) + "]", SubscriberRecord(RESOURCE_URI, CLIENT_URI, 1000, 10, PACKAGE_NAME, ID).toString())
    }
}
