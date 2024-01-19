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

import static org.eclipse.uprotocol.core.udiscovery.common.Constants.UDISCOVERY;
import static org.eclipse.uprotocol.core.udiscovery.common.Constants.UDISCOVERY_JSON;
import static org.eclipse.uprotocol.core.udiscovery.db.JsonNodeTest.REGISTRY_JSON;
import static org.eclipse.uprotocol.core.udiscovery.internal.log.Formatter.join;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import android.content.Context;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.core.udiscovery.common.Constants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

@RunWith(RobolectricTestRunner.class)
public class AssetUtilityTest extends TestBase {

    public Context mContext;
    AssetUtility mAssetUtility;
    @Mock
    Context mockContext;
    @Mock
    File mockFile;
    @Mock
    AssetUtility mockAssetUtility;
    private AssetUtility mSpyAssetUtility;
    private File mFile;
    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
        ShadowLog.stream = System.out;
        mAssetUtility = new AssetUtility();
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mSpyAssetUtility = Mockito.spy(new AssetUtility());
        mFile = File.createTempFile("lds", ".json");
        try (FileWriter writer = new FileWriter(mFile)) {
            writer.write(REGISTRY_JSON);
        }
    }

    @Test public void test_readFileFromInternalStorage() {
        boolean actual = mAssetUtility.writeFileToInternalStorage(mContext, Constants.LDS_DB_FILENAME,
                REGISTRY_JSON);
        assertTrue(actual);
        String actualReadData = mAssetUtility.readFileFromInternalStorage(mContext, Constants.LDS_DB_FILENAME);
        assertEquals(REGISTRY_JSON.replaceAll("\\n", ""), actualReadData);
    }

    @Test public void test_readFileFromInternalStorage_FileNotFound() {
        String actual = mAssetUtility.readFileFromInternalStorage(mContext, Constants.LDS_DB_FILENAME);
        assertEquals("", actual);
    }

    @Test public void test_readJsonFromFile() {
        String actualReadData = mAssetUtility.readJsonFromFile(mContext, UDISCOVERY+"/"+UDISCOVERY_JSON);
        Log.i(LOG_TAG, join(Key.MESSAGE, actualReadData));
    }

    @Test public void test_readJsonFromFile_FileNotFound() {

        String actual = mAssetUtility.readJsonFromFile(mContext, UDISCOVERY_JSON);
        assertEquals("", actual);
    }

    @Test public void test_close_exceptionally() throws IOException {
        BufferedReader obj = mock(BufferedReader.class);
        doThrow(IOException.class).when(obj).close();
        mAssetUtility.close(obj);
    }
}
