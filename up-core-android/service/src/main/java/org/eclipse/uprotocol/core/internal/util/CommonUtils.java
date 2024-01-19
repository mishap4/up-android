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
package org.eclipse.uprotocol.core.internal.util;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Set;

public interface CommonUtils {
    String[] EMPTY_STRING_ARRAY = new String[0];

    static @NonNull <T> List<T> emptyIfNull(@Nullable List<T> list) {
        return (list == null) ? emptyList() : list;
    }

    static @NonNull <T> Set<T> emptyIfNull(@Nullable Set<T> list) {
        return (list == null) ? emptySet() : list;
    }

    static @NonNull String emptyIfNull(@Nullable String string) {
        return (string == null) ? "" : string;
    }

    static @NonNull String[] emptyIfNull(@Nullable String[] array) {
        return (array == null) ? EMPTY_STRING_ARRAY : array;
    }
}
