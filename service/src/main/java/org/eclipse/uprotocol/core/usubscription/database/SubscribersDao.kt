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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import org.eclipse.uprotocol.v1.UUri

@Dao
interface SubscribersDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun addSubscriber(record: SubscriberRecord): Long

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun updateSubscriber(record: SubscriberRecord): Int

    @Query("DELETE FROM subscribers WHERE topic = :topic AND subscriber = :subscriber")
    fun deleteSubscriber(topic: UUri, subscriber: UUri): Int

    @Query("DELETE FROM subscribers WHERE topic = :topic")
    fun deleteSubscribers(topic: UUri): Int

    @Query("SELECT COUNT(*) FROM subscribers WHERE topic = :topic")
    fun getSubscribersCount(topic: UUri): Int

    @Query("SELECT * FROM subscribers")
    fun getAllSubscribers(): List<SubscriberRecord>

    @Query("SELECT * FROM subscribers WHERE topic = :topic AND subscriber = :subscriber")
    fun getSubscriber(topic: UUri, subscriber: UUri): SubscriberRecord?

    @Query("SELECT * FROM subscribers WHERE topic = :topic ORDER BY topic LIMIT 1")
    fun getFirstSubscriberForTopic(topic: UUri): SubscriberRecord?

    @Query("SELECT * FROM subscribers WHERE expiryTime > 0")
    fun getSubscribersWithExpiryTime(): List<SubscriberRecord>

    @Query("SELECT * FROM subscribers WHERE packageName = :packageName")
    fun getSubscribersFromPackage(packageName: String): List<SubscriberRecord>

    @Query("SELECT DISTINCT packageName FROM subscribers WHERE packageName <> ''")
    fun getSubscribedPackages(): List<String>

    @Query("SELECT * FROM subscribers WHERE topic = :topic")
    fun getSubscribersByTopic(topic: UUri): List<SubscriberRecord>

    @Query("SELECT * FROM subscribers WHERE subscriber = :subscriber")
    fun getSubscribersByUri(subscriber: UUri): List<SubscriberRecord>
}
