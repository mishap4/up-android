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
 * SPDX-FileCopyrightText: 2024 General Motors GTO LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.uprotocol.core.udiscovery.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import org.eclipse.uprotocol.common.util.log.Formatter.join
import org.eclipse.uprotocol.common.util.log.Key

import org.eclipse.uprotocol.core.udiscovery.UDiscoveryService.TAG

/**
 * This is an abstract class that extends RoomDatabase. Room is a persistence library, part of the Android Jetpack.
 * Room provides an abstraction layer over SQLite to allow fluent database access while harnessing the full power of SQLite.
 *
 * Classes annotated with @Database should extend this class and list all entities associated with that database.
 * The class should also be an abstract class and include an abstract method with no parameters and returns the class that is annotated with @Dao.
 *
 * In this case, ObserverDatabase provides a handle to the underlying SQLite database and serves as the main access point for the underlying connection.
 * It includes a method to access ObserverDao which is used to manage database operations related to the Observer entity.
 */

@Database(
    entities = [Observer::class],
    version = 1,
    exportSchema = true
)
abstract class ObserverDatabase : RoomDatabase() {

    abstract fun observerDao(): ObserverDao

    companion object {
        @Volatile
        private var INSTANCE: ObserverDatabase? = null

        fun createDatabase(appContext: Context): ObserverDatabase {
            Log.i(TAG, join(Key.EVENT, "createDatabase"))

            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    appContext,
                    ObserverDatabase::class.java,
                    "Observer.db"
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

fun createDbExtension(context: Context): ObserverDatabase {
    Log.i(TAG, join(Key.EVENT, "createDbExtension"))
    return ObserverDatabase.createDatabase(context)
}
