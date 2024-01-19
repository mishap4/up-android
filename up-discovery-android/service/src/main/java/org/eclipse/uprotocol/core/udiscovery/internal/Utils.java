package org.eclipse.uprotocol.core.udiscovery.internal;

import static org.eclipse.uprotocol.core.udiscovery.UDiscoveryService.UDISCOVERY_SERVICE;
import static org.eclipse.uprotocol.core.udiscovery.common.Constants.PERMISSION_LEVEL;

import android.text.TextUtils;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.protobuf.ProtocolStringList;

import org.eclipse.uprotocol.core.udiscovery.v3.Node;
import org.eclipse.uprotocol.uri.serializer.LongUriSerializer;
import org.eclipse.uprotocol.v1.UAuthority;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UEntity;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;

import java.util.List;
import java.util.stream.Collectors;

public final class Utils {

    private Utils() {
    }

    public static Pair<String, String> parseAuthority(UAuthority authority) {
        final String name = authority.getName();
        final String[] tokens = name.split("[.]", 2);
        checkArgument(tokens.length == 2, "[parseAuthority] missing delimiter");
        final String device = tokens[0];
        final String domain = tokens[1];
        return new Pair<>(device, domain);
    }

    public static String toLongUri(UAuthority authority) {
        return toLongUri(UUri.newBuilder().setAuthority(authority).build());
    }

    public static String toLongUri(UAuthority authority, UEntity entity) {
        return toLongUri(UUri.newBuilder().setAuthority(authority).setEntity(entity).build());
    }

    public static String toLongUri(UUri uri) {
        return LongUriSerializer.instance().serialize(uri);
    }

    public static String sanitizeUri(String uri) {
        LongUriSerializer lus = LongUriSerializer.instance();
        return lus.serialize(lus.deserialize(uri));
    }

    public static List<UUri> deserializeUriList(@NonNull ProtocolStringList list) {
        return list.stream().map(element -> LongUriSerializer.instance().deserialize(element)).collect(
                Collectors.toList());
    }

    public static boolean hasCharAt(@NonNull String string, int index, char ch) {
        if (index < 0 || index >= string.length()) {
            return false;
        }
        return string.charAt(index) == ch;
    }

    public static boolean isOk(@Nullable UStatus var0) {
        return var0 != null && var0.getCodeValue() == 0;
    }

    @NonNull
    public static <T> T checkNotNull(@Nullable T var0, @Nullable String var1) {
        if (var0 != null) {
            return var0;
        } else {
            throw new StatusException(UCode.INVALID_ARGUMENT, var1);
        }
    }

    @NonNull
    public static <T> T checkNotNull(@Nullable T var0, @NonNull UCode var1, @Nullable String var2) {
        if (var0 != null) {
            return var0;
        } else {
            throw new StatusException(var1, var2);
        }
    }

    public static void checkArgument(boolean var0, @Nullable String var1) {
        if (!var0) {
            throw new StatusException(UCode.INVALID_ARGUMENT, var1);
        }
    }

    public static void checkArgument(boolean var0, @NonNull UCode var1, @Nullable String var2) {
        if (!var0) {
            throw new StatusException(var1, var2);
        }
    }

    public static int checkArgumentPositive(int var0, @NonNull UCode var1, @Nullable String var2) {
        if (var0 > 0) {
            return var0;
        } else {
            throw new StatusException(var1, var2);
        }
    }

    public static int checkArgumentPositive(int var0, @Nullable String var1) {
        if (var0 > 0) {
            return var0;
        } else {
            throw new StatusException(UCode.INVALID_ARGUMENT, var1);
        }
    }

    @NonNull
    public static <T extends CharSequence> T checkStringNotEmpty(T var0, @Nullable String var1) {
        if (!TextUtils.isEmpty(var0)) {
            return var0;
        } else {
            throw new StatusException(UCode.INVALID_ARGUMENT, var1);
        }
    }

    @NonNull
    public static <T extends CharSequence> T checkStringNotEmpty(T var0, @NonNull UCode var1, @Nullable String var2) {
        if (!TextUtils.isEmpty(var0)) {
            return var0;
        } else {
            throw new StatusException(var1, var2);
        }
    }

    @NonNull
    public static UStatus throwableToStatus(@NonNull Throwable var0) {
        if (var0 instanceof StatusException) {
            return ((StatusException)var0).getStatus();
        }
        if (var0.getMessage() == null) {
            return UStatus.newBuilder().setCode(UCode.INVALID_ARGUMENT).build();
        }
        return UStatus.newBuilder().setCode(UCode.INVALID_ARGUMENT).setMessage(var0.getMessage()).build();
    }

    public static class StatusException extends RuntimeException {
        private final UStatus a;

        public StatusException(UCode var1, String var2) {
            a = UStatus.newBuilder().setCode(var1).setMessage(var2).build();
        }

        public StatusException(UCode var1, String var2, Throwable var3) {
            a = UStatus.newBuilder().setCode(var1).setMessage(var2).build();
        }

        @NonNull
        public UStatus getStatus() {
            return this.a;
        }
    }

    @NonNull
    public static UStatus buildStatus(@NonNull UCode var0) {
        return UStatus.newBuilder().setCode(var0).build();
    }

    @NonNull
    public static UStatus buildStatus(@NonNull UCode var0, @Nullable String var1) {
        return UStatus.newBuilder().setCode(var0).setMessage(var1).build();
    }

    private static boolean hasFullWritePermissions(@NonNull UUri callerUri) {
        final UEntity caller = callerUri.getEntity();
        return (caller.getName().equals(UDISCOVERY_SERVICE.getName()));
    }
    public static void checkPropertyUpdateAllowed(@NonNull UUri callerUri, @NonNull String propertyName) {
        if (propertyName.contains(PERMISSION_LEVEL) && !hasFullWritePermissions(callerUri)) {
            throw new StatusException(UCode.PERMISSION_DENIED, "Property is restricted for modification");
        }
    }

    public static void checkWritePermissions(@NonNull UUri callerUri, @NonNull String nodeUri,
                                             @NonNull Node.Type type) {
        if (hasFullWritePermissions(callerUri)) {
            return;
        }
        final UEntity caller = callerUri.getEntity();
        if ((caller.equals(nodeUri) && callerUri.getAuthority().hasName())) {
            if (type != Node.Type.MESSAGE && type != Node.Type.RESOURCE) {
                throw new StatusException(UCode.PERMISSION_DENIED, "Node type is restricted for modification");
            }
        } else {
            throw new StatusException(UCode.PERMISSION_DENIED, "Node is not owned by the caller");
        }
    }
}
