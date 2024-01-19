/*
 * Copyright (C) GM Global Technology Operations LLC 2022-2023.
 * All Rights Reserved.
 * GM Confidential Restricted.
 */
package org.eclipse.uprotocol.core.udiscovery.internal.log;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Set;

public final class CommonUtils {
    public static final String[] EMPTY_STRING_ARRAY = new String[0];

    private CommonUtils() {
    }

    public static @NonNull <T> List<T> emptyIfNull(@Nullable List<T> list) {
        return (list == null) ? emptyList() : list;
    }

    public static @NonNull <T> Set<T> emptyIfNull(@Nullable Set<T> list) {
        return (list == null) ? emptySet() : list;
    }

    public static @NonNull String emptyIfNull(@Nullable String string) {
        return (string == null) ? "" : string;
    }

    public static @NonNull String[] emptyIfNull(@Nullable String[] array) {
        return (array == null) ? EMPTY_STRING_ARRAY : array;
    }
}
