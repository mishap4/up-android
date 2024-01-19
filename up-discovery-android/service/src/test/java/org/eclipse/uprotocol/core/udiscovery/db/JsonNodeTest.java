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
import org.eclipse.uprotocol.core.udiscovery.common.Constants;

public class JsonNodeTest {
    public final static String REGISTRY_JSON = "{\n"
            + "       \"uri\": \"//" + TestBase.TEST_DOMAIN + "\",\n"
            + "       \"nodes\": [\n"
            + "         {\n"
            + "           \"uri\": \"//" + TestBase.TEST_NAME + "\",\n"
            + "           \"nodes\": [\n"
            + "             {\n"
            + "               \"uri\": \"//" + TestBase.TEST_NAME + "/" + TestBase.TEST_ENTITY + "\",\n"
            + "               \"nodes\": [\n"
            + "                 {\n"
            + "                   \"uri\": \"//" + TestBase.TEST_NAME + "/" + TestBase.TEST_ENTITY + "/1\",\n"
            + "                   \"nodes\": [\n"
            + "                     {\n"
            + "                       \"uri\": \"//" + TestBase.TEST_NAME + "/" + TestBase.TEST_ENTITY + "/1/door.front_left\",\n"
            + "                       \"properties\": {\n"
            + "                         \"" + TestBase.TEST_PROPERTY1 + "\": {\n"
            + "                           \"uInteger\": 1\n"
            + "                         },\n"
            + "                         \"" + TestBase.TEST_PROPERTY2 + "\": {\n"
            + "                           \"uInteger\": 2\n"
            + "                         }\n"
            + "                       },\n"
            + "                       \"type\": \"TOPIC\"\n"
            + "                     },\n"
            + "                     {\n"
            + "                       \"uri\": \"//" + TestBase.TEST_NAME + "/" + TestBase.TEST_ENTITY + "/1/door.front_right\",\n"
            + "                       \"properties\": {\n"
            + "                         \"topic_prop1\": {\n"
            + "                           \"uInteger\": 1\n"
            + "                         },\n"
            + "                         \"topic_prop2\": {\n"
            + "                           \"uInteger\": 2\n"
            + "                         }\n"
            + "                       },\n"
            + "                       \"type\": \"TOPIC\"\n"
            + "                     },\n"
            + "                     {\n"
            + "                       \"uri\": \"//" + TestBase.TEST_NAME + "/" + TestBase.TEST_ENTITY + "/1/door.rear_left\",\n"
            + "                       \"properties\": {\n"
            + "                         \"topic_prop1\": {\n"
            + "                           \"uInteger\": 1\n"
            + "                         },\n"
            + "                         \"topic_prop2\": {\n"
            + "                           \"uInteger\": 2\n"
            + "                         }\n"
            + "                       },\n"
            + "                       \"type\": \"TOPIC\"\n"
            + "                     },\n"
            + "                     {\n"
            + "                       \"uri\": \"//" + TestBase.TEST_NAME + "/" + TestBase.TEST_ENTITY + "/1/door.rear_right\",\n"
            + "                       \"properties\": {\n"
            + "                         \"topic_prop1\": {\n"
            + "                           \"uInteger\": 1\n"
            + "                         },\n"
            + "                         \"topic_prop2\": {\n"
            + "                           \"uInteger\": 2\n"
            + "                         }\n"
            + "                       },\n"
            + "                       \"type\": \"TOPIC\"\n"
            + "                     }\n"
            + "                   ],\n"
            + "                   \"type\": \"VERSION\"\n"
            + "                 }\n"
            + "               ],\n"
            + "               \"properties\": {\n"
            + "                 \"" + TestBase.TEST_PROPERTY1 + "\": {\n"
            + "                   \"uInteger\": 1\n"
            + "                 },\n"
            + "                 \"" + TestBase.TEST_PROPERTY2 + "\": {\n"
            + "                   \"uInteger\": 2\n"
            + "                 }\n"
            + "               },\n"
            + "               \"type\": \"ENTITY\"\n"
            + "             }\n"
            + "           ],\n"
            + "           \"properties\": {\n"
            + "             \"device_prop1\": {\n"
            + "               \"uInteger\": 1\n"
            + "             },\n"
            + "             \"device_prop2\": {\n"
            + "               \"uInteger\": 2\n"
            + "             }\n"
            + "           },\n"
            + "           \"type\": \"DEVICE\"\n"
            + "         }\n"
            + "       ],\n"
            + "       \"properties\": {\n"
            + "         \"domain_prop1\": {\n"
            + "           \"uInteger\": 1\n"
            + "         },\n"
            + "         \"domain_prop2\": {\n"
            + "           \"uInteger\": 2\n"
            + "         }\n"
            + "       },\n"
            + "       \"type\": \"DOMAIN\"\n"
            + "     }";

    protected static final String JSON_PROTOBUF_EXCEPTION = "{\n"
            + "  \"" + Constants.JSON_AUTHORITY + "\": \"//" + TestBase.TEST_NAME + "\",\n"
            + "  \"" + Constants.JSON_DATA + "\": {\n"
            + "    \"" + Constants.JSON_HIERARCHY + "\": { \"dummy_data\" : \"hello world\" }\n"
            + "  },\n"
            + "  \"" + Constants.JSON_HASH + "\": {\n"
            + "    \"" + Constants.JSON_HIERARCHY + "\": \"zfXfhrFVEIWr6D1bDtJJgqOgNj+keleiiqDfTDCOo8w=\"\n"
            + "  },\n"
            + "  \"" + Constants.JSON_TTL + "\": {\n"
            + "    \"" + Constants.JSON_HIERARCHY + "\": {}\n"
            + "  }\n"
            + "}";
}
