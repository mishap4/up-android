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

import org.eclipse.uprotocol.core.TestBase
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus.State
import org.eclipse.uprotocol.uri.serializer.UriSerializer
import org.eclipse.uprotocol.uuid.serializer.UuidSerializer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DatabaseConvertersTest : TestBase() {
    @Test
    fun testToUri() {
        assertEquals(RESOURCE_URI, DatabaseConverters.toUri(UriSerializer.serialize(RESOURCE_URI)))
    }

    @Test
    fun testFromUri() {
        assertEquals(UriSerializer.serialize(RESOURCE_URI), DatabaseConverters.fromUri(RESOURCE_URI))
    }

    @Test
    fun testToUuid() {
        assertEquals(ID, DatabaseConverters.toUuid(UuidSerializer.serialize(ID)))
    }

    @Test
    fun testFromUuid() {
        assertEquals(UuidSerializer.serialize(ID), DatabaseConverters.fromUuid(ID))
    }

    @Test
    fun testToState() {
        assertEquals(State.SUBSCRIBED, DatabaseConverters.toState(State.SUBSCRIBED.number))
    }

    @Test
    fun testToStateUnrecognized() {
        assertEquals(State.UNSUBSCRIBED, DatabaseConverters.toState(-1))
    }

    @Test
    fun testFromState() {
        assertEquals(State.SUBSCRIBED.number, DatabaseConverters.fromState(State.SUBSCRIBED))
    }
}
