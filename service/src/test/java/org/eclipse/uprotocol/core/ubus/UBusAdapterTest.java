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

import static org.eclipse.uprotocol.common.util.UStatusUtils.STATUS_OK;
import static org.eclipse.uprotocol.common.util.UStatusUtils.buildStatus;
import static org.eclipse.uprotocol.uri.factory.UriFactory.ANY;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import android.os.Binder;
import android.os.IBinder;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.communication.UStatusException;
import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.transport.builder.UMessageBuilder;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.internal.ParcelableUMessage;
import org.eclipse.uprotocol.v1.internal.ParcelableUStatus;
import org.eclipse.uprotocol.v1.internal.ParcelableUUri;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class UBusAdapterTest extends TestBase {
    private final IBinder mClientToken = new Binder();
    private final MockListener mListener = new MockListener();
    private UBus mUBus;
    private UBusAdapter mUBusAdapter;

    @Before
    public void setUp() {
        mUBus = mock(UBus.class);
        mUBusAdapter = new UBusAdapter(mUBus);
    }

    @Test
    public void testRegisterClient() {
        final UStatus status = STATUS_OK;
        doReturn(status).when(mUBus).registerClient(PACKAGE_NAME, CLIENT_URI, mClientToken, mListener);
        assertEquals(new ParcelableUStatus(status),
                mUBusAdapter.registerClient(PACKAGE_NAME, new ParcelableUUri(CLIENT_URI), mClientToken, 0, mListener));
    }

    @Test
    public void testRegisterClientFailure() {
        final UStatus status = buildStatus(UCode.UNKNOWN);
        doThrow(new UStatusException(status)).when(mUBus).registerClient(PACKAGE_NAME, CLIENT_URI, mClientToken, mListener);
        assertEquals(new ParcelableUStatus(status),
                mUBusAdapter.registerClient(PACKAGE_NAME, new ParcelableUUri(CLIENT_URI), mClientToken, 0, mListener));
    }

    @Test
    public void testUnregisterClient() {
        final UStatus status = STATUS_OK;
        doReturn(status).when(mUBus).unregisterClient(mClientToken);
        assertEquals(new ParcelableUStatus(status), mUBusAdapter.unregisterClient(mClientToken));
    }

    @Test
    public void testUnregisterClientFailure() {
        final UStatus status = buildStatus(UCode.UNKNOWN);
        doThrow(new UStatusException(status)).when(mUBus).unregisterClient(mClientToken);
        assertEquals(new ParcelableUStatus(status), mUBusAdapter.unregisterClient(mClientToken));
    }

    @Test
    public void testSend() {
        final UMessage message = UMessageBuilder.publish(RESOURCE_URI).build();
        final UStatus status = STATUS_OK;
        doReturn(status).when(mUBus).send(message, mClientToken);
        assertEquals(new ParcelableUStatus(status), mUBusAdapter.send(new ParcelableUMessage(message), mClientToken));
    }

    @Test
    public void testSendFailure() {
        final UMessage message = UMessageBuilder.publish(RESOURCE_URI).build();
        final UStatus status = buildStatus(UCode.UNKNOWN);
        doThrow(new UStatusException(status)).when(mUBus).send(message, mClientToken);
        assertEquals(new ParcelableUStatus(status), mUBusAdapter.send(new ParcelableUMessage(message), mClientToken));
    }

    @Test
    public void testEnableDispatchingFailure() {
        final UStatus status = buildStatus(UCode.UNKNOWN);
        doThrow(new UStatusException(status)).when(mUBus).enableDispatching(RESOURCE_FILTER, mClientToken);
        assertEquals(new ParcelableUStatus(status),
                mUBusAdapter.enableDispatching(new ParcelableUUri(RESOURCE_URI), new ParcelableUUri(ANY), 0, mClientToken));
    }

    @Test
    public void testEnableDispatching() {
        final UStatus status = STATUS_OK;
        doReturn(status).when(mUBus).enableDispatching(RESOURCE_FILTER, mClientToken);
        assertEquals(new ParcelableUStatus(status),
                mUBusAdapter.enableDispatching(new ParcelableUUri(RESOURCE_URI), new ParcelableUUri(ANY), 0, mClientToken));
    }

    @Test
    public void testDisableDispatchingFailure() {
        final UStatus status = buildStatus(UCode.UNKNOWN);
        doThrow(new UStatusException(status)).when(mUBus).disableDispatching(RESOURCE_FILTER, mClientToken);
        assertEquals(new ParcelableUStatus(status),
                mUBusAdapter.disableDispatching(new ParcelableUUri(RESOURCE_URI), new ParcelableUUri(ANY), 0, mClientToken));
    }

    @Test
    public void testDisableDispatching() {
        final UStatus status = STATUS_OK;
        doReturn(status).when(mUBus).disableDispatching(RESOURCE_FILTER, mClientToken);
        assertEquals(new ParcelableUStatus(status),
                mUBusAdapter.disableDispatching(new ParcelableUUri(RESOURCE_URI), new ParcelableUUri(ANY), 0, mClientToken));
    }
}
