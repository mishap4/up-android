/*
 * Copyright (C) GM Global Technology Operations LLC 2022-2023.
 * All Rights Reserved.
 * GM Confidential Restricted.
 */
package org.eclipse.uprotocol.core.udiscovery.internal.log;

import static android.text.TextUtils.isEmpty;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.eclipse.uprotocol.common.util.log.Key;

public final class Formatter {
    public static final String SEPARATOR_PAIR = ": ";
    public static final String SEPARATOR_PAIRS = ", ";

    private Formatter() {
    }

    private static @NonNull String toString(@Nullable Object object) {
        return (object == null) ? "" : object.toString();
    }

    private static @NonNull String escapeQuotes(String value) {
        return isEmpty(value) ? "" : value.replace("\"", "\\\"");
    }

    private static @NonNull String quoteIfNeeded(String value) {
        if (isEmpty(value) || value.charAt(0) == '"' || value.charAt(0) == '[') {
            return CommonUtils.emptyIfNull(value);
        }
        return value.indexOf(' ') >= 0 ? quote(value) : value;
    }

    public static @NonNull String quote(String value) {
        return '"' + escapeQuotes(value) + '"';
    }

    public static @NonNull String group(String value) {
        return '[' + CommonUtils.emptyIfNull(value) + ']';
    }

    public static @NonNull String joinGrouped(Object... args) {
        final StringBuilder builder = new StringBuilder("[");
        return joinAndAppend(builder, args).append("]").toString();
    }

    public static @NonNull String join(Object... args) {
        return joinAndAppend(new StringBuilder(), args).toString();
    }

    public static @NonNull StringBuilder joinAndAppend(@NonNull StringBuilder builder, Object... args) {
        if (args == null) {
            return builder;
        }
        boolean isKey = true;
        boolean skipValue = false;
        for (Object arg : args) {
            final String string = toString(arg);
            if (isKey && isEmpty(string) || skipValue) {
                isKey = !isKey;
                skipValue = !skipValue;
                continue;
            }
            if (isKey) {
                appendPairsSeparator(builder);
                builder.append(string);
            } else {
                builder.append(SEPARATOR_PAIR);
                builder.append(quoteIfNeeded(string));
            }
            isKey = !isKey;
        }
        return builder;
    }

    private static void appendPairsSeparator(@NonNull StringBuilder builder) {
        if (builder.length() > 1) {
            builder.append(SEPARATOR_PAIRS);
        }
    }

//    public static @NonNull String stringify(UltifiUri uri) {
//        return quote((uri != null && !uri.isEmpty()) ? uri.uProtocolUri() : "");
//    }

//    public static @NonNull String stringify(Status status) {
//        if (status == null) {
//            return "";
//        }
//        final String message = status.getMessage();
//        return joinGrouped(Key.CODE, getCode(status),
//                message.isEmpty() ? null : Key.MESSAGE, message.isEmpty() ? null : quote(message));
//    }

//    public static @NonNull String stringify(CloudEvent event) {
//        if (event == null) {
//            return "";
//        }
//        final Optional<String> sink = getSink(event);
//        return joinGrouped(Key.ID, event.getId(), Key.SOURCE, quote(getSource(event)),
//                sink.isPresent() ? Key.SINK : null, sink.map(Formatter::quote).orElse(null),
//                Key.TYPE, quote(event.getType()));
//    }


    public static @NonNull String stringify(IBinder binder) {
        return (binder != null) ? Integer.toHexString(binder.hashCode()) : "";
    }

    public static @NonNull String stringify(ComponentName component) {
        return (component != null) ? joinGrouped(Key.PACKAGE, quote(component.getPackageName()),
                Key.CLASS, quote(component.getShortClassName())) : "";
    }


    @SuppressLint("DefaultLocale")
    public static @NonNull String toPrettyMemory(long bytes) {
        long unit = 1024;
        if (bytes < unit) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), "KMGTPE".charAt(exp - 1));
    }

}
