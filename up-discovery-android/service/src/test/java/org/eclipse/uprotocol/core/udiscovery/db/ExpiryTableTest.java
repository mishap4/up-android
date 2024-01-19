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

import org.eclipse.uprotocol.core.udiscovery.TestBase;
import org.junit.Before;

public class ExpiryTableTest extends TestBase {
    public ExpiryTable tbl;

    @Before
    public void setUp(){
        tbl = new ExpiryTable();
    }

//    public ExpiryData buildExpiryData(DiscoveryManager.eSource src, String uri) {
//        return new ExpiryData(src, uri, Instant.now().toString(), null);
//    }
//
//    @Test
//    public void positive_clear() {
//        ExpiryData edCds = buildExpiryData(DiscoveryManager.eSource.CDS, AUTHORITY_URI + "body.access");
//        ExpiryData edOta = buildExpiryData(DiscoveryManager.eSource.OTA, "ultifi:/body.access");
//        assertTrue(tbl.add(edOta));
//        assertTrue(tbl.add(edCds));
//        tbl.clear();
//        ExpiryData rvCds = tbl.remove(DiscoveryManager.eSource.CDS, AUTHORITY_URI + "body.access");
//        ExpiryData rvOTa = tbl.remove(DiscoveryManager.eSource.OTA, "ultifi:/body.access");
//        assertNull(rvCds);
//        assertNull(rvOTa);
//    }
//
//    @Test
//    public void positive_remove() {
//        ExpiryData edCds = buildExpiryData(DiscoveryManager.eSource.CDS, AUTHORITY_URI + "body.access");
//        ExpiryData edOta = buildExpiryData(DiscoveryManager.eSource.OTA, "ultifi:/body.access");
//        assertTrue(tbl.add(edOta));
//        assertTrue(tbl.add(edCds));
//        ExpiryData rvCds = tbl.remove(DiscoveryManager.eSource.CDS, AUTHORITY_URI + "body.access");
//        ExpiryData rvOTa = tbl.remove(DiscoveryManager.eSource.OTA, "ultifi:/body.access");
//        assertEquals(edCds, rvCds);
//        assertEquals(edOta, rvOTa);
//    }
//
//    @Test
//    public void negative_remove_mismatch_src() {
//        ExpiryData ed = buildExpiryData(DiscoveryManager.eSource.OTA, "ultifi:/body.access");
//        assertTrue(tbl.add(ed));
//        ExpiryData result = tbl.remove(DiscoveryManager.eSource.CDS, "ultifi:/body.access");
//        assertNull(result);
//    }
//
//    @Test
//    public void negative_remove_mismatch_uri() {
//        ExpiryData ed = buildExpiryData(DiscoveryManager.eSource.OTA, "ultifi:/body.access");
//        assertTrue(tbl.add(ed));
//        ExpiryData result = tbl.remove(DiscoveryManager.eSource.OTA, "ultifi:/cabin.climate");
//        assertNull(result);
//    }
//
//    @Test
//    public void negative_remove_mismatch_src_and_uri() {
//        ExpiryData ed = buildExpiryData(DiscoveryManager.eSource.OTA, "ultifi:/body.access");
//        assertTrue(tbl.add(ed));
//        ExpiryData result = tbl.remove(DiscoveryManager.eSource.CDS,
//                AUTHORITY_URI + "body.access");
//        assertNull(result);
//    }
//
//    @Test
//    public void positive_export() {
//        ExpiryData edCds = buildExpiryData(DiscoveryManager.eSource.CDS, AUTHORITY_URI + "body.access");
//        ExpiryData edOta = buildExpiryData(DiscoveryManager.eSource.OTA, "ultifi:/body.access");
//        assertTrue(tbl.add(edOta));
//        assertTrue(tbl.add(edCds));
//        tbl.export();
//    }
}
