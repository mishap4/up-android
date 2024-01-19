/*
 * Copyright (C) GM Global Technology Operations LLC 2022-2023.
 * All Rights Reserved.
 * GM Confidential Restricted.
 */


package org.eclipse.uprotocol.core.udiscovery.internal.log;

import static org.eclipse.uprotocol.core.udiscovery.internal.log.Formatter.SEPARATOR_PAIR;
import static org.eclipse.uprotocol.core.udiscovery.internal.log.Formatter.SEPARATOR_PAIRS;
import static org.junit.Assert.assertEquals;

import android.content.ComponentName;
import android.os.Binder;
import android.os.IBinder;

import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.core.udiscovery.TestBase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FormatterTest extends TestBase {
    private static final String KEY1 = "key1";
    private static final String KEY2 = "key2";
    private static final String VALUE1 = "value1";
    private static final String VALUE2 = "value2";
    private static final String MESSAGE1 = KEY1 + SEPARATOR_PAIR + VALUE1;
    private static final String MESSAGE2 = MESSAGE1 + SEPARATOR_PAIRS + KEY2 + SEPARATOR_PAIR + VALUE2;

    @Test
    public void testQuote() {
        assertEquals("\"test\"", Formatter.quote("test"));
    }

    @Test
    public void testQuoteEscaped() {
        assertEquals("\"with \\\" inside\"", Formatter.quote("with \" inside"));
    }

    @Test
    public void testGroup() {
        assertEquals("[test]", Formatter.group("test"));
    }

    @Test
    public void testGroupEmpty() {
        assertEquals("[]", Formatter.group(""));
        assertEquals("[]", Formatter.group(null));
    }

    @Test
    public void testJoinGrouped() {
        assertEquals("[" + MESSAGE1 + "]", Formatter.joinGrouped(KEY1, VALUE1));
    }

    @Test
    public void testJoin() {
        assertEquals(MESSAGE2, Formatter.join(KEY1, VALUE1, KEY2, VALUE2));
    }

    @Test
    public void testJoinEmpty() {
        assertEquals("", Formatter.join());
        assertEquals("", Formatter.join((Object[]) null));
    }

    @Test
    public void testJoinEmptyKey() {
        assertEquals(MESSAGE1, Formatter.join(KEY1, VALUE1, null, VALUE2));
        assertEquals(MESSAGE1, Formatter.join(KEY1, VALUE1, "", VALUE2));
    }

    @Test
    public void testJoinAndAppend() {
        StringBuilder builder = new StringBuilder();
        Formatter.joinAndAppend(builder, KEY1, VALUE1);
        Formatter.joinAndAppend(builder, KEY2, VALUE2);
        assertEquals(MESSAGE2, builder.toString());
    }

//    @Test
//    public void testStringifyUltifiUriNull() {
//        assertEquals("\"\"", Formatter.stringify((UltifiUri) null));
//    }
//
//    @Test
//    public void testStringifyStatus() {
//        Status status = buildStatus(Code.UNKNOWN, "unknown");
//        assertEquals("[" + Key.CODE + SEPARATOR_PAIR + getCode(status) + SEPARATOR_PAIRS +
//                        Key.MESSAGE + SEPARATOR_PAIR + "\"" + status.getMessage() + "\"]",
//                Formatter.stringify(status));
//    }
//
//    @Test
//    public void testStringifyStatusNull() {
//        assertEquals("", Formatter.stringify((Status) null));
//    }
//
//    @Test
//    public void testStringifyStatusWithoutMessage() {
//        Status status = buildStatus(Code.OK);
//        assertEquals("[" + Key.CODE + SEPARATOR_PAIR + getCode(status) + "]",
//                Formatter.stringify(status));
//    }
//
//    @Test
//    public void testStringifyCloudEventNull() {
//        assertEquals("", Formatter.stringify((CloudEvent) null));
//    }

    @Test
    public void testStringifyIBinder() {
        IBinder binder = new Binder();
        assertEquals(Integer.toHexString(binder.hashCode()), Formatter.stringify(binder));
    }

    @Test
    public void testStringifyIBinderNull() {
        assertEquals("", Formatter.stringify((IBinder) null));
    }

    @Test
    public void testStringifyComponentName() {
        ComponentName component = new ComponentName("package", "class");
        assertEquals("[" + Key.PACKAGE + SEPARATOR_PAIR + "\"package\"" + SEPARATOR_PAIRS +
                Key.CLASS + SEPARATOR_PAIR + "\"class\"]", Formatter.stringify(component));
    }

    @Test
    public void testStringifyComponentNameNull() {
        assertEquals("", Formatter.stringify((ComponentName) null));
    }

    @Test
    public void testToPrettyMemory() {
        assertEquals("17 B", Formatter.toPrettyMemory(17));
        assertEquals("1.0 KB", Formatter.toPrettyMemory(1024));
        assertEquals("1.0 MB", Formatter.toPrettyMemory(1048576));
        assertEquals("1.0 GB", Formatter.toPrettyMemory(1073741824));
    }
}


