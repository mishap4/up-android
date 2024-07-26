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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.communication.UStatusException;
import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.core.UCore;
import org.eclipse.uprotocol.core.ubus.client.BindingClient;
import org.eclipse.uprotocol.core.ubus.client.Client;
import org.eclipse.uprotocol.core.ubus.client.ClientManager;
import org.eclipse.uprotocol.transport.UListener;
import org.eclipse.uprotocol.transport.builder.UMessageBuilder;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class UBusTest extends TestBase {
    private final IBinder mClientToken = new Binder();
    private final Client mClient = mock(BindingClient.class);
    private final ClientManager mClientManager = mock(ClientManager.class);
    private final Dispatcher mDispatcher = mock(Dispatcher.class);
    private final ThrottlingMonitor mThrottlingMonitor = mock(ThrottlingMonitor.class);
    private UBus mUBus;
    private UCore mUCore;

    @Before
    public void setUp() {
        setLogLevel(Log.VERBOSE);
        final Context context = mock(Context.class);
        mUBus = new UBus(context, mClientManager, mDispatcher, mThrottlingMonitor);
        mUCore = newMockUCoreBuilder(context).setUBus(mUBus).build();
        doReturn(PACKAGE_NAME).when(context).getPackageName();
        doReturn(mClient).when(mClientManager).getClientOrThrow(mClientToken);
        doReturn(STATUS_OK).when(mDispatcher).dispatchFrom(any(), any());
        mUCore.init();
    }

    @After
    public void tearDown() {
        mUCore.shutdown();
    }

    private static void setLogLevel(int level) {
        UBus.Component.DEBUG = (level <= Log.DEBUG);
        UBus.Component.VERBOSE = (level <= Log.VERBOSE);
    }

    @Test
    public void testInit() {
        mUBus.getComponents().forEach(component -> verify(component, times(1)).init(argThat(components -> {
            assertEquals(mClientManager, components.getClientManager());
            assertEquals(mDispatcher, components.getDispatcher());
            assertNotNull(components.getThrottlingMonitor());
            return true;
        })));
    }

    @Test
    public void testStartup() {
        mUBus.startup();
        mUBus.getComponents().forEach(component -> verify(component, times(1)).startup());
    }

    @Test
    public void testShutdown() {
        mUBus.shutdown();
        mUBus.getComponents().forEach(component -> verify(component, times(1)).shutdown());
    }

    @Test
    public void testClearCache() {
        mUBus.clearCache();
        mUBus.getComponents().forEach(component -> verify(component, times(1)).clearCache());
    }

    @Test
    public void testGetComponents() {
        final List<UBus.Component> components = mUBus.getComponents();
        assertTrue(components.contains(mClientManager));
        assertTrue(components.contains(mDispatcher));
    }

    @Test
    public void testRegisterClientBinding() {
        final MockListener listener = new MockListener();
        doReturn(STATUS_OK).when(mClientManager).registerClient(PACKAGE_NAME, CLIENT2_URI, mClientToken, listener);
        assertEquals(STATUS_OK, mUBus.registerClient(PACKAGE_NAME, CLIENT2_URI, mClientToken, listener));
    }

    @Test
    public void testRegisterClientInternal() {
        final UListener listener = mock(UListener.class);
        doReturn(STATUS_OK).when(mClientManager).registerClient(PACKAGE_NAME, CLIENT2_URI, mClientToken, listener);
        assertEquals(STATUS_OK, mUBus.registerClient(CLIENT2_URI, mClientToken, listener));
    }

    @Test
    public void testUnregisterClient() {
        doReturn(STATUS_OK).when(mClientManager).unregisterClient(mClientToken);
        assertEquals(STATUS_OK, mUBus.unregisterClient(mClientToken));
    }

    @Test
    public void testSendInvalidMessage() {
        assertStatus(UCode.INVALID_ARGUMENT, mUBus.send(EMPTY_MESSAGE, mClientToken));
    }

    @Test
    public void testSendNotRegisteredClient() {
        final UStatus status = buildStatus(UCode.UNAUTHENTICATED);
        doThrow(new UStatusException(status)).when(mClientManager).getClientOrThrow(mClientToken);
        assertEquals(status, mUBus.send(UMessageBuilder.publish(RESOURCE_URI).build(), mClientToken));
    }

    @Test
    public void testSendDispatcherFailure() {
        final UStatus status = buildStatus(UCode.UNAUTHENTICATED);
        doReturn(status).when(mDispatcher).dispatchFrom(any(), eq(mClient));
        assertEquals(status, mUBus.send(UMessageBuilder.publish(RESOURCE_URI).build(), mClientToken));
    }

    @Test
    public void testSendFailure() {
        final UStatus status = buildStatus(UCode.UNKNOWN);
        doThrow(new UStatusException(status)).when(mDispatcher).dispatchFrom(any(), eq(mClient));
        assertEquals(status, mUBus.send(UMessageBuilder.publish(RESOURCE_URI).build(), mClientToken));
    }

    @Test
    public void testSendPublishMessage() {
        setLogLevel(Log.INFO);
        final UStatus status = STATUS_OK;
        doReturn(status).when(mDispatcher).dispatchFrom(any(), eq(mClient));
        assertEquals(status, mUBus.send(UMessageBuilder.publish(RESOURCE_URI).build(), mClientToken));
    }

    @Test
    public void testSendNotificationMessage() {
        final UStatus status = STATUS_OK;
        doReturn(status).when(mDispatcher).dispatchFrom(any(), eq(mClient));
        assertEquals(status, mUBus.send(UMessageBuilder.notification(RESOURCE_URI, CLIENT2_URI).build(), mClientToken));
    }

    @Test
    public void testSendRequestMessage() {
        final UStatus status = STATUS_OK;
        doReturn(status).when(mDispatcher).dispatchFrom(any(), eq(mClient));
        assertEquals(status, mUBus.send(UMessageBuilder.request(CLIENT_URI, METHOD_URI, TTL).build(), mClientToken));
    }

    @Test
    public void testSendResponseMessage() {
        final UStatus status = STATUS_OK;
        doReturn(status).when(mDispatcher).dispatchFrom(any(), eq(mClient));
        assertEquals(status, mUBus.send(UMessageBuilder.response(METHOD_URI, CLIENT_URI, ID).build(), mClientToken));
    }

    @Test
    public void testEnableDispatchingNotRegisteredClient() {
        final UStatus status = buildStatus(UCode.UNAUTHENTICATED);
        doThrow(new UStatusException(status)).when(mClientManager).getClientOrThrow(mClientToken);
        assertEquals(status, mUBus.enableDispatching(RESOURCE_FILTER, mClientToken));
    }

    @Test
    public void testEnableDispatchingFailure() {
        final UStatus status = buildStatus(UCode.UNKNOWN);
        doThrow(new UStatusException(status)).when(mDispatcher).enableDispatching(RESOURCE_FILTER, mClient);
        assertEquals(status, mUBus.enableDispatching(RESOURCE_FILTER, mClientToken));
    }

    @Test
    public void testEnableDispatching() {
        final UStatus status = STATUS_OK;
        doReturn(status).when(mDispatcher).enableDispatching(RESOURCE_FILTER, mClient);
        assertEquals(status, mUBus.enableDispatching(RESOURCE_FILTER, mClientToken));
        setLogLevel(Log.INFO);
        assertEquals(status, mUBus.enableDispatching(RESOURCE_FILTER, mClientToken));
    }

    @Test
    public void testDisableDispatchingNotRegisteredClient() {
        final UStatus status = buildStatus(UCode.UNAUTHENTICATED);
        doThrow(new UStatusException(status)).when(mClientManager).getClientOrThrow(mClientToken);
        assertEquals(status, mUBus.disableDispatching(RESOURCE_FILTER, mClientToken));
    }

    @Test
    public void testDisableDispatchingFailure() {
        final UStatus status = buildStatus(UCode.UNKNOWN);
        doThrow(new UStatusException(status)).when(mDispatcher).disableDispatching(RESOURCE_FILTER, mClient);
        assertEquals(status, mUBus.disableDispatching(RESOURCE_FILTER, mClientToken));
    }

    @Test
    public void testDisableDispatching() {
        final UStatus status = STATUS_OK;
        doReturn(status).when(mDispatcher).disableDispatching(RESOURCE_FILTER, mClient);
        assertEquals(status, mUBus.disableDispatching(RESOURCE_FILTER, mClientToken));
        setLogLevel(Log.INFO);
        assertEquals(status, mUBus.disableDispatching(RESOURCE_FILTER, mClientToken));
    }

    @Test
    public void testGetCallerCredentials() {
        doReturn(mClient).when(mDispatcher).getCaller(ID);
        assertEquals(mClient.getCredentials(), mUBus.getCallerCredentials(ID));
    }

    @Test
    public void testGetCallerCredentialsFailure() {
        assertThrowsStatusException(UCode.UNAVAILABLE, () -> mUBus.getCallerCredentials(ID));
    }

    @Test
    public void testDump() {
        final PrintWriter writer = new PrintWriter(new StringWriter());
        final String[] args = {};
        mUBus.dump(writer, args);
        verify(mDispatcher, times(1)).dump(writer, args);
    }

    @Test
    public void testComponentDefaultImplementation() {
        final UBus.Component component = new UBus.Component() {};
        assertNotNull(component);
        component.init(mock(UBus.Components.class));
        component.startup();
        component.shutdown();
        component.clearCache();
    }

    @Test
    public void testDebugOrError() {
        assertEquals(Log.DEBUG, UBus.Component.debugOrError(STATUS_OK));
        assertEquals(Log.ERROR, UBus.Component.debugOrError(buildStatus(UCode.INVALID_ARGUMENT)));
    }

    @Test
    public void testVerboseOrError() {
        assertEquals(Log.VERBOSE, UBus.Component.verboseOrError(STATUS_OK));
        assertEquals(Log.ERROR, UBus.Component.verboseOrError(buildStatus(UCode.INVALID_ARGUMENT)));
    }
}
