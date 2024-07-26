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

import androidx.room.Entity
import org.eclipse.uprotocol.common.util.log.Formatter.joinGrouped
import org.eclipse.uprotocol.common.util.log.Formatter.stringify
import org.eclipse.uprotocol.common.util.log.Key
import org.eclipse.uprotocol.v1.UUID
import org.eclipse.uprotocol.v1.UUri

@Entity(tableName = SubscriberRecord.TABLE_NAME, primaryKeys = ["topic", "subscriber"])
data class SubscriberRecord @JvmOverloads constructor(
    val topic: UUri,
    val subscriber: UUri,
    val expiryTime: Long = 0,
    val samplingPeriod: Int = 0,
    val packageName: String = "",
    val requestId: UUID = UUID.getDefaultInstance()
) {
    companion object {
        const val TABLE_NAME = "subscribers"
    }

    override fun toString(): String {
        return joinGrouped(Key.TOPIC, stringify(topic), Key.SUBSCRIBER, stringify(subscriber),
            Key.EXPIRY, expiryTime, Key.PERIOD, samplingPeriod, Key.PACKAGE, packageName, Key.REQUEST_ID, stringify(requestId))
    }
}
