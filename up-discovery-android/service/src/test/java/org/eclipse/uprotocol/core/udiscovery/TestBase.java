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

import static org.eclipse.uprotocol.common.util.log.Formatter.join;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;

import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.core.udiscovery.v3.Node;
import org.eclipse.uprotocol.core.udiscovery.v3.NotificationsRequest;
import org.eclipse.uprotocol.core.udiscovery.v3.ObserverInfo;
import org.eclipse.uprotocol.v1.UAuthority;
import org.eclipse.uprotocol.v1.UEntity;

import java.util.List;

public class TestBase {
    public static final String LOG_TAG = TestBase.class.getSimpleName();
    public static final String SERVICE_NAME = "core.udiscovery";
    public static final String TEST_VIN = "1gk12d1t2n10339dc";
    public static final String TEST_DOMAIN = TEST_VIN + ".veh.ultifi.gm.com";
    public static final String TEST_DEVICE = "vcu";
    public static final String TEST_ALTERNATE_DEVICE = "cgm";
    public static final String TEST_ENTITY = "body.access";
    public static final String TEST_ALTERNATE_ENTITY = "cabin.climate";
    public static final String TEST_NAME = String.join(".", TEST_DEVICE, TEST_DOMAIN);
    public static final String TEST_OBSERVER1 = "observer1";
    public static final String TEST_OBSERVER2 = "observer2";
    public static final String TEST_RESOURCE = "door";
    public static final String TEST_INSTANCE_1 = "front_left";
    public static final String TEST_INSTANCE_2 = "front_right";
    public static final String TEST_INSTANCE_3 = "rear_left";
    public static final String TEST_INSTANCE_4 = "rear_right";
    public static final String TEST_PROPERTY1 = "ent_prop1";
    public static final String TEST_PROPERTY2 = "ent_prop2";
    public static final UEntity SERVICE = UEntity.newBuilder().setName(SERVICE_NAME).setVersionMajor(2).build();
    public static final UAuthority TEST_AUTHORITY = UAuthority.newBuilder().setName(TEST_NAME).build();
    public static final int DEFAULT_DEPTH = -1;
//    public static final UltifiUri VEHICLE_SOURCE = UltifiUriFactory.parseFromUri("ultifi:/core.uota/2/rpc.response");
//    public static final UResource LOOKUPURI_METHOD = forRpc(METHOD_LOOKUP_URI);
//    public static final UltifiUri LOCAL_LOOKUPURI = new UltifiUri(local(), SERVICE, LOOKUPURI_METHOD);
//    public static final UResource FINDNODEURI_METHOD = forRpc(METHOD_FIND_NODES);
//    public static final UltifiUri LOCAL_FINDNODEURI = new UltifiUri(local(), SERVICE, FINDNODEURI_METHOD);
//    public static final UResource UPDATENODE_METHOD = forRpc(METHOD_UPDATE_NODE);
//    public static final UltifiUri LOCAL_UPDATENODE = new UltifiUri(local(), SERVICE, UPDATENODE_METHOD);
//    public static final UResource FINDNODEPROPERTY_METHOD = forRpc(METHOD_FIND_NODE_PROPERTIES);
//    public static final UltifiUri LOCAL_FINDNODEPROPERTY = new UltifiUri(local(), SERVICE, FINDNODEPROPERTY_METHOD);
//    public static final UResource ADDNODES_METHOD = forRpc(METHOD_ADD_NODES);
//    public static final UltifiUri LOCAL_ADDNODES = new UltifiUri(local(), SERVICE, ADDNODES_METHOD);
//    public static final UResource DELETENODES_METHOD = forRpc(METHOD_DELETE_NODES);
//    public static final UltifiUri LOCAL_DELETENODES = new UltifiUri(local(), SERVICE, DELETENODES_METHOD);
//    public static final UResource UPDATEPROPERTY_METHOD = forRpc(METHOD_UPDATE_PROPERTY);
//    public static final UltifiUri LOCAL_UPDATEPROPERTY = new UltifiUri(local(), SERVICE, UPDATEPROPERTY_METHOD);
//    public static final UResource REGISTER_NOTIFICATION_METHOD = forRpc(METHOD_REGISTER_FOR_NOTIFICATIONS);
//    public static final UltifiUri LOCAL_REGISTER_NOTIFICATION = new UltifiUri(local(), SERVICE, REGISTER_NOTIFICATION_METHOD);
//    public static final UResource UNREGISTER_NOTIFICATION_METHOD = forRpc(METHOD_UNREGISTER_FOR_NOTIFICATIONS);
//    public static final UltifiUri LOCAL_UNREGISTER_NOTIFICATION= new UltifiUri(local(), SERVICE, UNREGISTER_NOTIFICATION_METHOD);
//    public static final UltifiUri LOCAL_NODE_URI1= new UltifiUri(local(), SERVICE, "Node1");
//    public static final UltifiUri LOCAL_NODE_URI2= new UltifiUri(local(), SERVICE, "Node2");
//    public static final UltifiUri LOCAL_OBSERVER_URI1= new UltifiUri(local(), SERVICE, "Observer1");
//    public static final UltifiUri LOCAL_OBSERVER_URI2= new UltifiUri(local(), SERVICE, "Observer2");
//    public static final UltifiUri REMOTE_OBSERVER_URI3= new UltifiUri(remote("azure", "bo.ultifi.gm.com"), SERVICE, "Observer3");
//    public static final String NODE1 = LOCAL_NODE_URI1.uProtocolUri();
//    public static final String NODE2 = LOCAL_NODE_URI2.uProtocolUri();
//    public static final String INVALID_NODE = "Invalid Node";
//    public static final UltifiUri LOCAL_INVALID_NODE_URI= new UltifiUri(local(), SERVICE, "Invalid Node");
//    public static final UltifiUri ULTIFI_UOTA_ROOTNODE_URI= new UltifiUri(local(), new UEntity("", ""), UResource.empty());
//    public static final UltifiUri BODY_ACCESS_V1 = new UltifiUri(local(),
//            new UEntity("body.access", "1"), UResource.empty());
//    public static final UltifiUri BODY_ACCESS = new UltifiUri(local(),
//            new UEntity("body.access", ""), UResource.empty());
//    public static final String BODY_ACCESS_V1_URI = BODY_ACCESS_V1.uProtocolUri();
//    public static final String OBSERVER1 = LOCAL_OBSERVER_URI1.uProtocolUri();
//    public static final String OBSERVER2 = LOCAL_OBSERVER_URI2.uProtocolUri();
    protected static final long DELAY_LONG_MS = 500;

    protected static @NonNull Node jsonToNode(String json) {
        Node.Builder bld = Node.newBuilder();
        try {
            JsonFormat.parser().merge(json, bld);
        } catch (InvalidProtocolBufferException e) {
            Log.e(LOG_TAG, join(Key.MESSAGE, "jsonToNode", Key.FAILURE, e));
        }
        return bld.build();
    }

    protected NotificationsRequest buildNotificationRequest(List<String> nodeUris, String observerUri) {
        return NotificationsRequest.newBuilder()
                .addAllUris(nodeUris)
                .setObserver(ObserverInfo.newBuilder().setUri(observerUri).build())
                .build();
    }
}
