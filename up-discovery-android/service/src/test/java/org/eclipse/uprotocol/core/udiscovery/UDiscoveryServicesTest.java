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

package org.eclipse.uprotocol.core.udiscovery;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.accounts.AccountManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.google.protobuf.ByteString;

import org.eclipse.uprotocol.ULink;
import org.eclipse.uprotocol.common.util.log.Formatter;
import org.eclipse.uprotocol.core.udiscovery.v3.FindNodePropertiesResponse;
import org.eclipse.uprotocol.core.udiscovery.v3.FindNodesResponse;
import org.eclipse.uprotocol.rpc.URpcListener;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLog;

import io.cloudevents.CloudEvent;

@SuppressWarnings({"java:S1200", "java:S3008", "java:S1134", "java:S2925", "java:S3415",
        "java:S5845"})
@RunWith(RobolectricTestRunner.class)
public class UDiscoveryServicesTest extends TestBase {
    private static final String PROPERTY_SDV_ENABLED = "persist.sys.gm.sdv_enable";
    private static final ByteString mCorruptPayload = ByteString.copyFromUtf8("corrupt payload");
    private static final String TOKEN = "token";
    private static final String LOG_TAG = Formatter.tag("core", UDiscoveryServicesTest.class.getSimpleName());
    private static CloudEvent mLookupUriCloudEvent;
    private static CloudEvent mFindNodeCloudEvent;
    private static CloudEvent mFindNodePropertiesCloudEvent;
    private static CloudEvent mUpdateNodeCloudEvent;
    private static CloudEvent mAddNodesCloudEvent;
    private static CloudEvent mDeleteNodesCloudEvent;
    private static CloudEvent mRegisterCloudEvent;
    private static CloudEvent mUnRegisterCloudEvent;
    private static CloudEvent mUpdatePropertyCloudEvent;
    //private static UriResponse mLookupUriResponse;
    private static FindNodesResponse mFindNodesResponse;
    private static FindNodePropertiesResponse mFindNodePropertiesResponse;
    private final AccountManager mAccountManager = mock(AccountManager.class);
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();
    private UDiscoveryService mService;
   private URpcListener mHandler;

   //private UltifiLink.EventListener mNotificationHandler;
    @Mock
    private ULink mULink;
    @Mock
    private RPCHandler mRpcHandler;
    @Mock
    private ConnectivityManager mConnectivityMgr;
    @Mock
    private LoadUtility mDatabaseLoader;
    private Context mContext;
/*    @Mock
    USubscription.Stub mUSubStub;*/
/*    @Mock
    private UDiscovery.Stub mStub;*/

    private static void setLogLevel(int level) {
        UDiscoveryService.VERBOSE = (level <= Log.VERBOSE);
    }

//    @BeforeClass
//    public static void init() {
//        mLookupUriCloudEvent = buildCloudEvent(LOCAL_LOOKUPURI.uProtocolUri(),
//                Any.pack(UriRequest.getDefaultInstance()));
//
//        mFindNodeCloudEvent = buildCloudEvent(LOCAL_FINDNODEURI.uProtocolUri(),
//                Any.pack(FindNodesRequest.getDefaultInstance()));
//
//        mUpdateNodeCloudEvent = buildCloudEvent(LOCAL_UPDATENODE.uProtocolUri(),
//                Any.getDefaultInstance());
//
//        mFindNodePropertiesCloudEvent = buildCloudEvent(LOCAL_FINDNODEPROPERTY.uProtocolUri(),
//                Any.getDefaultInstance());
//
//        mAddNodesCloudEvent = buildCloudEvent(LOCAL_ADDNODES.uProtocolUri(),
//                Any.getDefaultInstance());
//
//        mDeleteNodesCloudEvent = buildCloudEvent(LOCAL_DELETENODES.uProtocolUri(),
//                Any.getDefaultInstance());
//
//        mRegisterCloudEvent = buildCloudEvent(LOCAL_REGISTER_NOTIFICATION.uProtocolUri(),
//                Any.getDefaultInstance());
//
//        mUnRegisterCloudEvent = buildCloudEvent(LOCAL_UNREGISTER_NOTIFICATION.uProtocolUri(),
//                Any.getDefaultInstance());
//
//        mUpdatePropertyCloudEvent = buildCloudEvent(LOCAL_UPDATEPROPERTY.uProtocolUri(),
//                Any.getDefaultInstance());
//
//        UriResponse.Builder lookupUriRespBld = UriResponse.newBuilder()
//                .addUris("ultifi:/body.cabin_climate/1")
//                .addUris("ultifi:/body.cabin_climate/2")
//                .addUris("ultifi:/body.cabin_climate/3");
//        mLookupUriResponse = lookupUriRespBld.build();
//
//        Node node = jsonToNode(OTA_JSON);
//        mFindNodesResponse = FindNodesResponse.newBuilder().addNodes(node).setStatus(
//                Status.newBuilder()
//                        .setCode(Code.OK_VALUE).setMessage("OK").build()).build();
//
//        PropertyValue propString = PropertyValue.newBuilder().setUString("hello world").build();
//        PropertyValue propInteger = PropertyValue.newBuilder().setUInteger(2023).build();
//        PropertyValue propBoolean = PropertyValue.newBuilder().setUBoolean(true).build();
//
//        FindNodePropertiesResponse.Builder fnpRespBld = FindNodePropertiesResponse.newBuilder();
//        fnpRespBld.putProperties("message", propString);
//        fnpRespBld.putProperties("year", propInteger);
//        fnpRespBld.putProperties("enabled", propBoolean);
//        mFindNodePropertiesResponse = fnpRespBld.build();
//
//        mFailedStatus = Status.newBuilder()
//                .setCode(Code.FAILED_PRECONDITION_VALUE)
//                .setMessage("test exception")
//                .build();
//
//        mNotFoundStatus = Status.newBuilder().setCode(Code.NOT_FOUND_VALUE).build();
//    }

//    private static CloudEvent buildCloudEvent(String methodUri, Any payload) {
//        return CloudEventFactory.request(
//                buildUriForRpc(local(), new UEntity("core.udiscovery", "2"))
//                , methodUri, payload,
//                new UCloudEventAttributes.UCloudEventAttributesBuilder()
//                        .withTtl(CallOptions.TIMEOUT_DEFAULT)
//                        .withPriority(UCloudEventAttributes.Priority.REALTIME_INTERACTIVE)
//                        .build());
//    }
//
//    private static AccountManagerFuture<Bundle> buildAccountManagerFuture() {
//        Bundle result = new Bundle();
//        result.putString(AccountManager.KEY_AUTHTOKEN, UDiscoveryServicesTest.TOKEN);
//        AccountManagerFuture<Bundle> future = mock(AccountManagerFuture.class);
//        try {
//            doReturn(result).when(future).getResult();
//            doReturn(result).when(future).getResult(anyLong(), any());
//        } catch (Exception ignored) {
//        }
//        return future;
//    }
//
//    private static AccountManagerFuture<Bundle> buildAccountManagerFuture(Throwable exception) {
//        AccountManagerFuture<Bundle> future = mock(AccountManagerFuture.class);
//        try {
//            doThrow(exception).when(future).getResult();
//            doThrow(exception).when(future).getResult(anyLong(), any());
//        } catch (Exception ignored) {
//        }
//        return future;
//    }

    @Before
    public void setUp() throws InterruptedException {
        ShadowLog.stream = System.out;

//        CompletableFuture<UStatus> connectFut = CompletableFuture.completedFuture(STATUS_OK);
//        when(mULink.connect()).thenReturn(connectFut);
        when(mDatabaseLoader.initializeLDS()).thenReturn(LoadUtility.initLDSCode.SUCCESS);
//        when(mULink.isConnected()).thenReturn(true);

//        Status okStatus = Status.newBuilder().setCode(Code.OK_VALUE).build();
//        when(mULink.registerEventListener(any(UltifiUri.class),
//                any(UltifiLink.RequestEventListener.class))).thenReturn(okStatus);

//        CompletableFuture<UStatus> response = CompletableFuture.completedFuture(STATUS_OK);
//        doReturn(response).when(mUSubStub).createTopic(any(CreateTopicRequest.class));

        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mService = new UDiscoveryService(mContext, mRpcHandler, mULink, mDatabaseLoader,
                mConnectivityMgr);
        mService.setNetworkStatus(true);

        // sleep to ensure registerAllMethods completes in the async thread before the verify
        // registerEventListener call below
        Thread.sleep(100);

        // capture the request event listener to inject rpc request cloud events
//        ArgumentCaptor<UltifiLink.RequestEventListener> captor = ArgumentCaptor.forClass(
//                UltifiLink.RequestEventListener.class);
//        verify(mULink, atLeastOnce()).registerEventListener(any(UltifiUri.class), captor.capture());
//        mHandler = captor.getValue();
    }


//    private CloudEvent buildRemoteCloudEventRequest(UltifiUri uri, Any payload) {
//        String rpcUri = buildUriForRpc(TEST_AUTHORITY, SERVICE);
//        String methodUri = uri.uProtocolUri();
//        return CloudEventFactory.request(rpcUri, methodUri, payload, UCloudEventAttributes.empty());
//    }
//
//    private void setPropertySdvEnabled(boolean b) {
//        LoadUtilityTest.setProperty(PROPERTY_SDV_ENABLED, (b) ? "true" : "false");
//    }

    @Test
    public void initialization_test() {
        assertTrue(true);
    }


//    @Test
//    public void negative_ulink_connect_exception() {
//        CompletableFuture<Status> connectFut = CompletableFuture.completedFuture(mFailedStatus);
//        UltifiLink mockLink = mock(UltifiLink.class);
//        when(mockLink.connect()).thenReturn(connectFut);
//        boolean bException = false;
//        try {
//            new UDiscoveryService(mContext, mRpcHandler, mockLink, mDatabaseLoader,
//                    mAnalyticsReporter, mConnectivityMgr, mAuthenticator, mUSubStub, null);
//        } catch (CompletionException e) {
//            bException = true;
//            Log.e(LOG_TAG, Key.MESSAGE, "negative_ulink_connect_exception", Key.FAILURE, e);
//        }
//        assertTrue(bException);
//    }
//
//    @Test
//    public void negative_ulink_isConnected_false() {
//        UltifiLink mockLink = mock(UltifiLink.class);
//        USubscription.Stub mockStub = mock(USubscription.Stub.class);
//        CompletableFuture<Status> connectFut = CompletableFuture.completedFuture(STATUS_OK);
//
//        when(mockLink.connect()).thenReturn(connectFut);
//        when(mockLink.isConnected()).thenReturn(false);
//
//        new UDiscoveryService(mContext, mRpcHandler, mockLink, mDatabaseLoader, mAnalyticsReporter,
//                mConnectivityMgr, mAuthenticator, mockStub, null);
//
//        verify(mockLink, never()).registerEventListener(any(UltifiUri.class),
//                any(UltifiLink.RequestEventListener.class));
//        verify(mockStub, never()).createTopic(any(CreateTopicRequest.class));
//    }
//
//    @Test
//    public void negative_handler_uninitialized_exception() throws InterruptedException {
//        LoadUtility mockLoader = mock(LoadUtility.class);
//        when(mockLoader.initializeLDS()).thenReturn(LoadUtility.initLDSCode.FAILURE);
//        new UDiscoveryService(mContext, mRpcHandler, mULink, mockLoader,
//                mAnalyticsReporter, mConnectivityMgr, mAuthenticator, mUSubStub, null);
//
//        // sleep to ensure registerAllMethods completes in the async thread before the verify
//        // registerEventListener call below
//        Thread.sleep(100);
//
//        // capture the request event listener to inject rpc request cloud events
//        ArgumentCaptor<UltifiLink.RequestEventListener> captor = ArgumentCaptor.forClass(
//                UltifiLink.RequestEventListener.class);
//        verify(mULink, atLeastOnce()).registerEventListener(any(UltifiUri.class), captor.capture());
//        UltifiLink.RequestEventListener handler = captor.getValue();
//
//        List<CloudEvent> CloudEventList = List.of(mUpdateNodeCloudEvent,
//                mFindNodePropertiesCloudEvent,
//                mAddNodesCloudEvent,
//                mDeleteNodesCloudEvent,
//                mUpdatePropertyCloudEvent,
//                mRegisterCloudEvent,
//                mUnRegisterCloudEvent);
//        Any response = Any.pack(mFailedStatus);
//        for (int i = 0; i < CloudEventList.size(); i++) {
//            CompletableFuture<Any> fut = new CompletableFuture<>();
//            handler.onEvent(CloudEventList.get(i), fut);
//            fut.whenComplete((result, ex) -> {
//                assertEquals(response, result);
//                assertNotNull(ex);
//            });
//        }
//    }
//
//    @Test
//    public void negative_uLink_registerMethod_exception() throws InterruptedException {
//        CompletableFuture<Status> connectFut = CompletableFuture.completedFuture(STATUS_OK);
//        when(mULink.connect()).thenReturn(connectFut);
//
//        when(mULink.registerEventListener(any(UltifiUri.class),
//                any(UltifiLink.RequestEventListener.class))).thenReturn(mFailedStatus);
//
//        new UDiscoveryService(mContext, mRpcHandler, mULink, mDatabaseLoader, mAnalyticsReporter,
//                mConnectivityMgr, mAuthenticator, mUSubStub, null);
//        // wait for register async tasks to complete
//        Thread.sleep(100);
//        verify(mULink, atLeastOnce()).registerEventListener(any(UltifiUri.class),
//                any(UltifiLink.RequestEventListener.class));
//    }
//
//    @Test
//    public void negative_uLink_unRegisterMethod_exception() throws InterruptedException {
//        when(mDatabaseLoader.getAuthority()).thenReturn(TEST_AUTHORITY);
//        when(mULink.unregisterEventListener(any(UltifiUri.class),
//                any(UltifiLink.RequestEventListener.class))).thenReturn(mFailedStatus);
//
//        mService.onDestroy();
//        // wait for unregister async tasks to complete
//        Thread.sleep(100);
//        verify(mULink, atLeastOnce()).unregisterEventListener(any(UltifiUri.class),
//                any(UltifiLink.RequestEventListener.class));
//    }
//
//    @Test
//    public void negative_handleRequestEvent() {
//        UltifiUri uri = new UltifiUri(local(), SERVICE, forRpc("fakeMethod"));
//        CloudEvent ce = buildCloudEvent(uri.uProtocolUri(), Any.getDefaultInstance());
//        CompletableFuture<Any> fut = new CompletableFuture<>();
//        mHandler.onEvent(ce, fut);
//        fut.whenComplete((result, ex) -> {
//            assertNotNull(ex);
//            assertEquals(Code.INVALID_ARGUMENT_VALUE, throwableToStatus(ex).getCode());
//        });
//    }
//
//    @Test
//    public void positive_executeLookupUri_LDS() {
//        Status ok = Status.newBuilder().setCode(Code.OK_VALUE).build();
//        Any response = Any.pack(mLookupUriResponse);
//        Pair<Status, Any> lookupUriResult = new Pair<>(ok, response);
//        when(mRpcHandler.processLookupUriFromLDS(any(UriRequest.class),
//                any(UltifiUri.class))).thenReturn(lookupUriResult);
//
//        CompletableFuture<Any> fut = new CompletableFuture<>();
//        mHandler.onEvent(mLookupUriCloudEvent, fut);
//
//        fut.whenComplete((result, ex) -> {
//            assertEquals(response, result);
//            assertNull(ex);
//        });
//    }
//
//    @Test
//    public void positive_executeLookupUri_LDS_not_found() {
//        Any responseMessage = Any.pack(mLookupUriResponse);
//        Pair<Status, Any> lookupUriResult = new Pair<>(mNotFoundStatus, responseMessage);
//        when(mRpcHandler.processLookupUriFromLDS(any(UriRequest.class),
//                any(UltifiUri.class))).thenReturn(lookupUriResult);
//
//        CloudEvent ce = buildRemoteCloudEventRequest(LOCAL_LOOKUPURI, Any.getDefaultInstance());
//        CompletableFuture<Any> fut = new CompletableFuture<>();
//        mHandler.onEvent(ce, fut);
//
//        fut.whenComplete((result, ex) -> {
//            assertEquals(responseMessage, result);
//            assertNull(ex);
//        });
//    }
//
//    @Test
//    public void negative_executeLookupUri_throw_exception() {
//        CompletableFuture<Any> fut = new CompletableFuture<>();
//        mHandler.onEvent(mLookupUriCloudEvent, fut);
//
//        fut.whenComplete((result, ex) -> {
//            assertNull(ex);
//            try {
//                UriResponse resp = result.unpack(UriResponse.class);
//                assertEquals(Code.FAILED_PRECONDITION_VALUE, resp.getResult().getCode());
//            } catch (InvalidProtocolBufferException e) {
//                e.printStackTrace();
//            }
//        });
//    }
//
//    @Test
//    public void positive_executeFindNodes_LDS() {
//        Status ok = Status.newBuilder().setCode(Code.OK_VALUE).build();
//        Any response = Any.pack(mFindNodesResponse);
//        Pair<Status, Any> findNodeResult = new Pair<>(ok, response);
//        when(mRpcHandler.processFindNodesFromLDS(any(FindNodesRequest.class),
//                any(UltifiUri.class))).thenReturn(findNodeResult);
//
//        CompletableFuture<Any> fut = new CompletableFuture<>();
//        mHandler.onEvent(mFindNodeCloudEvent, fut);
//
//        fut.whenComplete((result, ex) -> {
//            assertEquals(response, result);
//            assertNull(ex);
//        });
//    }
//
//    @Test
//    public void positive_executeFindNodes_LDS_not_found() {
//        Any response = Any.pack(mFindNodesResponse);
//        Pair<Status, Any> findNodeResult = new Pair<>(mNotFoundStatus, response);
//        when(mRpcHandler.processFindNodesFromLDS(any(FindNodesRequest.class),
//                any(UltifiUri.class))).thenReturn(findNodeResult);
//
//        CloudEvent ce = buildRemoteCloudEventRequest(LOCAL_FINDNODEURI, Any.getDefaultInstance());
//        CompletableFuture<Any> fut = new CompletableFuture<>();
//        mHandler.onEvent(ce, fut);
//
//        fut.whenComplete((result, ex) -> {
//            assertEquals(response, result);
//            assertNull(ex);
//        });
//    }
//
//    @Test
//    public void executeFindNodes_throw_exception() {
//        CompletableFuture<Any> fut = new CompletableFuture<>();
//        mHandler.onEvent(mFindNodeCloudEvent, fut);
//
//        fut.whenComplete((result, ex) -> {
//            assertNull(ex);
//            try {
//                FindNodesResponse resp = result.unpack(FindNodesResponse.class);
//                assertEquals(Code.FAILED_PRECONDITION_VALUE, resp.getStatus().getCode());
//                assertNull(ex);
//            } catch (InvalidProtocolBufferException e) {
//                e.printStackTrace();
//            }
//        });
//    }
//
//    @Test
//    public void positive_executeUpdateNode() {
//        Status okSts = Status.newBuilder().setCode(Code.OK_VALUE).build();
//        Any response = Any.pack(okSts);
//        when(mRpcHandler.processLDSUpdateNode(any(Any.class), any(UltifiUri.class))).thenReturn(
//                Any.pack(response));
//
//        CompletableFuture<Any> fut = new CompletableFuture<>();
//        mHandler.onEvent(mUpdateNodeCloudEvent, fut);
//
//        fut.whenComplete((result, ex) -> {
//            assertEquals(response, result);
//            assertNull(ex);
//        });
//    }
//
//    @Test
//    public void negative_executeUpdateNode_throw_exception() {
//        CompletableFuture<Any> fut = new CompletableFuture<>();
//        mHandler.onEvent(mUpdateNodeCloudEvent, fut);
//
//        fut.whenComplete((result, ex) -> {
//            assertNull(ex);
//            try {
//                Status resp = result.unpack(Status.class);
//                assertEquals(Code.FAILED_PRECONDITION_VALUE, resp.getCode());
//            } catch (InvalidProtocolBufferException e) {
//                e.printStackTrace();
//            }
//        });
//    }
//
//    @Test
//    public void positive_executeFindNodesProperty() {
//        Any response = Any.pack(mFindNodePropertiesResponse);
//        when(mRpcHandler.processFindNodeProperty(any(Any.class))).thenReturn(response);
//
//        CompletableFuture<Any> fut = new CompletableFuture<>();
//        mHandler.onEvent(mFindNodePropertiesCloudEvent, fut);
//
//        fut.whenComplete((result, ex) -> {
//            assertEquals(response, result);
//            assertNull(ex);
//        });
//    }
//
//    @Test
//    public void negative_executeFindNodesProperty_throw_exception() {
//        CompletableFuture<Any> fut = new CompletableFuture<>();
//        mHandler.onEvent(mFindNodePropertiesCloudEvent, fut);
//
//        fut.whenComplete((result, ex) -> {
//            assertNull(ex);
//            try {
//                Status resp = result.unpack(Status.class);
//                assertEquals(Code.FAILED_PRECONDITION_VALUE, resp.getCode());
//            } catch (InvalidProtocolBufferException e) {
//                e.printStackTrace();
//            }
//        });
//    }
//
//    @Test
//    public void positive_executeAddNodes() {
//        Status okSts = Status.newBuilder().setCode(Code.OK_VALUE).build();
//        Any response = Any.pack(okSts);
//        when(mRpcHandler.processAddNodesLDS(any(Any.class), any(UltifiUri.class))).thenReturn(
//                Any.pack(response));
//
//        CompletableFuture<Any> fut = new CompletableFuture<>();
//        mHandler.onEvent(mAddNodesCloudEvent, fut);
//
//        fut.whenComplete((result, ex) -> {
//            assertEquals(response, result);
//            assertNull(ex);
//        });
//    }
//
//    @Test
//    public void negative_executeAddNodes_throw_exception() {
//        CompletableFuture<Any> fut = new CompletableFuture<>();
//        mHandler.onEvent(mAddNodesCloudEvent, fut);
//
//        fut.whenComplete((result, ex) -> {
//            assertNull(ex);
//            try {
//                Status resp = result.unpack(Status.class);
//                assertEquals(Code.FAILED_PRECONDITION_VALUE, resp.getCode());
//            } catch (InvalidProtocolBufferException e) {
//                e.printStackTrace();
//            }
//        });
//    }
//
//    @Test
//    public void positive_executeDeleteNodes() {
//        Status okSts = Status.newBuilder().setCode(Code.OK_VALUE).build();
//        Any response = Any.pack(okSts);
//        when(mRpcHandler.processDeleteNodes(any(Any.class), any(UltifiUri.class))).thenReturn(
//                Any.pack(response));
//
//        CompletableFuture<Any> fut = new CompletableFuture<>();
//        mHandler.onEvent(mDeleteNodesCloudEvent, fut);
//
//        fut.whenComplete((result, ex) -> {
//            assertEquals(response, result);
//            assertNull(ex);
//        });
//    }
//
//    @Test
//    public void negative_executeDeleteNodes_throw_exception() {
//        CompletableFuture<Any> fut = new CompletableFuture<>();
//        mHandler.onEvent(mDeleteNodesCloudEvent, fut);
//
//        fut.whenComplete((result, ex) -> {
//            assertNull(ex);
//            try {
//                Status resp = result.unpack(Status.class);
//                assertEquals(Code.FAILED_PRECONDITION_VALUE, resp.getCode());
//            } catch (InvalidProtocolBufferException e) {
//                e.printStackTrace();
//            }
//        });
//    }
//
//    @Test
//    public void positive_executeUpdateProperty() {
//        Status okSts = Status.newBuilder().setCode(Code.OK_VALUE).build();
//        Any response = Any.pack(okSts);
//        when(mRpcHandler.processLDSUpdateProperty(any(Any.class), any(UltifiUri.class))).thenReturn(
//                Any.pack(response));
//
//        CompletableFuture<Any> fut = new CompletableFuture<>();
//        mHandler.onEvent(mUpdatePropertyCloudEvent, fut);
//
//        fut.whenComplete((result, ex) -> {
//            assertEquals(response, result);
//            assertNull(ex);
//        });
//
//    }
//
//    @Test
//    public void negative_executeUpdateProperty() {
//        CompletableFuture<Any> fut = new CompletableFuture<>();
//        mHandler.onEvent(mUpdatePropertyCloudEvent, fut);
//
//        fut.whenComplete((result, ex) -> {
//            assertNull(ex);
//            try {
//                Status resp = result.unpack(Status.class);
//                assertEquals(Code.FAILED_PRECONDITION_VALUE, resp.getCode());
//            } catch (InvalidProtocolBufferException e) {
//                e.printStackTrace();
//            }
//        });
//    }
//
//    @Test
//    public void positive_executeRegisterNotification() {
//        CompletableFuture<Any> fut = new CompletableFuture<>();
//        mHandler.onEvent(mRegisterCloudEvent, fut);
//
//        fut.whenComplete((result, ex) -> {
//            assertNull(ex);
//            try {
//                Status resp = result.unpack(Status.class);
//                assertEquals(Code.UNIMPLEMENTED_VALUE, resp.getCode());
//            } catch (InvalidProtocolBufferException e) {
//                e.printStackTrace();
//            }
//        });
//    }
//
//    @Test
//    public void negative_executeRegisterNotification_throw_exception() {
//        CompletableFuture<Any> fut = new CompletableFuture<>();
//        mHandler.onEvent(mRegisterCloudEvent, fut);
//
//        fut.whenComplete((result, ex) -> {
//            assertNull(ex);
//            try {
//                Status resp = result.unpack(Status.class);
//                assertEquals(Code.FAILED_PRECONDITION_VALUE, resp.getCode());
//            } catch (InvalidProtocolBufferException e) {
//                e.printStackTrace();
//            }
//        });
//    }
//
//    @Test
//    public void positive_executeUnRegisterNotification() {
//        CompletableFuture<Any> fut = new CompletableFuture<>();
//        mHandler.onEvent(mUnRegisterCloudEvent, fut);
//
//        fut.whenComplete((result, ex) -> {
//            assertNull(ex);
//            try {
//                Status resp = result.unpack(Status.class);
//                assertEquals(Code.UNIMPLEMENTED_VALUE, resp.getCode());
//            } catch (InvalidProtocolBufferException e) {
//                e.printStackTrace();
//            }
//        });
//    }
//
//    @Test
//    public void negative_executeUnregisterNotification_throw_exception() {
//        CompletableFuture<Any> fut = new CompletableFuture<>();
//        mHandler.onEvent(mUnRegisterCloudEvent, fut);
//
//        fut.whenComplete((result, ex) -> {
//            assertNull(ex);
//            try {
//                Status resp = result.unpack(Status.class);
//                assertEquals(Code.FAILED_PRECONDITION_VALUE, resp.getCode());
//            } catch (InvalidProtocolBufferException e) {
//                e.printStackTrace();
//            }
//        });
//    }
//
//    @Test
//    public void testOnCreate() {
//        setPropertySdvEnabled(true);
//        mService = Robolectric.setupService(UDiscoveryService.class);
//        setPropertySdvEnabled(false);
//        mService = Robolectric.setupService(UDiscoveryService.class);
//    }
//
//    @SuppressWarnings("AssertBetweenInconvertibleTypes")
//    @Test
//    public void testBinder() {
//        setPropertySdvEnabled(true);
//        assertNotEquals(mService, mService.onBind(new Intent()));
//        setPropertySdvEnabled(false);
//        assertNotEquals(mService, mService.onBind(new Intent()));
//    }
//
//    @Test
//    public void shutDown() throws InterruptedException {
//        when(mDatabaseLoader.getAuthority()).thenReturn(TEST_AUTHORITY);
//        Status ok = Status.newBuilder().setCode(Code.OK_VALUE).build();
//        when(mULink.unregisterEventListener(any(UltifiUri.class),
//                any(UltifiLink.RequestEventListener.class))).thenReturn(ok);
//        when(mULink.isConnected()).thenReturn(true);
//        when(mULink.unregisterEventListener(any(UltifiUri.class),
//                any(UltifiLink.EventListener.class))).thenReturn(ok);
//        UriResponse uriResponse = UriResponse.newBuilder()
//                .addUris("ultifi://vcu.VIN.veh.ultifi.gm.com").build();
//        Pair<Status, Any> authorityDetails = new Pair<>(ok, Any.pack(uriResponse));
//        when(mRpcHandler.processLookupUriFromLDS(any(), any())).thenReturn(authorityDetails);
//        mService.onDestroy();
//        // wait for unregister async tasks to complete
//        Thread.sleep(100);
//        verify(mULink, atLeastOnce()).unregisterEventListener(any(UltifiUri.class),
//                any(UltifiLink.RequestEventListener.class));
//    }
//
//    @Test
//    public void positive_networkCallbacks() {
//        setLogLevel(Log.INFO);
//        mService.setNetworkStatus(false);
//        AtomicBoolean flag = new AtomicBoolean(false);
//        NetworkStatusInterface consumer = flag::set;
//        NwConnectionCallbacks cb = new NwConnectionCallbacks(consumer);
//
//        cb.onAvailable(null);
//        assertTrue(flag.get());
//        cb.onLost(null);
//        assertFalse(flag.get());
//        cb.onCapabilitiesChanged(null, null);
//    }
//
//    @Test
//    public void positive_networkCallbacks_verbose() {
//        setLogLevel(Log.VERBOSE);
//        mService.setNetworkStatus(false);
//        AtomicBoolean flag = new AtomicBoolean(false);
//        NetworkStatusInterface consumer = status -> {
//            flag.set(status);
//        };
//        NwConnectionCallbacks cb = new NwConnectionCallbacks(consumer);
//
//        cb.onAvailable(null);
//        assertTrue(flag.get());
//        cb.onLost(null);
//        assertFalse(flag.get());
//        cb.onCapabilitiesChanged(null, null);
//    }
}
