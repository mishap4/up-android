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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.v1.UCode;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class UUriUtilsTest extends TestBase {
    @Test
    public void testCheckTopicUriValid() {
        assertEquals(RESOURCE_URI, UUriUtils.checkTopicUriValid(RESOURCE_URI));
    }

    @Test
    public void testCheckTopicUriValidNegative() {
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () -> UUriUtils.checkTopicUriValid(EMPTY_URI));
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () -> UUriUtils.checkTopicUriValid(CLIENT_URI));
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () -> UUriUtils.checkTopicUriValid(METHOD_URI));
    }

    @Test
    public void testCheckMethodUriValid() {
        assertEquals(METHOD_URI, UUriUtils.checkMethodUriValid(METHOD_URI));
    }

    @Test
    public void testCheckMethodUriValidNegative() {
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () -> UUriUtils.checkMethodUriValid(EMPTY_URI));
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () -> UUriUtils.checkMethodUriValid(CLIENT_URI));
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () -> UUriUtils.checkMethodUriValid(RESOURCE_URI));
    }

    @Test
    public void testCheckResponseUriValid() {
        assertEquals(CLIENT_URI, UUriUtils.checkResponseUriValid(CLIENT_URI));
    }

    @Test
    public void testCheckResponseUriValidNegative() {
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () -> UUriUtils.checkResponseUriValid(EMPTY_URI));
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () -> UUriUtils.checkResponseUriValid(RESOURCE_URI));
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () -> UUriUtils.checkResponseUriValid(METHOD_URI));
    }

    @Test
    public void testRemoveResource() {
        assertEquals(SERVICE_URI, UUriUtils.removeResource(RESOURCE_URI));
    }

    @Test
    public void testAddAuthority() {
        assertEquals(SERVICE_URI_REMOTE, UUriUtils.addAuthority(SERVICE_URI, AUTHORITY_REMOTE));
    }

    @Test
    public void testRemoveAuthority() {
        assertEquals(SERVICE_URI, UUriUtils.removeAuthority(SERVICE_URI_REMOTE));
    }

    @Test
    public void testIsSameClient() {
        assertTrue(UUriUtils.isSameClient(METHOD_URI, SERVICE_URI));
        assertTrue(UUriUtils.isSameClient(METHOD_URI_REMOTE, SERVICE_URI_REMOTE));
        assertFalse(UUriUtils.isSameClient(METHOD_URI, SERVICE2_URI));
        assertFalse(UUriUtils.isSameClient(METHOD_URI_REMOTE, SERVICE_URI));
    }

    @Test
    public void testIsRemoteUri() {
        assertTrue(UUriUtils.isRemoteUri(METHOD_URI_REMOTE));
        assertFalse(UUriUtils.isRemoteUri(METHOD_URI));
    }

    @Test
    public void testIsLocal() {
        assertTrue(UUriUtils.isLocalUri(METHOD_URI));
        assertFalse(UUriUtils.isLocalUri(METHOD_URI_REMOTE));
    }
}
