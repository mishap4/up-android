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

import androidx.room.TypeConverter
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus.State
import org.eclipse.uprotocol.uri.serializer.UriSerializer
import org.eclipse.uprotocol.uuid.serializer.UuidSerializer
import org.eclipse.uprotocol.v1.UUID
import org.eclipse.uprotocol.v1.UUri

object DatabaseConverters {
    @TypeConverter
    fun toUri(value: String): UUri {
        return UriSerializer.deserialize(value)
    }

    @TypeConverter
    fun fromUri(uri: UUri): String {
        return UriSerializer.serialize(uri)
    }

    @TypeConverter
    fun toUuid(value: String): UUID {
        return UuidSerializer.deserialize(value)
    }

    @TypeConverter
    fun fromUuid(id: UUID): String {
        return UuidSerializer.serialize(id)
    }

    @TypeConverter
    fun toState(value: Int): State {
        val state = State.forNumber(value)
        return if ((state != null)) state else State.UNSUBSCRIBED
    }

    @TypeConverter
    fun fromState(state: State): Int {
        return state.number
    }
}
