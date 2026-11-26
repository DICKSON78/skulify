package org.example.school_wallet.common.tenant;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * The tenant bound to the current thread/request.
 *
 * <p>The value is populated from the authenticated principal by
 * {@code JwtAuthenticationFilter}. Code that runs outside a request (startup
 * tasks, scheduled jobs, tests) must wrap work in {@link #runAs(UUID, Runnable)}
 * or {@link #callAs}.
 */
public final class TenantContext {

    /** Reserved tenant that owns platform users and can access every tenant. */
    public static final UUID SYSTEM_TENANT_ID = new UUID(0L, 0L);

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static UUID get() {
        return CURRENT.get();
    }

    public static UUID require() {
        UUID tenantId = CURRENT.get();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant bound to the current thread");
        }
        return tenantId;
    }

    public static boolean isRoot() {
        return SYSTEM_TENANT_ID.equals(CURRENT.get());
    }

    public static void set(UUID tenantId) {
        CURRENT.set(tenantId);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static void runAs(UUID tenantId, Runnable action) {
        UUID previous = CURRENT.get();
        CURRENT.set(tenantId);
        try {
            action.run();
        } finally {
            restore(previous);
        }
    }

    public static <T> T callAs(UUID tenantId, Supplier<T> action) {
        UUID previous = CURRENT.get();
        CURRENT.set(tenantId);
        try {
            return action.get();
        } finally {
            restore(previous);
        }
    }

    public static <T> T callAs(UUID tenantId, Callable<T> action) throws Exception {
        UUID previous = CURRENT.get();
        CURRENT.set(tenantId);
        try {
            return action.call();
        } finally {
            restore(previous);
        }
    }

    private static void restore(UUID previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }
}
