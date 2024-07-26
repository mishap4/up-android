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

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.eclipse.uprotocol.common.util.log.Formatter
import org.eclipse.uprotocol.common.util.log.Key
import org.eclipse.uprotocol.core.usubscription.USubscription

@Database(
    entities = [ObserverRecord::class, SubscriberRecord::class, SubscriptionRecord::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(DatabaseConverters::class)
abstract class SubscriptionDatabase : RoomDatabase() {
    abstract fun getSubscribersDao(): SubscribersDao
    abstract fun getSubscriptionsDao(): SubscriptionsDao
    abstract fun getObserversDao(): ObserversDao

    companion object {
        @JvmField val TAG: String = Formatter.tag(USubscription.NAME)
        const val NAME: String = "subscriptions.db"

        @Volatile
        private var INSTANCE: SubscriptionDatabase? = null

        fun createDatabase(appContext: Context): SubscriptionDatabase {
            Log.i(TAG, Formatter.join(Key.EVENT, "Database created"))
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    appContext,
                    SubscriptionDatabase::class.java,
                    NAME
                )
                    .allowMainThreadQueries()
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

fun createDbExtension(context: Context): SubscriptionDatabase {
    return SubscriptionDatabase.createDatabase(context)
}
