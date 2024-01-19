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

import static org.eclipse.uprotocol.common.util.UStatusUtils.checkState;
import static org.eclipse.uprotocol.common.util.UStatusUtils.checkStringNotEmpty;
import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.core.udiscovery.UDiscoveryService.errorStatus;

import android.content.Context;
import android.util.Log;

import org.eclipse.uprotocol.common.util.log.Formatter;
import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.core.udiscovery.common.Constants;
import org.eclipse.uprotocol.core.udiscovery.db.DiscoveryManager;
import org.eclipse.uprotocol.core.udiscovery.internal.Utils;

public class LoadUtility {
    private static final String LOG_TAG =  Formatter.tag("core", LoadUtility.class.getSimpleName());

    protected static boolean DEBUG = Log.isLoggable(LOG_TAG, Log.DEBUG);
    protected static boolean VERBOSE = Log.isLoggable(LOG_TAG, Log.VERBOSE);
    private final Context mContext;
    public final AssetUtility mAssetUtil;
    public final DiscoveryManager mDiscoveryMgr;

    enum initLDSCode {
        FAILURE,
        RECOVERY,
        SUCCESS
    }

    public LoadUtility(Context context, AssetUtility au, DiscoveryManager mgr) {
        mContext = context;
        mAssetUtil = au;
        mDiscoveryMgr = mgr;
    }

    public initLDSCode initializeLDS() {
        initLDSCode code;
        try {
            load(Constants.LDS_DB_FILENAME);
            code = initLDSCode.SUCCESS;
        } catch (Utils.StatusException e) {
            errorStatus(LOG_TAG, "initializeLDS", Utils.throwableToStatus(e));
            code = initLDSCode.RECOVERY;
        }
        if (code == initLDSCode.RECOVERY) {
            Log.w(LOG_TAG, join(Key.MESSAGE, "initializing empty LDS database"));
            mDiscoveryMgr.init(Constants.LDS_AUTHORITY);
            if (!saveDB(Constants.LDS_DB_FILENAME)) {
                code = initLDSCode.FAILURE;
            }
        }
        if (code == initLDSCode.FAILURE) {
            Log.e(LOG_TAG,join(Key.MESSAGE, "DB initialization failed"));
        } else {
            Log.d(LOG_TAG, join(Key.MESSAGE, "DB initialization successful"));
            if (VERBOSE) {
                Log.v(LOG_TAG, join(Key.MESSAGE, mDiscoveryMgr.export()));
            }
        }
        return code;
    }

    private boolean saveDB(String filename) {
        checkStringNotEmpty(filename, "[saveDB] filename empty string");
        final String db = mDiscoveryMgr.export();
        return mAssetUtil.writeFileToInternalStorage(mContext, filename, db);
    }

    private void load(String filename) {
        checkStringNotEmpty(filename, "[load] filename empty string");

        final String json = mAssetUtil.readFileFromInternalStorage(mContext, filename);
        checkStringNotEmpty(json, "[load] database is empty");

        final boolean bLoadResult = mDiscoveryMgr.load(json);
        checkState(bLoadResult, "[load] failed to load database");

        Log.d(LOG_TAG, join(Key.EVENT, "load",  Key.STATUS, "successful"));
    }
}
