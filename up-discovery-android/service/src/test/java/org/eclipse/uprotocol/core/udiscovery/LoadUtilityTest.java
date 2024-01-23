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

import static android.util.Log.VERBOSE;
import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.core.udiscovery.db.JsonNodeTest.REGISTRY_JSON;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.util.Log;

import org.eclipse.uprotocol.ULink;
import org.eclipse.uprotocol.common.UStatusException;
import org.eclipse.uprotocol.common.util.log.Formatter;
import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.core.udiscovery.common.Constants;
import org.eclipse.uprotocol.core.udiscovery.db.DiscoveryManager;
import org.eclipse.uprotocol.v1.UCode;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLog;

import java.lang.reflect.Method;

@RunWith(RobolectricTestRunner.class)
public class LoadUtilityTest extends TestBase {

    public static final String LOG_TAG = Formatter.tag("core", LoadUtilityTest.class.getSimpleName());
    private static boolean deadlineExceededFlag = false;
    @Mock
    public AssetUtility assetUtil;
    @Mock
    public Context appContext;
    @Mock
    public ULink mEUlink;
    @Mock
    public Notifier mNotifier;
    @Rule //initMocks
    public MockitoRule rule = MockitoJUnit.rule();
    public IntegrityCheck integrity;
    public DiscoveryManager discoveryMgr;
    LoadUtility loadUtil;

    // using reflection to access hidden static SystemProperties.setProperty function
    public static void setProperty(String key, String value) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method set = c.getMethod("set", String.class, String.class);
            set.invoke(c, key, value);
        } catch (Exception e) {
            Log.e(LOG_TAG, join(Key.MESSAGE, "setProperty", Key.FAILURE, e));
            e.printStackTrace();
        }
    }

    private static void setLogLevel(int level) {
        LoadUtility.DEBUG = (level <= Log.DEBUG);
        LoadUtility.VERBOSE = (level <= VERBOSE);
    }

    @Before
    public void setUp() {
        ShadowLog.stream = System.out;
        setLogLevel(Log.DEBUG);
        integrity = new IntegrityCheck();
        discoveryMgr = new DiscoveryManager(mNotifier);
        loadUtil = Mockito.spy(new LoadUtility(appContext, assetUtil, discoveryMgr));
    }

    @Test
    public void testinitializeLDS() {
        setLogLevel(VERBOSE);
        when(assetUtil.readFileFromInternalStorage(appContext, Constants.LDS_DB_FILENAME)).thenReturn(REGISTRY_JSON);
        DiscoveryManager discoveryManager = Mockito.spy(new DiscoveryManager(mNotifier));
        when(discoveryManager.load(REGISTRY_JSON)).thenReturn(true);
        loadUtil = Mockito.spy(new LoadUtility(appContext, assetUtil, discoveryManager));
        assertEquals(LoadUtility.initLDSCode.SUCCESS, loadUtil.initializeLDS());
    }

    @Test
    public void testinitializeLDS_Recovery() {
        when(assetUtil.readFileFromInternalStorage(appContext, Constants.LDS_DB_FILENAME)).thenReturn(REGISTRY_JSON);
        DiscoveryManager discoveryManager = Mockito.spy(new DiscoveryManager(mNotifier));
        when(discoveryManager.load(REGISTRY_JSON)).thenReturn(true);
        loadUtil = Mockito.spy(new LoadUtility(appContext, assetUtil, discoveryManager, LoadUtility.initLDSCode.RECOVERY));
        assertEquals(LoadUtility.initLDSCode.FAILURE, loadUtil.initializeLDS());
    }

    @Test
    public void testLoadFailure1() {
        when(assetUtil.readFileFromInternalStorage(appContext, Constants.LDS_DB_FILENAME)).thenReturn("corrupt lds");
        UStatusException uStatusException = assertThrows(UStatusException.class, () -> loadUtil.initializeLDS());
        assertEquals(UCode.FAILED_PRECONDITION, uStatusException.getCode());
        verify(assetUtil, times(1)).readFileFromInternalStorage(appContext, Constants.LDS_DB_FILENAME);
    }
}
