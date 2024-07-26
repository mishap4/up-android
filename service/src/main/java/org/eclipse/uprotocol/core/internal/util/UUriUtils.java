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

import static org.eclipse.uprotocol.common.util.UStatusUtils.checkArgument;
import static org.eclipse.uprotocol.uri.validator.UriValidator.isRpcMethod;
import static org.eclipse.uprotocol.uri.validator.UriValidator.isRpcResponse;
import static org.eclipse.uprotocol.uri.validator.UriValidator.isTopic;
import static org.eclipse.uprotocol.uri.validator.UriValidator.matchesAuthority;
import static org.eclipse.uprotocol.uri.validator.UriValidator.matchesEntity;

import androidx.annotation.NonNull;

import org.eclipse.uprotocol.v1.UUri;

public interface UUriUtils {
    static @NonNull UUri checkTopicUriValid(@NonNull UUri uri) {
        checkArgument(isTopic(uri), "Invalid topic URI");
        return uri;
    }

    static @NonNull UUri checkMethodUriValid(@NonNull UUri uri) {
        checkArgument(isRpcMethod(uri), "Invalid method URI");
        return uri;
    }

    static @NonNull UUri checkResponseUriValid(@NonNull UUri uri) {
        checkArgument(isRpcResponse(uri), "Invalid response URI");
        return uri;
    }

    static @NonNull UUri removeResource(@NonNull UUri uri) {
        return UUri.newBuilder(uri).clearResourceId().build();
    }

    static @NonNull UUri addAuthority(@NonNull UUri uri, @NonNull String authority) {
        return UUri.newBuilder(uri).setAuthorityName(authority).build();
    }

    static @NonNull UUri removeAuthority(@NonNull UUri uri) {
        return UUri.newBuilder(uri).clearAuthorityName().build();
    }

    static boolean isSameClient(@NonNull UUri uri1, @NonNull UUri uri2) {
        return matchesAuthority(uri1, uri2) && matchesEntity(uri1, uri2);
    }

    static boolean isLocalUri(@NonNull UUri uri) {
        return uri.getAuthorityName().isBlank();
    }

    static boolean isRemoteUri(@NonNull UUri uri) {
        return !isLocalUri(uri);
    }
}
