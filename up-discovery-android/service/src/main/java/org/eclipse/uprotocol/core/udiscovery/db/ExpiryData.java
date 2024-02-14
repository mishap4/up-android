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

package org.eclipse.uprotocol.core.udiscovery.db;

import java.util.concurrent.ScheduledFuture;

/**
 * The ExpiryData class represents a data structure that holds information about a URI's expiry date and time.
 * It also holds a reference to a ScheduledFuture object that can be used to schedule tasks in a concurrent environment.
 */
public class ExpiryData {
    /**
     * The URI for which the expiry data is being stored.
     */
    public final String mUri;

    /**
     * The date and time at which the URI is set to expire.
     */
    public final String mExpireDateTime;

    /**
     * A ScheduledFuture object that can be used to schedule tasks in a concurrent environment.
     */
    public final ScheduledFuture<?> mFuture;

    public ExpiryData(String Uri, String expireDateTime, ScheduledFuture<?> future) {
        this.mUri = Uri;
        this.mExpireDateTime = expireDateTime;
        this.mFuture = future;
    }
}

