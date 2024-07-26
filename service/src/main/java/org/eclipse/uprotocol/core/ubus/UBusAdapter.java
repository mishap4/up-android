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
package org.eclipse.uprotocol.core.ubus;

import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;

import android.os.IBinder;

import androidx.annotation.NonNull;

import org.eclipse.uprotocol.uri.validator.UriFilter;
import org.eclipse.uprotocol.v1.internal.ParcelableUMessage;
import org.eclipse.uprotocol.v1.internal.ParcelableUStatus;
import org.eclipse.uprotocol.v1.internal.ParcelableUUri;

public class UBusAdapter extends IUBus.Stub {
    private final UBus mUBus;

    public UBusAdapter(@NonNull UBus uBus) {
        mUBus = uBus;
    }

    @Override
    public ParcelableUStatus registerClient(String packageName, ParcelableUUri clientUri, IBinder clientToken, int flags, IUListener listener) {
        try {
            return new ParcelableUStatus(mUBus.registerClient(packageName, clientUri.getWrapped(), clientToken, listener));
        } catch (Exception e) {
            return new ParcelableUStatus(toStatus(e));
        }
    }

    @Override
    public ParcelableUStatus unregisterClient(IBinder clientToken) {
        try {
            return new ParcelableUStatus(mUBus.unregisterClient(clientToken));
        } catch (Exception e) {
            return new ParcelableUStatus(toStatus(e));
        }
    }

    @Override
    public ParcelableUStatus send(ParcelableUMessage message, IBinder clientToken) {
        try {
            return new ParcelableUStatus(mUBus.send(message.getWrapped(), clientToken));
        } catch (Exception e) {
            return new ParcelableUStatus(toStatus(e));
        }
    }

    @Override
    public ParcelableUStatus enableDispatching(ParcelableUUri sourceFilter, ParcelableUUri sinkFilter, int flags, IBinder clientToken) {
        try {
            final UriFilter filter = new UriFilter(sourceFilter.getWrapped(), sinkFilter.getWrapped());
            return new ParcelableUStatus(mUBus.enableDispatching(filter, clientToken));
        } catch (Exception e) {
            return new ParcelableUStatus(toStatus(e));
        }
    }

    @Override
    public ParcelableUStatus disableDispatching(ParcelableUUri sourceFilter, ParcelableUUri sinkFilter, int flags, IBinder clientToken) {
        try {
            final UriFilter filter = new UriFilter(sourceFilter.getWrapped(), sinkFilter.getWrapped());
            return new ParcelableUStatus(mUBus.disableDispatching(filter, clientToken));
        } catch (Exception e) {
            return new ParcelableUStatus(toStatus(e));
        }
    }
}
