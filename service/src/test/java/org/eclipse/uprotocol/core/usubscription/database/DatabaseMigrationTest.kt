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

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class DatabaseMigrationTest {
    private val allMigrations = emptyArray<Migration>()

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SubscriptionDatabase::class.java
    )

    private fun getDatabase(): SubscriptionDatabase {
        return Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            SubscriptionDatabase::class.java, SubscriptionDatabase.NAME
        )
            .addMigrations(*allMigrations)
            .fallbackToDestructiveMigration()
            .allowMainThreadQueries().build().apply { openHelper.writableDatabase.close() }
    }

    @Test
    @Throws(IOException::class)
    fun testDatabaseCreation() {
        helper.createDatabase(SubscriptionDatabase.NAME, 1).apply {
            close()
        }
        helper.runMigrationsAndValidate(SubscriptionDatabase.NAME, 1, true, *allMigrations).close()

        val database = getDatabase()
        assertTrue(database.getSubscribersDao().getAllSubscribers().isEmpty())
        assertTrue(database.getSubscriptionsDao().getSubscribedTopics().isEmpty())
        assertTrue(database.getObserversDao().getObservedTopics().isEmpty())
        database.close()
    }
}