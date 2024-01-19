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

package org.eclipse.uprotocol.core.udiscovery.db;

import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.core.udiscovery.UDiscoveryService.METHOD_ADD_NODES;
import static org.eclipse.uprotocol.core.udiscovery.UDiscoveryService.METHOD_DELETE_NODES;
import static org.eclipse.uprotocol.core.udiscovery.UDiscoveryService.METHOD_FIND_NODE_PROPERTIES;
import static org.eclipse.uprotocol.core.udiscovery.UDiscoveryService.METHOD_LOOKUP_URI;
import static org.eclipse.uprotocol.core.udiscovery.UDiscoveryService.METHOD_UPDATE_NODE;
import static org.eclipse.uprotocol.core.udiscovery.UDiscoveryService.METHOD_UPDATE_PROPERTY;
import static org.eclipse.uprotocol.core.udiscovery.UDiscoveryService.errorStatus;
import static org.eclipse.uprotocol.core.udiscovery.db.DatabaseUtility.insert;
import static org.eclipse.uprotocol.core.udiscovery.internal.log.Formatter.quote;

import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;

import org.eclipse.uprotocol.common.util.log.Formatter;
import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.core.udiscovery.Notifier;
import org.eclipse.uprotocol.core.udiscovery.UDiscoveryService;
import org.eclipse.uprotocol.core.udiscovery.common.Constants;
import org.eclipse.uprotocol.core.udiscovery.interfaces.ChecksumInterface;
import org.eclipse.uprotocol.core.udiscovery.interfaces.PersistInterface;
import org.eclipse.uprotocol.core.udiscovery.internal.Utils;
import org.eclipse.uprotocol.core.udiscovery.v3.Node;
import org.eclipse.uprotocol.core.udiscovery.v3.Notification;
import org.eclipse.uprotocol.core.udiscovery.v3.PropertyValue;
import org.eclipse.uprotocol.uri.serializer.LongUriSerializer;
import org.eclipse.uprotocol.v1.UAuthority;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UEntity;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;
import org.eclipse.uprotocol.v1.UUriBatch;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class DiscoveryManager {
    private static final String LOG_TAG = Formatter.tag("core", DiscoveryManager.class.getSimpleName());
    private static final String LOCALURI = "localUri";
    private static final String KEY = "key";
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExpiryTable expiryTable = new ExpiryTable();
    private org.eclipse.uprotocol.v1.UAuthority ldsAuthority = UAuthority.getDefaultInstance();
    private Node ldsTree = Node.getDefaultInstance();
    private PersistInterface persistIntf;
    private ChecksumInterface checksumIntf;

    private final Notifier mNotifier;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    public DiscoveryManager(Notifier notifier) {
        mNotifier = notifier;
    }

    public void setPersistInterface(PersistInterface intf) {
        persistIntf = intf;
    }

    public void shutdown() {
        mExecutor.shutdown();
    }

    public void setChecksumInterface(ChecksumInterface intf) { checksumIntf = intf; }

    /**
     * @return true if successful, false otherwise
     * @fn init
     * @brief Creates a "shell" database on the first run
     * @param authority - authority for the vehicle
     */
    public synchronized boolean init(@NonNull UAuthority authority) {
        try {
            final String domain = Utils.parseAuthority(authority).second;
            expiryTable.clear();
            ldsAuthority = authority;
            final UAuthority domainAuthority = UAuthority.newBuilder().setName(domain).build();
            final String domainUri = Utils.toLongUri(domainAuthority);
            final String deviceUri = Utils.toLongUri(authority);
            final Node deviceNode = Node.newBuilder().setUri(deviceUri).setType(Node.Type.DEVICE).build();
            ldsTree = Node.newBuilder().setUri(domainUri).setType(Node.Type.DOMAIN).addNodes(deviceNode).build();
            return true;
        } catch (Utils.StatusException e) {
            Log.e(LOG_TAG, join(Key.EVENT, "init", Key.MESSAGE, e.getMessage()));
            return false;
        }
    }

    /**
     * @fn cleanup
     * @brief Erase all data from the database, cancel all pending expiration futures and
     * shutdown the ScheduledExecutorService
     */
    public synchronized void cleanup() {
        expiryTable.clear();
        ldsAuthority = UAuthority.getDefaultInstance();
        ldsTree = Node.getDefaultInstance();
        scheduler.shutdown();
    }

    /**
     * @return List<String> - a list of Uri strings
     * @fn lookupUri
     * @brief This is used by any ultifi application or service to find service instances location,
     * and its current version. What is returned is a list of Uri strings like the following:
     * Example Application calls: lookupUri(“ultifi:///core.example”)
     * Returns: [“ultifi://ultifi.gm.com/core.example/2.0”, “ultifi:core.example/1.0”]
     * @param uri - an Ultifi URI string
     */
    public synchronized Pair<UUriBatch, UStatus> lookupUri(@NonNull UUri uri) {
        UStatus sts;
        final UUriBatch.Builder batch = UUriBatch.newBuilder();
        try {
            final String serviceName = uri.getEntity().getName();
            Utils.checkStringNotEmpty(serviceName, "[lookupUri] entity name is empty ");
            if (serviceName.equals(Constants.JSON_AUTHORITY)) {
                final UUri authorityUri = UUri.newBuilder().setAuthority(ldsAuthority).build();
                batch.addUris(authorityUri);
                sts = UStatus.newBuilder().setCode(UCode.OK).setMessage("[lookupUri] Success").build();
                return new Pair<>(batch.build(), sts);
            }

            final UEntity serviceEntity = UEntity.newBuilder().setName(serviceName).build();
            final String serviceUri = Utils.toLongUri(ldsAuthority, serviceEntity);
            final Node serviceNode = DatabaseUtility.internalFindNode(ldsTree, serviceUri);
            Utils.checkNotNull(serviceNode, UCode.NOT_FOUND, "[lookupUri] could not find " + serviceUri);
            for (Node entity : serviceNode.getNodesList()) {
                final UUri fullUri = LongUriSerializer.instance().deserialize(entity.getUri());
                batch.addUris(fullUri);
            }
            sts = UStatus.newBuilder().setCode(UCode.OK).setMessage("[lookupUri] Success").build();
            return new Pair<>(batch.build(), sts);
        } catch (Utils.StatusException e) {
            sts = errorStatus(LOG_TAG, METHOD_LOOKUP_URI, Utils.throwableToStatus(e));
        }
        return new Pair<>(batch.build(), sts);
    }

    /**
     * @return google.rpc.Status
     * @fn updateNode
     * @brief Add or replace a new node in the hierarchy
     * @param node - an Ultifi node in JSON format
     * @param ttl - Time To Live in seconds, ttl equal to -1 indicates live forever
     */
    public synchronized UStatus updateNode(@NonNull Node node, long ttl) {
        UStatus sts;
        try {
            final Node insertNode = DatabaseUtility.verifyNode(node);
            final List<Node.Builder> nodePath = DatabaseUtility.FindPathToNode(ldsTree.toBuilder(),
                    insertNode.getUri());
            Utils.checkArgumentPositive(nodePath.size(), UCode.NOT_FOUND,
                    "[updateNode] could not find " + insertNode.getUri());
            if (nodePath.size() == 1) {
                ldsTree = insertNode;
            } else {
                Node.Builder parentBld = nodePath.get(nodePath.size() - 2);
                DatabaseUtility.commitNode(parentBld, insertNode);
                ldsTree = nodePath.get(0).build();
            }
            setExpirationTime(insertNode.getUri(), ttl);
            final List<UUri> uriPath = DatabaseUtility.extractUriFromNodeOrBuilder(List.copyOf(nodePath));
            mExecutor.execute(() -> mNotifier.notifyObserversWithParentUri(Notification.Operation.UPDATE, uriPath));
            sts = UStatus.newBuilder().setCode(UCode.OK).setMessage("[UpdateNode] Success").build();
        } catch (Utils.StatusException e) {
            sts = errorStatus(LOG_TAG, METHOD_UPDATE_NODE, Utils.throwableToStatus(e));
        }
        return sts;
    }

    /**
     * @return google.rpc.Status
     * @fn updateProperty
     * @brief Update property value if property exists otherwise create new one
     * @param property - property to look for under given nodeUri
     * @param value - latest value to update for a property
     * @param uri - uri of node where property to be updated/placed
     */
    public synchronized UStatus updateProperty(@NonNull String property, @NonNull PropertyValue value, @NonNull UUri uri) {
        UStatus sts;
        try {
            Utils.checkStringNotEmpty(property, "[updateProperty] property is empty");
            final String longUri = Utils.toLongUri(uri);
            Utils.checkPropertyUpdateAllowed(uri, property);
            final List<Node.Builder> nodePath = DatabaseUtility.FindPathToNode(ldsTree.toBuilder(),
                    property);
            Utils.checkArgumentPositive(nodePath.size(), UCode.NOT_FOUND, "[updateProperty] could not find " + longUri);
            final Node.Builder builder = nodePath.get(nodePath.size() - 1);
            Utils.checkWritePermissions(uri, longUri, builder.getType());
            builder.putProperties(property, value);
            ldsTree = nodePath.get(0).build();
            final List<UUri> uriPath = DatabaseUtility.extractUriFromNodeOrBuilder(List.copyOf(nodePath));
            mExecutor.execute(() -> mNotifier.notifyObserversWithParentUri(Notification.Operation.UPDATE, uriPath));
            sts = UStatus.newBuilder().setCode(UCode.OK).setMessage("[updateProperty] Success").build();
        } catch (Utils.StatusException e) {
            sts = errorStatus(LOG_TAG, METHOD_UPDATE_PROPERTY, Utils.throwableToStatus(e));
        }
        return sts;
    }

    /**
     * @return google.rpc.Status
     * @fn addNodes
     * @brief Adds node in the hierarchy
     * @param parentUri - UUri object which represents ParentUri
     * @param nodesList - list of nodes to be added
     */
    public synchronized UStatus addNodes(@NonNull UUri parentUri, @NonNull List<Node> nodesList) {
        UStatus sts;
        try {
            final String insertionUri = Utils.toLongUri(parentUri);
            Utils.checkStringNotEmpty(insertionUri, "[addNodes] parentUri is empty");
            Utils.checkArgumentPositive(nodesList.size(), "[addNodes] nodesList is empty");
            final List<Node.Builder> nodePath = DatabaseUtility.FindPathToNode(ldsTree.toBuilder(),
                    insertionUri);
            Utils.checkArgumentPositive(nodePath.size(), UCode.NOT_FOUND, "[addNodes] could not find " +
                    insertionUri);

            // Adding the list of nodes at once
            final int lastIdx = nodePath.size() - 1;
            Node.Builder parentBld = nodePath.get(lastIdx);
            parentBld.addAllNodes(nodesList);

            // Verify nodes before adding
            final Node insertNode = DatabaseUtility.verifyNode(parentBld.build());
            if (nodePath.size() == 1) {
                ldsTree = insertNode;
            } else {
                parentBld = nodePath.get(nodePath.size() - 2);
                final Node.Builder childBld = DatabaseUtility.commitNode(parentBld, insertNode);
                nodePath.set(lastIdx, childBld);
                ldsTree = nodePath.get(0).build();
            }
            final List<UUri> uriPath = DatabaseUtility.extractUriFromNodeOrBuilder(List.copyOf(nodePath));
            final List<UUri> uriList = DatabaseUtility.extractUriFromNodeOrBuilder(List.copyOf(nodesList));
            mExecutor.execute(() -> mNotifier.notifyObserversAddNodes(uriPath, uriList));
            sts = UStatus.newBuilder().setCode(UCode.OK).setMessage("[AddNodes] Success").build();

        } catch (Utils.StatusException e) {
            sts = errorStatus(LOG_TAG, METHOD_ADD_NODES, Utils.throwableToStatus(e));
        }
        return sts;
    }

    /**
     * @return Status
     * @fn deleteNodes
     * @brief Delete a list of nodes from the uOTA database
     * @param uriList - list or node uri's to delete
     */
    public synchronized UStatus deleteNodes(@NonNull List<UUri> uriList) {
        UStatus sts;
        try {
            final StringBuilder message = new StringBuilder("[deleteNodes]");
            final List<List<Node.Builder>> sortedList = new ArrayList<>();
            final Node.Builder root = ldsTree.toBuilder();
            for (UUri uri : uriList) {
                String longUri = LongUriSerializer.instance().serialize(uri);
                final List<Node.Builder> nodePath = DatabaseUtility.FindPathToNode(root, longUri);
                if (nodePath.isEmpty()) {
                    message.append(" could not find " + longUri);
                } else if (nodePath.size() == 1) {
                    message.append(" cannot delete root node " + longUri);
                } else {
                    insert(sortedList, nodePath);
                }
            }
            for (List<Node.Builder> branch : sortedList) {
                final Node.Builder builder = branch.get(branch.size() - 1);
                final String uri = builder.getUri();
                expiryTable.remove(uri);
                DatabaseUtility.DeleteNodeFromPath(branch);
                final List<UUri> uriPath = DatabaseUtility.extractUriFromNodeOrBuilder(List.copyOf(branch));
                mExecutor.execute(() -> mNotifier.notifyObserversWithParentUri(Notification.Operation.REMOVE, uriPath));
                message.append(" deleted " + uri);
            }
            ldsTree = root.build();
            UCode code = sortedList.isEmpty() ? UCode.NOT_FOUND : UCode.OK;
            sts = UStatus.newBuilder().setCode(code).setMessage(message.toString()).build();
        } catch (Utils.StatusException e) {
            sts = errorStatus(LOG_TAG, METHOD_DELETE_NODES, Utils.throwableToStatus(e));
        }
        return sts;
    }

    /**
     * @return rv - protobuf formatted data from the registry along with status information
     * @fn findNode
     * @brief Find a node in the hierarchy based on URI.</brief>
     * @param uri - URI string to find
     * @param depth - Int depth to return node tree as
     */
    public synchronized Pair<Node, UStatus> findNode(@NonNull UUri uri, int depth) {
        UStatus sts = UStatus.getDefaultInstance();
        try {
            final String localUri = Utils.toLongUri(uri);
            Utils.checkStringNotEmpty(localUri, "[findNode] uri is empty");
            Log.i(LOG_TAG, join(Key.EVENT, UDiscoveryService.METHOD_FIND_NODES, LOCALURI, quote(localUri)));
            final Node node = DatabaseUtility.internalFindNode(ldsTree, localUri);
            Utils.checkNotNull(node, UCode.NOT_FOUND, "[findNode] could not find " + localUri + " in uOTA");
            sts = sts.toBuilder().setCode(UCode.OK).setMessage("[findNode] Success").build();
            final Node nodeToReturn = (depth < 0) ? node : DatabaseUtility.copy(node, depth);
            return new Pair<>(nodeToReturn, sts);
        } catch (Utils.StatusException e) {
            sts = errorStatus(LOG_TAG, UDiscoveryService.METHOD_FIND_NODES, Utils.throwableToStatus(e));
        }
        return new Pair<>(null, sts);
    }

    /**
     * @return pMap - a map of PropertyValues from the uOTA db along with status information
     * @fn findNodeProperties
     * @brief Find properties belonging to a node in the hierarchy based on URI and name.
     * This operation will be performed only in the uOTA db with local uris, It will return
     * the map of properties names and its values found in the uOTA db along with the status
     * information. The status will be OK even if some of the requested properties values are
     * not found but the the message indicating as "Success for limited properties"
     * @param uri - URI string
     * @param nameList - names of property values
     */
    public synchronized Pair<Map<String, PropertyValue>, UStatus> findNodeProperties(@NonNull UUri uri,
            @NonNull List<String> nameList) {
        UStatus sts;
        try {
            Utils.checkArgumentPositive(nameList.size(), "[findNodeProperties] nameList is empty");
            final String longUri = Utils.toLongUri(uri);
            final Node node = DatabaseUtility.internalFindNode(ldsTree, longUri);
            Utils.checkNotNull(node, UCode.UNAVAILABLE, "[findNodeProperties] could not find " + longUri);
            final Map<String, PropertyValue> nodeProperties = node.getPropertiesMap();
            Map<String, PropertyValue> propMap = new HashMap<>();
            for (String name : nameList) {
                final PropertyValue propValue = nodeProperties.get(name);
                if (propValue == null) {
                    Log.w(LOG_TAG, join(Key.REQUEST, METHOD_FIND_NODE_PROPERTIES, Key.MESSAGE,
                            "could not find property", Key.NAME, quote(name)));
                } else {
                    propMap.put(name, propValue);
                }
            }
            Utils.checkArgumentPositive(propMap.size(), UCode.NOT_FOUND, "[findNodeProperties] Failed");
            String msg = "[findNodeProperties] Success";
            if (propMap.size() < nameList.size()) {
                msg += " for limited properties";
            }
            sts = UStatus.newBuilder().setCode(UCode.OK).setMessage(msg).build();
            return new Pair<>(propMap, sts);
        } catch (Utils.StatusException e) {
            sts = errorStatus(LOG_TAG, METHOD_FIND_NODE_PROPERTIES, Utils.throwableToStatus(e));
        }
        return new Pair<>(new HashMap<>(), sts);
    }

    /**
     * @return the entire discovery database as a JSON string
     * @fn export
     * @brief Converts the internal representation of the data to a JSON object
     */
    public synchronized String export() {
        try {
            final String authority = ldsAuthority.getName();

            final JsonObject data = new JsonObject();
            final JsonObject hash = new JsonObject();

            writeNode(data, hash, Constants.JSON_HIERARCHY, ldsTree);

            final JsonObject root = new JsonObject();
            root.addProperty(Constants.JSON_AUTHORITY, authority);
            root.add(Constants.JSON_DATA, data);
            root.add(Constants.JSON_HASH, hash);
            root.add(Constants.JSON_TTL, expiryTable.export());

            return gson.toJson(root);

        } catch (InvalidProtocolBufferException e) {
            errorStatus(LOG_TAG, "export", Utils.throwableToStatus(e));
        }
        return "";
    }

    /**
     * @return true if successful, false otherwise
     * @fn load
     * @brief Initialize the Discovery Database with the persisted JSON store
     * @param json - the persisted JSON string
     */
    public synchronized boolean load(@NonNull String json) {
        try {
            final JsonElement rootElement = JsonParser.parseString(json);
            final JsonObject root = rootElement.getAsJsonObject();
            final String authority = root.get(Constants.JSON_AUTHORITY).getAsString();
            final JsonObject data = root.getAsJsonObject(Constants.JSON_DATA);
            final JsonObject hash = root.getAsJsonObject(Constants.JSON_HASH);
            final JsonObject ttl = root.getAsJsonObject(Constants.JSON_TTL).getAsJsonObject(Constants.JSON_HIERARCHY);

            final UAuthority temporaryCopy = UAuthority.newBuilder().setName(authority).build();
            // verify the authority before assigning it
            Utils.parseAuthority(temporaryCopy);
            ldsAuthority = temporaryCopy;

            ldsTree = readNode(data, hash, Constants.JSON_HIERARCHY);
            if (ldsTree == null) {
                init(ldsAuthority);
            }
            loadTtl(ttl);
            return true;

        } catch (InvalidProtocolBufferException e) {
            Log.e(LOG_TAG, join("load", e.getMessage()));
        } catch (Exception e) {
            Log.e(LOG_TAG, join("load", e.getMessage()));
        }
        return false;
    }

    /**
     * @fn writeNode
     * @brief Convert the node to json and add it to the data section
     * Calculate the hash and add it to the hash section
     * @param data - data section
     * @param hash - hash section
     * @param key - key to write to each section
     * @param node - Node object
     */
    private void writeNode(@NonNull JsonObject data, JsonObject hash, @NonNull String key, @NonNull Node node)
            throws InvalidProtocolBufferException {

        Utils.checkNotNull(data, "[writeNode] data is null");
        Utils.checkNotNull(hash, "[writeNode] hash is null");
        Utils.checkStringNotEmpty(key, "[writeNode] key is empty");
        Utils.checkNotNull(node, "[writeNode] node is null");

        final String jString = JsonFormat.printer().print(node);
        final JsonObject jObject = (JsonObject) JsonParser.parseString(jString);
        final String payload = gson.toJson(jObject);
        String signature = "";
        if (checksumIntf != null) {
            signature = checksumIntf.generateHash(payload);
        }
        data.add(key, jObject);
        hash.addProperty(key, signature);
    }

    /**
     * @return None
     * @fn readNode
     * @brief Read the data, verify the hash, and convert json to protobuf
     * @param data - data section
     * @param hash - hash section
     * @param key - key to read from each section
     */
    private Node readNode(@NonNull JsonObject data, @NonNull JsonObject hash, @NonNull String key)
            throws InvalidProtocolBufferException {

        Utils.checkNotNull(data, "[readNode] data is null");
        Utils.checkNotNull(hash, "[readNode] hash is null");
        Utils.checkStringNotEmpty(key, "[readNode] key is empty");

        final JsonObject jObject = data.getAsJsonObject(key);
        final String payload = gson.toJson(jObject);
        final String signature = hash.get(key).getAsString();
        final boolean bOk = (checksumIntf == null) ? true : checksumIntf.verifyHash(payload, signature);
        if (!bOk) {
            Log.w(LOG_TAG, join("readNode", Key.MESSAGE,  "hash check failed", KEY, quote(key)));
            return null;
        }
        final Node.Builder bld = Node.newBuilder();
        JsonFormat.parser().merge(payload, bld);
        return bld.build();
    }

    /**
     * @fn setExpirationTime
     * @brief Schedules a runnable to delete the node at a specific point in time
     * @param uri - an Ultifi URI string
     * @param millis - number of milliseconds from the current time upon which
     * the node is set to expire
     */
    private void setExpirationTime(@NonNull String uri, long millis) {
        Utils.checkNotNull(uri, "[setExpirationTime] uri is null");
        if (millis <= 0) {
            return;
        }
        final Instant expiryInstant = Instant.now().plusMillis(millis);
        final ScheduledFuture<?> future = scheduler.schedule(onExpired(uri), millis,
                TimeUnit.MILLISECONDS);
        final ExpiryData ed = new ExpiryData(uri, expiryInstant.toString(), future);
        expiryTable.add(ed);
    }

    /**
     * @return Runnable
     * @fn onExpired
     * @brief Creates a task to delete the node associated with the given URI
     * @param uri - an Ultifi URI string
     */
    private synchronized Runnable onExpired(@NonNull String uri) {
        return () -> {
            expiryTable.remove(uri);
            try {
                ldsTree = DatabaseUtility.internalDeleteNode(ldsTree, uri);
                if (null != persistIntf) {
                    persistIntf.persist(export());
                }
            } catch (Utils.StatusException e) {
                errorStatus(LOG_TAG, "onExpired", Utils.throwableToStatus(e));
            }
        };
    }

    /**
     * @fn loadTtl
     * @brief Load DiscoveryManager TTL
     * @param obj - JsonObject containing ttl data
     */
    private void loadTtl(@NonNull JsonObject obj) {
        final Instant now = Instant.now();
        final ArrayList<String> expiredList = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            final String uri = e.getKey();
            final String exp = e.getValue().getAsString();
            final Instant expiry = Instant.parse(exp);
            if (now.isBefore(expiry)) {
                final long millis = Duration.between(now, expiry).toMillis();
                final ScheduledFuture<?> future = scheduler.schedule(onExpired(uri), millis,
                        TimeUnit.MILLISECONDS);
                final ExpiryData ed = new ExpiryData(uri, exp, future);
                expiryTable.add(ed);
            } else {
                expiredList.add(uri);
            }
        }
        for (String uri : expiredList) {
            try {
                ldsTree = DatabaseUtility.internalDeleteNode(ldsTree, uri);
            } catch (Utils.StatusException e) {
                errorStatus(LOG_TAG, "loadTtl", Utils.throwableToStatus(e));
            }
        }
    }
}
