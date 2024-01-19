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

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import org.eclipse.uprotocol.common.util.log.Formatter;
import org.eclipse.uprotocol.core.udiscovery.internal.Utils;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class AssetUtility {

    public AssetUtility() {}

    private static final String LOG_TAG =  Formatter.tag("core", AssetUtility.class.getSimpleName());

    public String readFileFromInternalStorage(Context context, String sFileName){
        try {
            final String filepath = context.getFilesDir() + "/" + sFileName;
            final StringBuilder buffer = new StringBuilder();
            BufferedReader reader = new BufferedReader(new FileReader(filepath));
            String currentLine;
            while ((currentLine = reader.readLine()) != null) {
                buffer.append(currentLine);
            }
            return buffer.toString();

        } catch (FileNotFoundException e) {
            Log.e(LOG_TAG, join("readFileFromInternalStorage", Utils.throwableToStatus(e)));
            e.printStackTrace();
        } catch (IOException e) {
            Log.e(LOG_TAG,join("readFileFromInternalStorage", Utils.throwableToStatus(e)));
            e.printStackTrace();
        }
        return "";
    }

    public boolean writeFileToInternalStorage(Context context, String sFileName, String sBody){
        try {
            final File fd = new File(context.getFilesDir(), sFileName);
            final FileWriter writer = new FileWriter(fd);
            writer.append(sBody);
            writer.flush();
            writer.close();
            return true;
        } catch (IOException e) {
            Log.e(LOG_TAG,join("writeFileToInternalStorage", Utils.throwableToStatus(e)));
            e.printStackTrace();
        }
        return false;
    }

    public String readJsonFromFile(Context context, String fileName) {
        final StringBuilder buffer = new StringBuilder();
        BufferedReader reader = null;
        try {
            AssetManager assetManager = context.getAssets();
            InputStream inputStream = assetManager.open(fileName);
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            reader = new BufferedReader(inputStreamReader);
            String row;
            while ((row = reader.readLine()) != null) {
                buffer.append(row);
            }
        } catch (FileNotFoundException fileNotFoundException) {
            Log.e(LOG_TAG, join("readJsonFromFile", Utils.throwableToStatus(fileNotFoundException),
                    "file does not exist : " + fileName));
        } catch (IOException ioException) {
            Log.e(LOG_TAG, join("readJsonFromFile", Utils.throwableToStatus(ioException),"file : " + fileName));
            ioException.printStackTrace();
        } finally {
            close(reader);
        }
        return buffer.toString();
    }

    public void close(Closeable obj) {
        if (obj != null) {
            try {
                obj.close();
            } catch (IOException e) {
                Log.e(LOG_TAG, join("close", Utils.throwableToStatus(e)));
                e.printStackTrace();
            }
        }
    }
}
