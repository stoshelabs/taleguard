package dev.stoshe.taleguard.api;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class HookRegistry {
    private static final String REGISTRY_KEY = "taleguard.hook.registry";
    private static final Map<Class<?>, MethodHandle> ALLOWED_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, MethodHandle> DENIED_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, MethodHandle> PRIORITY_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, MethodHandle> FLUID_FLOW_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, MethodHandle> COMMAND_ALLOWED_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, MethodHandle> COMMAND_MESSAGE_CACHE = new ConcurrentHashMap<>();

    static {
        // Initialize the registry map in System properties if not present to share across all classloaders
        if (!System.getProperties().containsKey(REGISTRY_KEY)) {
            System.getProperties().put(REGISTRY_KEY, new ConcurrentHashMap<String, Object>());
        }
    }

    private HookRegistry() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getRawRegistry() {
        return (Map<String, Object>) System.getProperties().get(REGISTRY_KEY);
    }

    public static void registerHook(String key, Object hook) {
        getRawRegistry().put(key, hook);
    }

    public static void unregisterHook(String key) {
        getRawRegistry().remove(key);
    }

    public static boolean checkAllowed(UUID playerUuid, String worldName, double x, double y, double z, String type) {
        Map<String, Object> registry = getRawRegistry();
        if (registry == null || registry.isEmpty()) {
            return true;
        }

        // Retrieve and sort hooks by priority level dynamically
        List<Object> hooks = new ArrayList<>(registry.values());
        try {
            hooks.sort((h1, h2) -> Integer.compare(getPriorityLevel(h1), getPriorityLevel(h2)));
        } catch (Exception ignored) {
        }

        for (Object hook : hooks) {
            MethodHandle mh = ALLOWED_CACHE.computeIfAbsent(hook.getClass(), HookRegistry::findAllowedHandle);
            if (mh != null) {
                try {
                    boolean allowed = (boolean) mh.invoke(hook, playerUuid, worldName, x, y, z, type);
                    if (!allowed) {
                        // Action is denied! Trigger notification and return immediately
                        notifyDenied(hook, playerUuid, worldName, x, y, z, type);
                        return false;
                    }
                } catch (Throwable ignored) {
                    // Fail-safe: if hook execution fails, continue to next hook or default to allowed
                }
            }
        }
        return true;
    }

    /**
     * Checks whether fluid is allowed to flow from one position to another. If ANY registered hook
     * returns false the flow is considered blocked.
     *
     * @return true if the fluid flow is allowed (not blocked by any hook)
     */
    public static boolean checkFluidFlow(String worldName, int fromX, int fromY, int fromZ, int toX, int toY, int toZ) {
        Map<String, Object> registry = getRawRegistry();
        if (registry == null || registry.isEmpty()) {
            return true;
        }
        for (Object hook : registry.values()) {
            MethodHandle mh = FLUID_FLOW_CACHE.computeIfAbsent(hook.getClass(), HookRegistry::findFluidFlowHandle);
            if (mh != null) {
                try {
                    boolean allowed = (boolean) mh.invoke(hook, worldName, fromX, fromY, fromZ, toX, toY, toZ);
                    if (!allowed) {
                        return false;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return true;
    }

    /**
     * Checks whether a command is allowed. If ANY registered hook returns false the command is blocked.
     *
     * @return true if the command is allowed (not blocked by any hook)
     */
    public static boolean checkCommand(UUID senderUuid, String rawCommand) {
        Map<String, Object> registry = getRawRegistry();
        if (registry == null || registry.isEmpty()) {
            return true;
        }
        for (Object hook : registry.values()) {
            MethodHandle mh = COMMAND_ALLOWED_CACHE.computeIfAbsent(hook.getClass(), HookRegistry::findCommandAllowedHandle);
            if (mh != null) {
                try {
                    boolean allowed = (boolean) mh.invoke(hook, senderUuid, rawCommand);
                    if (!allowed) {
                        return false;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return true;
    }

    /**
     * Returns the first non-null command denial message exposed by a registered hook, or null if none.
     */
    public static String commandDenialMessage() {
        Map<String, Object> registry = getRawRegistry();
        if (registry == null || registry.isEmpty()) {
            return null;
        }
        for (Object hook : registry.values()) {
            MethodHandle mh = COMMAND_MESSAGE_CACHE.computeIfAbsent(hook.getClass(), HookRegistry::findCommandMessageHandle);
            if (mh != null) {
                try {
                    Object r = mh.invoke(hook);
                    if (r != null) {
                        return String.valueOf(r);
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static void notifyDenied(Object hook, UUID playerUuid, String worldName, double x, double y, double z, String type) {
        MethodHandle mh = DENIED_CACHE.computeIfAbsent(hook.getClass(), HookRegistry::findDeniedHandle);
        if (mh != null) {
            try {
                mh.invoke(hook, playerUuid, worldName, x, y, z, type);
            } catch (Throwable ignored) {
            }
        }
    }

    private static int getPriorityLevel(Object hook) {
        MethodHandle mh = PRIORITY_CACHE.computeIfAbsent(hook.getClass(), HookRegistry::findPriorityHandle);
        if (mh != null) {
            try {
                return (int) mh.invoke(hook);
            } catch (Throwable ignored) {
            }
        }
        return 2; // Default to Priority.NORMAL (2)
    }

    private static MethodHandle findAllowedHandle(Class<?> hookClass) {
        try {
            return MethodHandles.publicLookup().findVirtual(
                hookClass,
                "isAllowed",
                MethodType.methodType(boolean.class, UUID.class, String.class, double.class, double.class, double.class, String.class)
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private static MethodHandle findDeniedHandle(Class<?> hookClass) {
        try {
            return MethodHandles.publicLookup().findVirtual(
                hookClass,
                "notifyDenied",
                MethodType.methodType(void.class, UUID.class, String.class, double.class, double.class, double.class, String.class)
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private static MethodHandle findPriorityHandle(Class<?> hookClass) {
        try {
            return MethodHandles.publicLookup().findVirtual(
                hookClass,
                "getPriority",
                MethodType.methodType(int.class)
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private static MethodHandle findFluidFlowHandle(Class<?> hookClass) {
        try {
            return MethodHandles.publicLookup().findVirtual(
                hookClass,
                "isFluidFlowAllowed",
                MethodType.methodType(boolean.class, String.class, int.class, int.class, int.class, int.class, int.class, int.class)
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private static MethodHandle findCommandAllowedHandle(Class<?> hookClass) {
        try {
            return MethodHandles.publicLookup().findVirtual(
                hookClass,
                "isCommandAllowed",
                MethodType.methodType(boolean.class, UUID.class, String.class)
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private static MethodHandle findCommandMessageHandle(Class<?> hookClass) {
        try {
            return MethodHandles.publicLookup().findVirtual(
                hookClass,
                "getCommandDenialMessage",
                MethodType.methodType(String.class)
            );
        } catch (Exception ignored) {
            return null;
        }
    }
}
