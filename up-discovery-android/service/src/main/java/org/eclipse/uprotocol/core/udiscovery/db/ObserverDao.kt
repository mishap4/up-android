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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * `ObserverDao` is an interface that defines the operations that can be performed on the Observer database table.
 * It uses Room persistence library to provide an abstraction layer over SQLite.
 *
 * The following operations are defined:
 * - `addObserver(Observer: Observer): Long`: This function is used to add an Observer to the database. If an Observer with the same primary key already exists, it is replaced.
 * - `removeObserver(nodeUri: String, observer: String): Int`: This function is used to remove an Observer from the database based on the provided `nodeUri` and `observer`.
 * - `getNodeUrisList(): List<String>`: This function is used to retrieve a list of all node URIs in the Observer table.
 * - `getObserverList(nodeUri: String): List<String>`: This function is used to retrieve a list of all Observers for a specific node URI.
 */
@Dao
interface ObserverDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addObserver(Observer: Observer): Long

    @Query("DELETE FROM Observer WHERE nodeUri = :nodeUri AND observer = :observer")
    fun removeObserver(nodeUri: String, observer: String): Int

    @Query("SELECT nodeUri FROM Observer")
    fun getNodeUrisList(): List<String>

    @Query("SELECT observer FROM Observer where nodeUri = :nodeUri")
    fun getObserverList(nodeUri: String): List<String>
}
