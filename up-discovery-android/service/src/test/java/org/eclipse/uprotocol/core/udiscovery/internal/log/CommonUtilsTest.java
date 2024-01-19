/*
 * Copyright (C) GM Global Technology Operations LLC 2022-2023.
 * All Rights Reserved.
 * GM Confidential Restricted.
 */
package org.eclipse.uprotocol.core.udiscovery.internal.log;

import static org.eclipse.uprotocol.core.udiscovery.internal.log.CommonUtils.EMPTY_STRING_ARRAY;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;

import org.eclipse.uprotocol.core.udiscovery.TestBase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
public class CommonUtilsTest extends TestBase {
    @Test
    public void testEmptyIfNullList() {
        List<String> list = List.of("test");
        assertEquals(list, CommonUtils.emptyIfNull(list));
        assertEquals(emptyList(), CommonUtils.emptyIfNull((List<String>) null));
    }

    @Test
    public void testEmptyIfNullSet() {
        Set<String> set = Set.of("test");
        assertEquals(set, CommonUtils.emptyIfNull(set));
        assertEquals(emptySet(), CommonUtils.emptyIfNull((Set<String>) null));
    }

    @Test
    public void testEmptyIfNullString() {
        String string = "test";
        assertEquals(string, CommonUtils.emptyIfNull(string));
        assertEquals("", CommonUtils.emptyIfNull((String) null));
    }

    @Test
    public void testEmptyIfNullStringArray() {
        String[] array = new String[]{"test"};
        assertArrayEquals(array, CommonUtils.emptyIfNull(array));
        assertArrayEquals(EMPTY_STRING_ARRAY, CommonUtils.emptyIfNull((String[]) null));
    }
}
